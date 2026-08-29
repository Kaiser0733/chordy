package com.kaiser.chordy.data

/**
 * What happened to the cable. DRIVING the reaction.
 */
enum class PowerEvent { CONNECTED, DISCONNECTED }

/**
 * Mood escalation tiers. 0–1 reconnects = CALM, 2–3 = ANXIOUS, 4+ = ANGRY.
 * ANGRIER than ANGRY doesn't exist — four plus is where the ladder stops.
 */
enum class MoodTier {
    CALM, ANXIOUS, ANGRY;

    companion object {
        fun fromReconnectCount(count: Int): MoodTier = when {
            count >= 4 -> ANGRY
            count >= 2 -> ANXIOUS
            else -> CALM
        }
    }
}
