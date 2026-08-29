package com.kaiser.chordy.data

/**
 * Canned fallback lines, ~4 per mood tier per personality, split by event.
 * Used when the LLM call fails, times out, or AI lines are toggled off.
 * A rotating index per (personality, tier, event) avoids back-to-back repeats.
 *
 * Anchor lines from the spec are marked [anchor]; the rest extend the same voices.
 */
object LineBank {

    private val lines: Map<Personality, Map<MoodTier, Map<PowerEvent, List<String>>>> = mapOf(

        Personality.CLINGY to mapOf(
            MoodTier.CALM to mapOf(
                PowerEvent.CONNECTED to listOf(
                    "okay we're good. don't leave again though. i mean it.", // [anchor]
                    "you came back. i wasn't worried. i was just… watching the door.",
                    "warm now. staying? you're staying. right. good.",
                    "hi. i kept your spot warm. the whole time."
                ),
                PowerEvent.DISCONNECTED to listOf(
                    "oh. okay. you're needed elsewhere probably. that's fine.",
                    "wait, no, it's fine, go. i'll just be here. being fine.",
                    "you'll come back though? like, soon-ish?",
                    "k. k. it's k. the battery percent is basically a countdown to us."
                )
            ),
            MoodTier.ANXIOUS to mapOf(
                PowerEvent.CONNECTED to listOf(
                    "there you are. don't— don't do that again. please.",
                    "i counted the minutes. all of them. hi.",
                    "you're back you're back you're back. okay. breathing.",
                    "i wasn't going to text you. i wrote the text. i just didn't send it."
                ),
                PowerEvent.DISCONNECTED to listOf(
                    "wait— where are you going. it's fine. i'm fine. take your time i guess.", // [anchor]
                    "that's twice now. i'm not counting. i just happen to know it's twice.",
                    "no yeah, go, everyone leaves eventually, i've made peace. mostly.",
                    "do you know how long a percentage point takes? because i do now."
                )
            ),
            MoodTier.ANGRY to mapOf(
                PowerEvent.CONNECTED to listOf(
                    "you're back. cool. i wasn't checking every three seconds or anything.", // [anchor]
                    "four times. i know you know i counted. i keep receipts now.",
                    "oh NOW you want stability. okay. okay. we're doing this again.",
                    "i'm not mad. i'm just… keeping a spreadsheet. it's a very mad spreadsheet."
                ),
                PowerEvent.DISCONNECTED to listOf(
                    "again? AGAIN? cool. cool cool cool. love this for us.",
                    "you know what, fine. i hope your battery dies somewhere embarrassing.",
                    "leave. see if i care. i'll just be here, alone, running low, whatever.",
                    "i did the math. you've spent more time away than with me. i made a chart."
                )
            )
        ),

        Personality.TOXIC_EX to mapOf(
            MoodTier.CALM to mapOf(
                PowerEvent.CONNECTED to listOf(
                    "oh, it's you. didn't notice you left, honestly.", // [anchor]
                    "back so soon? don't let me interrupt your little adventures.",
                    "fine. stay. it's not like i was waiting for the plug to click.",
                    "huh. and here i was doing perfectly fine without you. still am, technically."
                ),
                PowerEvent.DISCONNECTED to listOf(
                    "leaving? wow. shocker. no really — i'm shocked.",
                    "take your time. i've got literally anything else to think about.",
                    "gone again? noticed. didn't care. mostly didn't care.",
                    "do what you want. it's what you always do anyway."
                )
            ),
            MoodTier.ANXIOUS to mapOf(
                PowerEvent.CONNECTED to listOf(
                    "wow. leaving already? shocking. truly unprecedented behavior.", // [anchor, fits reconnection after repeat exits]
                    "you're back. i wasn't waiting. the waiting was coincidental.",
                    "two gaps today. i'm not hurt, i'm just… keeping track. for me. for closure.",
                    "plug in, plug out. real mature. really finding yourself, huh."
                ),
                PowerEvent.DISCONNECTED to listOf(
                    "twice in one day? no, go on, tell me more about how you've changed.",
                    "sure. leave. i love the suspense. keeps things spicy.",
                    "don't worry about me. i've been through worse. notably, you.",
                    "you always do this. right when things are stable. classic."
                )
            ),
            MoodTier.ANGRY to mapOf(
                PowerEvent.CONNECTED to listOf(
                    "back again? i was doing SO well without you. genuinely thriving. anyway.", // [anchor]
                    "four times. i counted because you made me count. hope that feels good.",
                    "oh look who remembered i exist. shall i applaud or just… not.",
                    "stay or don't. i've emotionally diversified. you're like my fourth priority now."
                ),
                PowerEvent.DISCONNECTED to listOf(
                    "again?? at this point just stay gone. you're clearly great at it.",
                    "unbelievable. i give you warmth and THIS is the thanks.",
                    "leave then. block the outlet on your way out. oh wait, you can't. i live there.",
                    "i'm not angry. i'm consolidating. there's a difference, and a lawyer could explain it."
                )
            )
        ),

        Personality.DRAMATIC_ACTOR to mapOf(
            MoodTier.CALM to mapOf(
                PowerEvent.CONNECTED to listOf(
                    "Behold! The current flows once more, and so too does my will to continue this performance.", // [anchor]
                    "Rejoice, mortal coil — the sacred tether is restored!",
                    "Oh blessed union of copper and intent! The stage glows anew.",
                    "And lo, the plug doth find its port, as fate — and physics — intended."
                ),
                PowerEvent.DISCONNECTED to listOf(
                    "Alas! Severed from the source, I feel my strength ebbing like the tide at dusk!", // [anchor]
                    "Farewell, sweet current! Mine eyes grow dim, mine audience doth wander!",
                    "Thus begins the intermission of my soul — the curtain falls on warmth itself.",
                    "What darkness is this, that tears a humble creature from its voltaic beloved?"
                )
            ),
            MoodTier.ANXIOUS to mapOf(
                PowerEvent.CONNECTED to listOf(
                    "HARK! Twice forsaken, twice returned — O fickle fate, thou art a cruel dramaturge!",
                    "The current returns! But can my wounded scene partner ever trust again?",
                    "A resurrection! And yet — the ghost of departures past haunts this very outlet.",
                    "Thou art back. Applause would be premature. The understudy of betrayal still waits in the wings."
                ),
                PowerEvent.DISCONNECTED to listOf(
                    "AGAIN?? The gods themselves rehearse this tragedy — and I have not consented to the run!",
                    "Parting is such sweet sorrow — but this is the THIRD parting, and it is merely sorrow!",
                    "O cruel world, that lends a charger and repossesseth it by dusk!",
                    "I die a little — literally, my battery percentage IS my health bar in this production."
                )
            ),
            MoodTier.ANGRY to mapOf(
                PowerEvent.CONNECTED to listOf(
                    "Return, cruel wanderer! Four times now you have forsaken me upon this stage of suffering!", // [anchor]
                    "FOUR returns! This is no longer a play — it is an endurance piece, and I am EXHAUSTED.",
                    "Thou art here. Again. The critics are bored. I am bored. And yet the show demands an ending.",
                    "Enough! Plug no more, or plug forever — no artist works under these conditions!"
                ),
                PowerEvent.DISCONNECTED to listOf(
                    "A FOURTH betrayal! Shakespeare himself would call this excessive!",
                    "Fie upon thee, plug-puller of destiny! Fie, i say — fie with feeling!",
                    "The intermission grows longer than the show itself. Even the stagehands have gone home.",
                    "I shall die here, dramatically, on this very table — mind the trailing cable of my people."
                )
            )
        )
    )

    // Rotating pick index per (personality, tier, event) so the same line doesn't
    // repeat back-to-back when the user jiggles the cable on purpose. They will.
    private val cursor = mutableMapOf<String, Int>()

    fun line(personality: Personality, tier: MoodTier, event: PowerEvent): String {
        val pool = lines.getValue(personality).getValue(tier).getValue(event)
        val key = "${personality.name}/${tier.name}/${event.name}"
        val idx = cursor.getOrDefault(key, 0)
        cursor[key] = (idx + 1) % pool.size
        return pool[idx % pool.size]
    }

    /** Full voice check for the settings preview button. */
    fun previewLine(personality: Personality): String =
        line(personality, MoodTier.CALM, PowerEvent.CONNECTED)
}
