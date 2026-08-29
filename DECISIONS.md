# Chordy — Architecture Decisions

Per-project decision log. Each entry: what, why, and what it would take to change.

## D1: SharedPreferences over Room for mood state
**What:** Mood state (reconnectCount, two timestamps, selected personality) persists in plain SharedPreferences.
**Why:** Five scalars. Room would add an entity, DAO, migration story, and KSP wiring for zero benefit. Ponytail ladder rung 5.
**Change trigger:** If per-event history or analytics ("line said at timestamp X") becomes a feature, that's the moment for Room.

## D2: Plain Views for the overlay, Compose only for Settings
**What:** The floating bubble + speech bubble are a custom View drawing on Canvas via WindowManager; Settings is Compose.
**Why:** Compose in an overlay window requires a lifecycleOwner/viewModelOwner hack and recomposes constantly — exactly the "heavy recomposition loop" the brief forbids for a view that sits on screen indefinitely. A draw-on-invalidate View is ~200 lines and repaints only on mood change or drag.
**Change trigger:** If the overlay grows complex interactive widgets (swipe actions, carousels), revisit Compose-in-overlay.

## D3: Canned-first, LLM swap-in
**What:** Every event shows a canned LineBank line instantly; if AI lines are enabled, an LLM call (3s timeout) runs on a worker and swaps the line in place if it wins the race against newer events.
**Why:** The bubble must never wait on the network. Instant text + optional upgrade is the only ordering that satisfies both "show text immediately" and "AI-generated lines."
**Change trigger:** None foreseeable; this is a UX contract, not a tech detail.

## D4: Retrofit with @Url endpoints, not per-provider interfaces
**What:** One Retrofit interface whose endpoint is a runtime parameter (`@Url`); base URLs normalize (`/v1` → `/v1/chat/completions`).
**Why:** Retrofit bakes base URLs at build time, but users configure providers in Settings. @Url keeps one interface for all OpenAI-compatible providers.
**Change trigger:** If a provider's path shape differs beyond the two accepted forms, add a second normalize rule.

## D5: EncryptedSharedPreferences only for the four secrets
**What:** API keys (LLM, TTS) live in EncryptedSharedPreferences; base URLs, model name, voice IDs, personality, counter in plain prefs.
**Why:** Keystore encryption for everything would be ceremony; base URLs aren't secrets. Corrupt-keystore fallback deletes and rebuilds the secure store (re-enter keys) rather than bricking the app.
**Change trigger:** If voice IDs ever become guessable secrets (they're provider resource IDs), move them over.

## D6: Mood tiers map to face drawing, not assets
**What:** BubbleView draws eyes/brows/mouth per tier with Canvas primitives.
**Why:** Zero asset payload, infinitely moodable, matches the app-icon face. Asset Lottie/animated-vector would be heavier and less controllable.
**Change trigger:** If someone contributes real character art (multi-frame personality sprites), swap the draw code for an ImageView + frame list.

## D7: BootReceiver is best-effort
**What:** RECEIVE_BOOT_COMPLETED restarts the service only on API ≤ 31.
**Why:** Android 12+ forbids startForegroundService from the background in most cases; pretending otherwise would produce silent crashes or worse, Play Store rejections. Honest limitation, documented in README.
**Change trigger:** If Chordy ever ships to Play, consider the exempted set (which still won't cover BOOT_COMPLETED) or drop the receiver entirely.

## D8: Single worker thread for lines + audio
**What:** One daemon ExecutorService handles LLM + TTS calls and MediaPlayer handoff.
**Why:** Events are sequential in practice (plug/unplug can't race meaningfully); a generation counter invalidates stale callbacks. Two pools would complicate the stale-guard for no throughput gain.
**Change trigger:** If per-event processing ever exceeds the debounce window (2s), split audio from text generation.

## D9: kotlin-in-LCR conventions
**What:** No hardcoded keys, timeouts on every call, no TODOs in core paths, debounced receiver, START_STICKY service.
**Why:** The brief's hard constraints. These are invariants, not decisions; treat violations as bugs.
