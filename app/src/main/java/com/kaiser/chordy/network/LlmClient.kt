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
        // Measured NIM latency: median ~5s, spikes past 6s, plus mobile-network
        // overhead. User-supplied REASONING models legitimately spend 20-40s+
        // thinking before the line — 30s cut them off mid-thought. 60s + one
        // in-client retry (below) absorbs all of it.
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
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
            // 1000 tokens: reasoning models burn most of this on hidden/inline
            // thinking before the actual line. 300 starved them into empty content.
            max_tokens = 1000,
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
            var response = api.chat(endpoint, authHeader(apiKey), request).execute()
            // One retry for transient trouble (timeouts, 429 shared-pool, 5xx).
            // Auth/config errors (401/403/404) fail fast — retrying those is noise.
            if (!response.isSuccessful && response.code() in RETRYABLE_CODES) {
                response = api.chat(endpoint, authHeader(apiKey), request).execute()
            }
            if (!response.isSuccessful) {
                val errBody = response.errorBody()?.string()?.take(200) ?: "no body"
                return Result.Fail("HTTP ${response.code()}: $errBody")
            }
            extractSpokenLine(response.body()?.choices?.firstOrNull()?.message)
        } catch (e: Exception) {
            // IOException covers SocketTimeoutException. Retry once, report real reason.
            val reason = when (e) {
                is java.io.IOException -> "network timeout — the API is slow or unreachable"
                else -> e.message?.take(120) ?: "network error"
            }
            try {
                val response = api.chat(endpoint, authHeader(apiKey), request).execute()
                if (!response.isSuccessful) {
                    val errBody = response.errorBody()?.string()?.take(200) ?: "no body"
                    Result.Fail("HTTP ${response.code()}: $errBody")
                } else {
                    extractSpokenLine(response.body()?.choices?.firstOrNull()?.message)
                }
            } catch (e2: Exception) {
                Result.Fail("$reason (retried once, still failing)")
            }
        }
    }

    /**
     * Pull the SPOKEN line out of a chat message, thinking stripped.
     *
     * Reasoning models (user-supplied: DeepSeek-R1 style, Qwen3, GLM, etc.)
     * inline their chain
     * (or <thinking>, or an UNTERMINATED block when the token cap hits mid-thought).
     * Showing that raw made Chordy narrate his thoughts instead of speaking.
     *
     * Separate reasoning fields (reasoning / reasoning_content) are simply
     * ignored — content is the speech, everything else is internals.
     */
    private fun extractSpokenLine(message: Message?): Result {
        val content = message?.content
        if (content.isNullOrBlank()) {
            return Result.Fail(
                "model returned no content — if it's a reasoning model, its thinking may have eaten the whole token budget (try a higher max_tokens or a non-reasoning model)"
            )
        }
        var line = content
        // terminated think blocks, both spellings, multiline
        line = line.replace(
            Regex("<think(?:ing)?>[\\s\\S]*?</think(?:ing)?>", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        // unterminated think block: everything after the opening tag is thinking
        val openTag = Regex("<think(?:ing)?>", RegexOption.IGNORE_CASE).find(line)
        if (openTag != null) {
            line = line.substring(0, openTag.range.first).trim()
        }
        return if (line.isBlank()) {
            Result.Fail("model only returned thinking, no line — try a non-reasoning model or raise max_tokens")
        } else {
            Result.Ok(line)
        }
    }

    fun authHeader(key: String) = "Bearer $key"

    /** HTTP codes worth one retry (rate-limit + server hiccups — not auth). */
    private val RETRYABLE_CODES = setOf(429, 500, 502, 503, 504)

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
data class Message(
    val role: String,
    val content: String? = null,
    // Reasoning-model fields — deliberately NOT displayed anywhere. DeepSeek-style
    // APIs use reasoning_content; NIM/others use reasoning. Thinking never
    // reaches the bubble; extractSpokenLine only trusts `content`.
    val reasoning: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null
)

@Serializable
data class ChatResponse(val choices: List<Choice> = emptyList())

@Serializable
data class Choice(val index: Int = 0, val message: Message? = null)
