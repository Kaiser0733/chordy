package com.kaiser.chordy.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible chat completions client. Default endpoint is NVIDIA NIM
 * (bundled at build time via BuildConfig — key injected from a CI secret, so
 * it ships in the APK but never in git). Users can still override all three
 * in Settings; bundled values are the fallback.
 *
 * Hard rules: 8s timeouts (reasoning models need room — 3s was starving them),
 * ~300-token budget (48 starves reasoning models into returning null content),
 * any failure returns a LoadFailure with the REAL reason — callers show it
 * instead of a misleading "check your URL" guess.
 */
class LlmClient {

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    val api: ChatApi = Retrofit.Builder()
        .baseUrl("https://localhost/")   // placeholder — @Url carries the real endpoint
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ChatApi::class.java)

    /**
     * Outcome of a generateLine call: the line, or the actual reason it failed
     * (HTTP status/body, timeout, empty content). Never a vague shrug.
     */
    sealed class Result {
        data class Ok(val line: String) : Result()
        data class Fail(val reason: String) : Result()
    }

    fun generateLine(
        baseUrl: String,
        apiKey: String,
        model: String,
        personaPrompt: String,
        moodTierName: String,
        event: String,
        reconnectCount: Int
    ): Result {
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            return Result.Fail("missing key, base URL, or model")
        }
        val endpoint = normalizeEndpoint(baseUrl)
        val request = ChatRequest(
            model = model,
            max_tokens = 300,   // reasoning models spend budget thinking first
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
            if (!response.isSuccessful) {
                val errBody = response.errorBody()?.string()?.take(200) ?: "no body"
                return Result.Fail("HTTP ${response.code()}: $errBody")
            }
            val content = response.body()?.choices?.firstOrNull()?.message?.content
            if (content.isNullOrBlank()) {
                Result.Fail("model returned no content (try a larger max_tokens or another model)")
            } else {
                Result.Ok(content.trim())
            }
        } catch (e: Exception) {
            Result.Fail(e.message?.take(120) ?: "network error")
        }
    }

    fun authHeader(key: String) = "Bearer $key"

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
        @Header("Authorization") auth: String,
        @Body body: ChatRequest
    ): Call<ChatResponse>
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
