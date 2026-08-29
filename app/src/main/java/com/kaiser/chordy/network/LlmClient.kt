package com.kaiser.chordy.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible chat completions client. Base URL and key come from Settings
 * at call time — Retrofit bakes base URLs in at build time, so the endpoint is a
 * full URL parameter instead ("https://api.example.com/v1/chat/completions").
 *
 * Hard rules: 3s timeouts, one short line back (~40 tokens), any failure = null,
 * caller falls back to LineBank. The app never crashes over a dead API.
 */
class LlmClient {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val api: ChatApi = Retrofit.Builder()
        .baseUrl("https://localhost/")   // unused placeholder — every call passes @Url
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ChatApi::class.java)

    /**
     * Fetch one in-character line. Returns null on ANY failure — missing config,
     * timeout, HTTP error, empty choices. Caller falls back to the LineBank.
     */
    fun generateLine(
        baseUrl: String,
        apiKey: String,
        model: String,
        personaPrompt: String,
        moodTierName: String,
        event: String,
        reconnectCount: Int
    ): String? {
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) return null
        val endpoint = normalizeEndpoint(baseUrl)
        val request = ChatRequest(
            model = model,
            max_tokens = 48,
            temperature = 0.9f,
            messages = listOf(
                Message(role = "system", content = personaPrompt),
                Message(
                    role = "user",
                    content = "Event: $event. Current mood tier: $moodTierName. " +
                        "Reconnect count so far: $reconnectCount. " +
                        "Say exactly one short in-character line reacting to this event."
                )
            )
        )
        return try {
            val response = api.chat(endpoint, authHeader(apiKey), request).execute()
            val body = response.body() ?: return null
            if (!response.isSuccessful) null
            else body.choices.firstOrNull()?.message?.content?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null // timeout, DNS, TLS, parse — LineBank catches all of it
        }
    }

    private fun authHeader(key: String) = "Bearer $key"

    /** Accepts either a bare base ("https://host/v1") or a full completions URL. */
    internal fun normalizeEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) trimmed
        else trimmed + "/chat/completions"
    }
}

// ---------- Retrofit surface ----------

interface ChatApi {
    @POST
    fun chat(
        @Url endpoint: String,
        @retrofit2.http.Header("Authorization") auth: String,
        @Body body: ChatRequest
    ): retrofit2.Response<ChatResponse>
}

@Serializable
data class ChatRequest(
    val model: String,
    @SerialName("max_tokens") val max_tokens: Int,
    val temperature: Float = 0.9f,
    val messages: List<Message>
)

@Serializable
data class Message(val role: String, val content: String)

@Serializable
data class ChatResponse(val choices: List<Choice> = emptyList())

@Serializable
data class Choice(val index: Int = 0, val message: Message? = null)
