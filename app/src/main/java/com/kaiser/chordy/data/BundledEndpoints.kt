package com.kaiser.chordy.data

import com.kaiser.chordy.BuildConfig

/**
 * The bundled default endpoints. Users pick between these in Settings
 * (or bring their own API as an override). Groq ships first — its LPU
 * inference measured ~1s vs NIM's ~5s median, which is the difference
 * between Chordy replying while you're still looking at the bubble and
 * staring at "…" for ages.
 *
 * Keys arrive at build time via GitHub Actions secrets and land only in
 * the APK (BuildConfig) — never in a commit.
 */
data class BundledEndpoint(
    val id: String,
    val label: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String
)

object BundledEndpoints {

    val GROQ = BundledEndpoint(
        id = "groq",
        label = "Groq — gpt-oss-120b (fast, ~1s)",
        baseUrl = "https://api.groq.com/openai/v1",
        model = "openai/gpt-oss-120b",
        apiKey = BuildConfig.GROQ_API_KEY
    )

    val NIM = BundledEndpoint(
        id = "nim",
        label = "NVIDIA NIM — gpt-oss-120b (stable)",
        baseUrl = "https://integrate.api.nvidia.com/v1",
        model = "openai/gpt-oss-120b",
        apiKey = BuildConfig.NIM_API_KEY
    )

    /** Groq first — it's the fast one, so it's the default default. */
    val all: List<BundledEndpoint> = listOf(GROQ, NIM)

    fun byId(id: String): BundledEndpoint? = all.firstOrNull { it.id == id }
}
