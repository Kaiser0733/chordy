# Chordy

A floating overlay creature that lives on top of your other apps and reacts emotionally every time you plug in or unplug your charger. Repeatedly yank the cable and Chordy's mood escalates from calm to anxious to genuinely upset. Each reaction is a speech-bubble line plus — optionally — a spoken voice line via cloud TTS.

## The three personalities

- **Clingy** — panics softly when unplugged, relieved and slightly guilt-trippy when reconnected. lowercase, fragment-heavy, clinging.
- **Toxic Ex** — cold, detached, performatively unbothered… until it isn't. Weaponized punctuation, keeps score.
- **Dramatic Actor** — every power event is a stage monologue. Behold! The current flows once more, etc.

Each personality has a canned line bank (~4 lines per mood tier per event) that works fully offline, and — if you enable AI lines — an LLM persona prompt that generates fresh in-character reactions via any OpenAI-compatible chat endpoint. On any LLM failure (timeout, bad key, no network) the app silently falls back to the canned bank. Nothing ever crashes or goes quiet over a dead API.

## Mood escalation

Reconnect counter persists across restarts: 0–1 reconnects = CALM, 2–3 = ANXIOUS, 4+ = ANGRY. A reset button lives in Settings.

## Opening the project

Standard Android project layout — open the root folder in Android Studio (or build via `./gradlew assembleDebug`). minSdk 26, targetSdk 35, Kotlin + Jetpack Compose for Settings, plain Views for the overlay.

Note: this repo currently has **no CI/build step** — that's phase 2. Building is by Android Studio or Gradle CLI locally.

## Where do API keys go?

In the app's **Settings screen, never in source code.** Keys are stored in `EncryptedSharedPreferences` (device keystore-backed) and never leave the device except in the request to your chosen provider. There are no hardcoded keys anywhere in this repo — check `data/SettingsStore.kt` if you're paranoid, which you should be.

Three configurable blocks in Settings:

1. **LLM** (optional): API key, base URL (any OpenAI-compatible endpoint), model name. Toggle "AI-generated lines" on to use it.
2. **TTS** (optional): API key, base URL (Fish Audio-style `POST {base}/v1/tts`), and one voice ID per personality — the voice-style tags (soft/fast/anxious, sassy/cold female, theatrical/baritone) tell you what kind of voice ID to paste. Empty voice ID = that personality stays silent, text-only.
3. **Everything else is free**: canned lines work offline at zero cost.

## Permissions, briefly

- `SYSTEM_ALERT_WINDOW` — Chordy floats over other apps. Requested on first launch.
- `POST_NOTIFICATIONS` — Android 13+; one low-priority persistent notification, required for foreground services.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — asked once; OEM battery managers kill unexempted services (Samsung especially).
- `RECEIVE_BOOT_COMPLETED` — restarts monitoring after reboot (best-effort; Android 12+ limits service starts from background receivers).
- Accessibility (no manifest permission) — the "React to app opens" feature uses an AccessibilityService that watches for window-change events so Chordy can notice when you open apps. It reads no screen content and keeps nothing; enable it in Settings > Accessibility via the button in Chordy's settings, and disable it any time to turn app reactions off entirely.

## Repo layout

```
app/src/main/java/com/kaiser/chordy/
├── service/    PowerMonitorService (FGS + power receiver + debounce), BootReceiver
├── overlay/    OverlayManager (WindowManager), BubbleView (Canvas face)
├── data/       Personality, LineBank, MoodState/MoodTier, SettingsStore
├── network/    LlmClient (OpenAI-compatible chat), TtsClient (speech bytes)
├── audio/      AudioPlayer (MediaPlayer, cancel/replace)
└── ui/         SettingsScreen (Compose), Theme
```

Architecture decisions and rationale live in `DECISIONS.md`.
