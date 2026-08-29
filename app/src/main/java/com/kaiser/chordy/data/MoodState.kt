package com.kaiser.chordy.data

/**
 * What Chordy just noticed. Power events remain the emotional core; presence
 * events (UNLOCK, APP_OPENED) are the "he noticed you" layer on top.
 */
enum class PowerEvent { CONNECTED, DISCONNECTED, UNLOCK, APP_OPENED }

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
