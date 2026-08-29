package com.kaiser.chordy.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit
import android.util.Base64

/**
 * Cloud TTS client, Fish Audio-style: POST {text, voice id} -> raw audio bytes
 * that MediaPlayer can play. Also understands OpenAI's speech endpoint shape
 * (JSON with base64 audio) since plenty of compatible providers speak it.
 *
 * Failure policy: return null on ANY failure — audio is a garnish, the text
 * bubble is the meal. Never crash, never block the UI.
 */
class TtsClient {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)   // speech synthesis can take a beat longer than chat
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    private val api: TtsApi = Retrofit.Builder()
        .baseUrl("https://localhost/")   // placeholder — @Url carries the real endpoint
        .client(okHttp)
        .build()
        .create(TtsApi::class.java)

    /**
     * @param baseUrl provider base, e.g. "https://api.fish.audio" — endpoint is
     *        derived the same way for all providers: POST {base}/v1/tts
     * @return decoded audio bytes ready for MediaPlayer, or null (skip audio silently)
     */
    fun synthesize(
        baseUrl: String,
        apiKey: String,
        voiceId: String,
        text: String
    ): ByteArray? {
        if (baseUrl.isBlank() || apiKey.isBlank() || voiceId.isBlank() || text.isBlank()) return null
        val endpoint = normalizeEndpoint(baseUrl)
        val payload = TtsRequest(
            text = text,
            voiceId = voiceId,
            format = "mp3"
        ).let { json.encodeToString(TtsRequest.serializer(), it) }
        val body: RequestBody = payload.toRequestBody("application/json".toMediaType())
        return try {
            val response = api.speak(endpoint, "Bearer $apiKey", body).execute()
            if (!response.isSuccessful) return null
            val bytes = response.body()?.bytes() ?: return null
            when {
                bytes.isEmpty() -> null
                // JSON envelope (OpenAI speech-style): {"audio": "<base64>"}
                looksLikeJson(bytes) -> decodeJsonAudio(bytes)
                else -> bytes // raw audio bytes — the happy path
            }
        } catch (e: Exception) {
            null // silent skip by contract
        }
    }

    private fun looksLikeJson(bytes: ByteArray): Boolean {
        val first = bytes.firstOrNull() ?: return false
        return first == '{'.code.toByte()
    }

    private fun decodeJsonAudio(bytes: ByteArray): ByteArray? = try {
        val envelope = json.decodeFromString(TtsEnvelope.serializer(), String(bytes))
        if (envelope.audio.isNullOrBlank()) null
        else Base64.decode(envelope.audio, Base64.DEFAULT)
    } catch (e: Exception) {
        null
    }

    internal fun normalizeEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/v1/tts")) trimmed
        else trimmed + "/v1/tts"
    }
}

interface TtsApi {
    @POST
    fun speak(
        @Url endpoint: String,
        @Header("Authorization") auth: String,
        @Body body: RequestBody
    ): retrofit2.Response<okhttp3.ResponseBody>
}

@Serializable
data class TtsRequest(
    val text: String,
    @SerialName("voice_id") val voiceId: String,
    val format: String = "mp3"
)

@Serializable
data class TtsEnvelope(
    val audio: String? = null  // base64-encoded audio from JSON-style providers
)
