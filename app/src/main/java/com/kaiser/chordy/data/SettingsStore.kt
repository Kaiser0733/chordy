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

    /** Selected persona id (PersonaStore world) — falls back to Clingy. */
    var selectedPersonaId: String
        get() = plain.getString(KEY_PERSONA_ID, PersonaStore.ID_CLINGY) ?: PersonaStore.ID_CLINGY
        set(value) = plain.edit().putString(KEY_PERSONA_ID, value).apply()

    /**
     * Which BUNDLED endpoint is the default (BundledEndpoints ids: "groq",
     * "nim"). Only applies when the user hasn't typed their own API override —
     * any override field filled wins over this.
     */
    var defaultEndpointId: String
        get() = plain.getString(KEY_DEFAULT_ENDPOINT, BundledEndpoints.GROQ.id) ?: BundledEndpoints.GROQ.id
        set(value) = plain.edit().putString(KEY_DEFAULT_ENDPOINT, value).apply()

    // ---------- switches ----------

    /**
     * AI lines are ON by default now — the app ships with a bundled endpoint,
     * so the default experience is the LLM personas. Canned lines are the
     * emergency fallback, not the main act.
     */
    var aiLinesEnabled: Boolean
        get() = plain.getBoolean(KEY_AI_LINES, true)
        set(value) = plain.edit().putBoolean(KEY_AI_LINES, value).apply()

    /** React to device unlock. Default ON. */
    var reactToUnlock: Boolean
        get() = plain.getBoolean(KEY_REACT_UNLOCK, true)
        set(value) = plain.edit().putBoolean(KEY_REACT_UNLOCK, value).apply()

    /** React to foreground app changes. Default ON (cooldown still applies). */
    var reactToAppOpens: Boolean
        get() = plain.getBoolean(KEY_REACT_APP_OPENS, true)
        set(value) = plain.edit().putBoolean(KEY_REACT_APP_OPENS, value).apply()

    /** Timestamp of the last APP_OPENED reaction — gates the 5-minute cooldown. */
    var lastAppReactionTimestamp: Long
        get() = plain.getLong(KEY_LAST_APP_REACTION_TS, 0L)
        set(value) = plain.edit().putLong(KEY_LAST_APP_REACTION_TS, value).apply()

    /**
     * Master switch: is the PowerMonitorService supposed to be running?
     * Persisted so "paused" survives reboot (BootReceiver checks this).
     * Killing the app from recents also stops the service — the Settings
     * screen detects that and offers to wake him back up.
     */
    var monitoringEnabled: Boolean
        get() = plain.getBoolean(KEY_MONITORING_ENABLED, true)
        set(value) = plain.edit().putBoolean(KEY_MONITORING_ENABLED, value).apply()

    // ---------- LLM config (bundled NIM defaults, user-overridable) ----------

    var llmApiKey: String
        get() = secure.getString(KEY_LLM_KEY, "") ?: ""
        set(value) = secure.edit().putString(KEY_LLM_KEY, value).apply()

    /** User base URL — blank means "use the bundled NIM endpoint". */
    var llmBaseUrl: String
        get() = plain.getString(KEY_LLM_URL, "") ?: ""
        set(value) = plain.edit().putString(KEY_LLM_URL, value).apply()

    var llmModel: String
        get() = plain.getString(KEY_LLM_MODEL, "") ?: ""   // blank = bundled model
        set(value) = plain.edit().putString(KEY_LLM_MODEL, value).apply()

    // ---------- TTS config ----------

    var ttsApiKey: String
        get() = secure.getString(KEY_TTS_KEY, "") ?: ""
        set(value) = secure.edit().putString(KEY_TTS_KEY, value).apply()

    var ttsBaseUrl: String
        get() = plain.getString(KEY_TTS_URL, "") ?: ""
        set(value) = plain.edit().putString(KEY_TTS_URL, value).apply()

    /** Voice ID per persona id; empty string = no audio for that persona. */
    fun voiceIdFor(personaId: String): String =
        plain.getString(KEY_VOICE_PREFIX + personaId, "") ?: ""

    fun setVoiceId(personaId: String, id: String) {
        plain.edit().putString(KEY_VOICE_PREFIX + personaId, id).apply()
    }

    fun allVoiceIds(personaIds: List<String>): Map<String, String> =
        personaIds.associateWith { voiceIdFor(it) }

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
        const val KEY_PERSONA_ID = "selected_persona_id"
        const val KEY_DEFAULT_ENDPOINT = "default_endpoint_id"
        const val KEY_AI_LINES = "ai_lines_enabled"
        const val KEY_LLM_KEY = "llm_api_key"
        const val KEY_LLM_URL = "llm_base_url"
        const val KEY_LLM_MODEL = "llm_model"
        const val KEY_TTS_KEY = "tts_api_key"
        const val KEY_TTS_URL = "tts_base_url"
        const val KEY_VOICE_PREFIX = "voice_id_"
        const val KEY_REACT_UNLOCK = "react_to_unlock"
        const val KEY_REACT_APP_OPENS = "react_to_app_opens"
        const val KEY_LAST_APP_REACTION_TS = "last_app_reaction_ts"
        const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        const val KEY_FIRST_RUN = "first_run_done"
    }
}
