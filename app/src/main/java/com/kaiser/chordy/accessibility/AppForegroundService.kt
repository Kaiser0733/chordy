package com.kaiser.chordy.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent
import com.kaiser.chordy.service.PowerMonitorService

/**
 * Presence watcher: listens for TYPE_WINDOW_STATE_CHANGED and forwards the
 * foreground app's package to PowerMonitorService as an APP_OPENED reaction.
 *
 * The service does zero reaction logic of its own — it's a sensor. All gating
 * (toggle, cooldown, mood, lines) lives in PowerMonitorService's pipeline.
 *
 * Edge-case rules (the window-change stream is noisy):
 *  - skip our own package, systemui, the default launcher
 *  - skip IMEs and system overlay packages (dialogs, installers, permission
 *    prompts fire window changes from their own packages — not "an app opened")
 *  - REACT ONLY ON CHANGE: last package persists in prefs, so a floating window
 *    opening over the current app, or a dialog stacking on top, does NOT
 *    re-register as a new app-open of itself. Going A -> dialog -> A is still A.
 */
class AppForegroundService : AccessibilityService() {

    private var prefs: SharedPreferences? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg.isBlank()) return
        if (shouldSkip(pkg)) return

        // React only when the foreground package CHANGED. This is what makes
        // floating windows, split-screen reshuffles, and dialog stacks behave:
        // same package on top = not a new app open.
        val last = prefs?.getString(KEY_LAST_PACKAGE, null)
        if (pkg == last) return
        prefs?.edit()?.putString(KEY_LAST_PACKAGE, pkg)?.apply()

        val intent = Intent(applicationContext, PowerMonitorService::class.java).apply {
            action = ACTION_APP_OPENED
            putExtra(EXTRA_PACKAGE, pkg)
            putExtra(EXTRA_SENT_AT, System.currentTimeMillis())
        }
        // startForegroundService, not startService — PowerMonitorService is an
        // FGS and we may be called while the app is in the background.
        PowerMonitorService.start(applicationContext, intent)
    }

    private fun shouldSkip(pkg: String): Boolean {
        if (pkg == applicationContext.packageName) return true
        // Explicit system-UI skip-list — NOT a blanket com.android.* prefix,
        // because real apps live there too (com.android.chrome is Chrome).
        if (pkg in SYSTEM_PACKAGES) return true
        // IMEs fire window changes constantly while typing — not app opens.
        if (pkg.endsWith(".ims") || pkg.contains(".ime.") || pkg.endsWith(".ime")) return true
        if (isLauncher(pkg)) return true
        return false
    }

    private fun isLauncher(pkg: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolved = packageManager.resolveActivity(intent, 0) ?: return false
        return resolved.activityInfo?.packageName == pkg
    }

    override fun onInterrupt() {
        // Nothing to interrupt — we produce no output for the user.
    }

    companion object {
        const val ACTION_APP_OPENED = "com.kaiser.chordy.action.APP_OPENED"
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_SENT_AT = "extra_sent_at"
        private const val SYSTEM_UI = "com.android.systemui"
        private const val PREFS = "chordy_fg_watcher"
        private const val KEY_LAST_PACKAGE = "last_fg_package"

        /**
         * Window-noise packages: system UI, system dialogs, the package
         * installer + permission controllers, settings shell. These fire
         * TYPE_WINDOW_STATE_CHANGED as windows stack, but none of them is
         * "the user opened an app."
         */
        private val SYSTEM_PACKAGES = setOf(
            SYSTEM_UI,
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.settings",
            "com.google.android.gms",          // GMS overlays, not user opens
            "com.android.systemui.shell"
        )

        /**
         * True when the user has enabled this service in system Accessibility
         * settings. Used by the settings screen to show live permission state.
         */
        fun isChordyAccessibilityEnabled(context: android.content.Context): Boolean {
            val expected = "${context.packageName}/${AppForegroundService::class.java.name}"
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
