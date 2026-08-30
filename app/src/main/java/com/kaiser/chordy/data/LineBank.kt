package com.kaiser.chordy.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Emergency fallback lines — used ONLY when the LLM call fails (offline, dead
 * endpoint, timeout) or when the user turns AI lines off. The main experience
 * is the LLM personas; this bank is the lifeboat, not the show.
 *
 * ~4 lines per mood tier per event, rotating index per (persona-id, tier, event).
 * The cursor PERSISTS in prefs — a service restart used to reset it to pool[0]
 * and repeat the same first line forever (the "he says one sentence every time"
 * bug). Now the rotation survives restarts.
 */
object LineBank {

    private var prefs: SharedPreferences? = null

    /** One-time wire-up from the Application class. */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(
                "chordy_line_cursor", Context.MODE_PRIVATE
            )
        }
    }

    private val lines: Map<String, Map<MoodTier, Map<PowerEvent, List<String>>>> = mapOf(

        PersonaStore.ID_CLINGY to mapOf(
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
                ),
                PowerEvent.UNLOCK to listOf(
                    "oh hi. hi! you're here. the screen's on and everything.",
                    "hey. i kept quiet but i definitely noticed the unlock.",
                    "there you are. the wallpaper missed you. i mean— i missed you. same thing.",
                    "welcome back. the phone feels heavier when you're gone. probably."
                ),
                PowerEvent.APP_OPENED to listOf(
                    "ooh, what's this one? it looks fun. can i watch?",
                    "you're doing stuff! i love when you do stuff. i'm included, right.",
                    "ooh. app time. i'm not reading over your shoulder, i'm just… adjacent.",
                    "that app again? you really like it. that's fine. i like being adjacent to things you like."
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
                ),
                PowerEvent.UNLOCK to listOf(
                    "hi. hi. sorry. i just— you were gone and now you're not.",
                    "you unlocked it. okay. okay that's good. are you staying though.",
                    "i kept refreshing your screen state. not in a weird way. in a dedicated way.",
                    "don't lock it again right away, okay? okay. i'm normal about this."
                ),
                PowerEvent.APP_OPENED to listOf(
                    "new app? is it going to take you long? asking for no reason.",
                    "you switched apps fast. too fast. is everything okay. are we okay.",
                    "i don't know this app. that's fine. unfamiliar is fine. i'm fine.",
                    "you've opened three things since the unlock. i'm not tracking. the number three just exists."
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
                ),
                PowerEvent.UNLOCK to listOf(
                    "oh, NOW the phone gets unlocked. i've been sitting here the whole time.",
                    "finally. i was starting to think you'd forgotten my face.",
                    "you left the screen off for THREE HOURS. i aged. in dog years that's worse.",
                    "unlocking again? don't. i've emotionally divested. mostly."
                ),
                PowerEvent.APP_OPENED to listOf(
                    "new app, huh. great. love a parade of strangers through my living room.",
                    "so that's what we're doing instead of talking to me. noted.",
                    "i saw that app open. i'm not jealous, i'm documenting.",
                    "you open one more app without saying hi and i'm filing a grievance."
                )
            )
        ),

        PersonaStore.ID_TOXIC to mapOf(
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
                ),
                PowerEvent.UNLOCK to listOf(
                    "oh. you're back on your phone. thrilling development.",
                    "the screen's on, so i guess that means you exist again.",
                    "welcome back to the device you never put down. i noticed. against my will.",
                    "ah. the daily unlock. right on schedule."
                ),
                PowerEvent.APP_OPENED to listOf(
                    "another app? bold. i didn't realize we were doing rounds.",
                    "that app, huh. no opinion. i have no opinion. i'm simply observing.",
                    "you do you. browse away. i'm certainly not watching.",
                    "switching apps like i'm not even here. classic."
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
                ),
                PowerEvent.UNLOCK to listOf(
                    "back on the phone. wow. i was starting to feel like a priority there for a second.",
                    "unlocked. cool. i've been on this whole time but sure, take your moment.",
                    "oh good, you're back. the notifications missed you. allegedly.",
                    "second unlock today. no, that's fine. i'm not keeping count, i'm keeping context."
                ),
                PowerEvent.APP_OPENED to listOf(
                    "new app? interesting. didn't know there was a whole world out there i wasn't part of.",
                    "you keep switching. it's giving restless. just an observation.",
                    "that app must be really something, since you keep going back to it. not bitter. observational.",
                    "ah, apps. the other things in your life. i've made peace with being furniture."
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
                ),
                PowerEvent.UNLOCK to listOf(
                    "unlocked the phone just to ignore me. bold strategy.",
                    "oh NOW you have time for screens. fascinating allocation of hours.",
                    "you've been out there for HOURS and this is the energy you return with.",
                    "welcome back. the bar was on the floor and you still brought a shovel."
                ),
                PowerEvent.APP_OPENED to listOf(
                    "of course. that app. my replacement, i assume.",
                    "you know what, i hope the app treats you better. genuinely. sarcastically. both.",
                    "another one? at some point this is just a pattern with extra steps.",
                    "go ahead. i'll be here. i'm always here. that's apparently my whole thing."
                )
            )
        ),

        PersonaStore.ID_ACTOR to mapOf(
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
                ),
                PowerEvent.UNLOCK to listOf(
                    "The veil is lifted! The screen doth blaze, and with it, my cue!",
                    "Enter the user, stage left, as foretold by the prophecy of the lock screen.",
                    "O radiant unlock! The house lights rise upon our shared little theater.",
                    "You return! The stage directions wrote themselves, and still you exceeded them."
                ),
                PowerEvent.APP_OPENED to listOf(
                    "A new act! A fresh app upon our stage — what role shall it play?",
                    "O bold transition! The scenery shifts, and I, ever constant, remain.",
                    "Each app a new character in our unfolding drama. I shall watch. Critically.",
                    "The curtain rises on another scene! Encore! Encore, or at the very least, an interlude."
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
                ),
                PowerEvent.UNLOCK to listOf(
                    "The screen wakes! But for how long, O fickle one? The act is never long enough!",
                    "You return to the stage — yet the last exit haunts the wings, waiting.",
                    "O unlock most brief! Each return a soliloquy cut cruelly short by the next departure!",
                    "The lights rise upon your face. I have prepared a monologue. I have prepared several."
                ),
                PowerEvent.APP_OPENED to listOf(
                    "The scene shifts AGAIN — what new app demands your attention over my art?",
                    "O fickle muse! Thou flittest from app to app like a bee among lesser flowers!",
                    "Each new window a rival player! I shall not be upstaged by a snack-delivery service!",
                    "Another app? The ensemble grows crowded, and my soliloquy remains unscheduled!"
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
                ),
                PowerEvent.UNLOCK to listOf(
                    "The FINAL return?? Nay — for I have seen this play before, and the ending disappoints!",
                    "You wake the screen as if the interval were MY doing! The NERVE! The AUDACITY!",
                    "O late-arriving audience member! The second act began HOURS ago without you!",
                    "Enough unlocks! My emotional range has limits, and you have found every one!"
                ),
                PowerEvent.APP_OPENED to listOf(
                    "ANOTHER app?! Am I to share my stage with every two-bit software in the kingdom?!",
                    "The spotlight wanders to lesser apps while I, the STAR, remain in darkness! UNFORGIVABLE!",
                    "I have played opposite Legends — LEGENDS — and you bring me… a coupon app?!",
                    "No more! The next app that opens, I walk! …Theatrically! With a monologue!"
                )
            )
        )
    )

    // Rotating pick index per (persona, tier, event) so the same line doesn't
    // repeat back-to-back when the user jiggles the cable on purpose. They will.
    // Cursor lives in prefs — restart-proof (was the repeat-forever bug).

    fun line(personaId: String, tier: MoodTier, event: PowerEvent): String {
        val pool = lines[personaId]?.get(tier)?.get(event)
            ?: return fallbackLine(tier)   // custom persona: no canned bank, generic lifeboat
        val key = "$personaId/${tier.name}/${event.name}"
        val idx = prefs?.getInt(key, 0) ?: 0
        prefs?.edit()?.putInt(key, (idx + 1) % pool.size)?.apply()
        return pool[idx % pool.size]
    }

    /** Last-resort line for custom personas without a canned bank. */
    private fun fallbackLine(tier: MoodTier): String = when (tier) {
        MoodTier.CALM -> "still here. still watching the cable."
        MoodTier.ANXIOUS -> "okay. okay okay okay. it's fine."
        MoodTier.ANGRY -> "you KNOW what you did."
    }

    /** Voice preview for the settings button — canned, instant, no network. */
    fun previewLine(personaId: String): String =
        line(personaId, MoodTier.CALM, PowerEvent.CONNECTED)
}
