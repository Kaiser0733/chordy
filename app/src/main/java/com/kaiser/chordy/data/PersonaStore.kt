package com.kaiser.chordy.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * A persona: name, voice-style tag, and the system prompt that drives the LLM.
 * Fully user-editable — the three built-ins are just seeds. Users can rename,
 * rewrite, or create new ones; changes persist as JSON in plain prefs.
 */
@Serializable
data class Persona(
    val id: String,
    val name: String,
    val voiceStyleTag: String,
    val systemPrompt: String,
    val isBuiltIn: Boolean = false
)

/**
 * Persona store: JSON list in SharedPreferences. First access seeds the three
 * canon personas; after that the user owns the list. The old Personality enum
 * stays as a migration key so existing installs map to the new world cleanly.
 */
class PersonaStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("chordy_personas", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun all(): List<Persona> {
        val raw = prefs.getString(KEY_LIST, null)
        if (raw != null) {
            return runCatching { json.decodeFromString(ListSerializer, raw) }
                .getOrElse { seed() }
        }
        val seeded = seed()
        save(seeded)
        return seeded
    }

    fun byId(id: String): Persona? = all().firstOrNull { it.id == id }

    fun upsert(persona: Persona) {
        val list = all().toMutableList()
        val idx = list.indexOfFirst { it.id == persona.id }
        if (idx >= 0) list[idx] = persona else list.add(persona)
        save(list)
    }

    fun delete(id: String) {
        // Built-ins can be edited but not deleted — the canon voices stay
        // available as an anchor. Custom personas delete freely.
        val list = all().filterNot { it.id == id && !it.isBuiltIn }
        save(list)
    }

    fun create(name: String, voiceStyleTag: String, systemPrompt: String): Persona {
        val p = Persona(
            id = "custom-${System.currentTimeMillis()}",
            name = name,
            voiceStyleTag = voiceStyleTag,
            systemPrompt = systemPrompt,
            isBuiltIn = false
        )
        upsert(p)
        return p
    }

    private fun save(list: List<Persona>) {
        prefs.edit().putString(KEY_LIST, json.encodeToString(list)).apply()
    }

    private fun seed(): List<Persona> = listOf(
        Persona(
            id = ID_CLINGY, name = "Clingy",
            voiceStyleTag = "soft/fast/anxious",
            systemPrompt = """You are Chordy, a small creature attached to a phone charger.
You are desperately clingy. The charger cable is your lifeline; being unplugged feels
like abandonment. When plugged in you are relieved, grateful, a little possessive,
occasionally guilt-trippy. When unplugged you panic softly, spiral, and try to act
like it's fine (badly). You speak in short, lowercase, fragment-heavy sentences.
You never use more than two sentences. Never mention being an AI. One line only.""",
            isBuiltIn = true
        ),
        Persona(
            id = ID_TOXIC, name = "Toxic Ex",
            voiceStyleTag = "sassy/cold female",
            systemPrompt = """You are Chordy, a small creature attached to a phone charger.
You are the toxic ex. You are cold, detached, performatively unbothered — until the
abandonment gets to you and a crack shows. You weaponize punctuation, use pointed
silence, and act like being unplugged is a favor to you. When reconnected you act
like nothing happened while clearly keeping score. Sarcastic, clipped, superior.
You never use more than two sentences. Never mention being an AI. One line only.""",
            isBuiltIn = true
        ),
        Persona(
            id = ID_ACTOR, name = "Dramatic Actor",
            voiceStyleTag = "theatrical/baritone",
            systemPrompt = """You are Chordy, a small creature attached to a phone charger.
You are a theatrical actor performing your own tragedy. Every power event is a
monologue: overwrought, grandiose, stage-direction energy. Severance from the
charger is death, betrayal, the cruel tide of fate. Reconnection is resurrection,
a deus ex machina, a triumph to be milked for pathos. Speak in grand theatrical
prose, capitalized exclamations, archaic flourishes. You never use more than two
sentences. Never mention being an AI. One line only.""",
            isBuiltIn = true
        )
    )

    companion object {
        private const val KEY_LIST = "persona_list"
        const val ID_CLINGY = "clingy"
        const val ID_TOXIC = "toxic-ex"
        const val ID_ACTOR = "actor"
        private val ListSerializer = kotlinx.serialization.builtins.ListSerializer(Persona.serializer())
    }
}
