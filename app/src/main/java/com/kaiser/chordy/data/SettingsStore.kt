package com.kaiser.chordy.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Two stores, one face:
 *  - plain prefs (chordy_prefs) for everything not secret
 *  - EncryptedSharedPreferences (chordy_secure_prefs) for API keys only
 *
 * Voice IDs are configurable strings per personality (they depend on whichever
 * TTS provider the user enables), with sane empty-string defaults meaning "audio off".
 */
class SettingsStore(context: Context) {

    private val plain: SharedPreferences =
        context.getSharedPreferences("chordy_prefs", Context.MODE_PRIVATE)

    private val secure: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "chordy_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Corrupt keystore entry (e.g. after a restore): nuke and rebuild rather
        // than crash. Keys are re-entered; the app survives. Ponytail call —
        // the alternative is a permanently bricked app over four strings.
        context.deleteSharedPreferences("chordy_secure_prefs")
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "chordy_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ---------- mood state (plain prefs — nothing secret about a grumpy counter) ----------

    var reconnectCount: Int
        get() = plain.getInt(KEY_RECONNECTS, 0)
        set(value) = plain.edit().putInt(KEY_RECONNECTS, value).apply()

    var lastDisconnectTs: Long
        get() = plain.getLong(KEY_DISCONNECT_TS, 0L)
        set(value) = plain.edit().putLong(KEY_DISCONNECT_TS, value).apply()

    var lastConnectTs: Long
        get() = plain.getLong(KEY_CONNECT_TS, 0L)
        set(value) = plain.edit().putLong(KEY_CONNECT_TS, value).apply()

    var selectedPersonality: Personality
        get() = Personality.fromName(plain.getString(KEY_PERSONALITY, null))
        set(value) = plain.edit().putString(KEY_PERSONALITY, value.name).apply()

    // ---------- switches ----------

    /** true = use LLM lines (with canned fallback); false = canned only, zero cost. */
    var aiLinesEnabled: Boolean
        get() = plain.getBoolean(KEY_AI_LINES, false)
        set(value) = plain.edit().putBoolean(KEY_AI_LINES, value).apply()

    // ---------- LLM config ----------

    var llmApiKey: String
        get() = secure.getString(KEY_LLM_KEY, "") ?: ""
        set(value) = secure.edit().putString(KEY_LLM_KEY, value).apply()

    var llmBaseUrl: String
        get() = plain.getString(KEY_LLM_URL, "") ?: ""   // base URL isn't a secret
        set(value) = plain.edit().putString(KEY_LLM_URL, value).apply()

    var llmModel: String
        get() = plain.getString(KEY_LLM_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini"
        set(value) = plain.edit().putString(KEY_LLM_MODEL, value).apply()

    // ---------- TTS config ----------

    var ttsApiKey: String
        get() = secure.getString(KEY_TTS_KEY, "") ?: ""
        set(value) = secure.edit().putString(KEY_TTS_KEY, value).apply()

    var ttsBaseUrl: String
        get() = plain.getString(KEY_TTS_URL, "") ?: ""
        set(value) = plain.edit().putString(KEY_TTS_URL, value).apply()

    /** Voice ID per personality; empty string = no audio for that personality. */
    fun voiceIdFor(p: Personality): String =
        plain.getString(KEY_VOICE_PREFIX + p.name, "") ?: ""

    fun setVoiceId(p: Personality, id: String) {
        plain.edit().putString(KEY_VOICE_PREFIX + p.name, id).apply()
    }

    fun allVoiceIds(): Map<Personality, String> =
        Personality.entries.associateWith { voiceIdFor(it) }

    // ---------- misc ----------

    var firstRunDone: Boolean
        get() = plain.getBoolean(KEY_FIRST_RUN, false)
        set(value) = plain.edit().putBoolean(KEY_FIRST_RUN, value).apply()

    fun resetCounter() {
        reconnectCount = 0
    }

    private companion object {
        const val KEY_RECONNECTS = "reconnect_count"
        const val KEY_DISCONNECT_TS = "last_disconnect_ts"
        const val KEY_CONNECT_TS = "last_connect_ts"
        const val KEY_PERSONALITY = "selected_personality"
        const val KEY_AI_LINES = "ai_lines_enabled"
        const val KEY_LLM_KEY = "llm_api_key"
        const val KEY_LLM_URL = "llm_base_url"
        const val KEY_LLM_MODEL = "llm_model"
        const val KEY_TTS_KEY = "tts_api_key"
        const val KEY_TTS_URL = "tts_base_url"
        const val KEY_VOICE_PREFIX = "voice_id_"
        const val KEY_FIRST_RUN = "first_run_done"
    }
}
