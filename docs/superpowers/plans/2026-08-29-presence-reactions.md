# Presence Reactions Implementation Plan

> **For agentic workers:** Executed inline this session per the brief. Steps use checkbox (`- [ ]`) tracking.

**Goal:** Chordy reacts to device unlock (runtime receiver) and foreground app changes (AccessibilityService), with a 5-minute app-open cooldown, two settings toggles, and an accessibility permission status row.

**Architecture:** Strictly additive. `PowerEvent` enum gains `UNLOCK` + `APP_OPENED` values (no renames — existing `when` is statement-form so no breakage). `AppForegroundService` filters self/launcher/systemui, then forwards via `startForegroundService` intent to the existing `PowerMonitorService`, which gates on toggle + cooldown and feeds the ONE existing pipeline (`handleEvent` gains an optional `context` param for the LLM prompt — default arg, callers unchanged).

**Tech Stack:** No new dependencies. Runtime BroadcastReceiver (ACTION_USER_PRESENT can't be manifest-declared on 8+), AccessibilityService + config XML, Compose for the settings row.

## Global Constraints
- No renames of existing classes/fields (PowerEvent name kept)
- Power-event logic untouched; unlock receiver is a separate receiver instance (no shared debounce)
- UNLOCK: no cooldown. APP_OPENED: cooldown 5 min as named constant, gate lives in the firing code path
- Skip self package, resolved default launcher(s), com.android.systemui
- Toggles default ON; accessibility status row + deep link
- No hardcoded keys, no TODOs, K2-safe formatting (no line-leading binary operators)

## Tasks
1. data/MoodState.kt — extend PowerEvent with UNLOCK, APP_OPENED; update doc comment
2. data/SettingsStore.kt — reactToUnlock / reactToAppOpens (default true), lastAppReactionTimestamp
3. data/LineBank.kt — 24 new lines (3 personalities × 3 tiers × 2 events × 4), matched voices
4. accessibility/AppForegroundService.kt — TYPE_WINDOW_STATE_CHANGED, package filter, forward via intent
5. res/xml/accessibility_service_config.xml + strings + manifest service declaration
6. service/PowerMonitorService.kt — unlock receiver, APP_OPENED intent path with cooldown gate, optional context param, start() overload
7. ui/SettingsScreen.kt + MainActivity.kt — presence card, accessibility status + deep link via existing launcher pattern
8. README permission note, verification sweep, commit `feat: add unlock and app-open presence reactions`, push, CI green

## Interfaces
- `PowerEvent.UNLOCK`, `PowerEvent.APP_OPENED` — new enum values
- `SettingsStore.reactToUnlock: Boolean`, `.reactToAppOpens: Boolean`, `.lastAppReactionTimestamp: Long`
- `PowerMonitorService.APP_OPEN_COOLDOWN_MS` — named constant (5 min)
- `AppForegroundService.isChordyAccessibilityEnabled(context)` — companion helper for the status row
- `PowerMonitorService.start(context, intent)` — overload, existing `start(context)` untouched
- `handleEvent(event, context: String? = null)` — optional param, existing callsites unchanged
