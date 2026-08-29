package com.kaiser.chordy.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.kaiser.chordy.service.PowerMonitorService

/**
 * Presence watcher: listens for TYPE_WINDOW_STATE_CHANGED and forwards the
 * foreground app's package to PowerMonitorService as an APP_OPENED reaction.
 *
 * The service does zero reaction logic of its own — it's a sensor. All gating
 * (toggle, cooldown, mood, lines) lives in PowerMonitorService's existing
 * pipeline, so this stays dumb, cheap, and replaceable.
 *
 * Skipping rules (per spec): never react to our own package, the default
 * launcher, or system UI — home-screen swiping shouldn't summon Chordy.
 */
class AppForegroundService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg.isBlank()) return

        if (shouldSkip(pkg)) return

        val intent = Intent(applicationContext, PowerMonitorService::class.java).apply {
            action = ACTION_APP_OPENED
            putExtra(EXTRA_PACKAGE, pkg)
        }
        // startForegroundService, not startService — PowerMonitorService is an
        // FGS and we may be called while the app is in the background.
        PowerMonitorService.start(applicationContext, intent)
    }

    private fun shouldSkip(pkg: String): Boolean {
        if (pkg == applicationContext.packageName) return true
        if (pkg == SYSTEM_UI) return true
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
        private const val SYSTEM_UI = "com.android.systemui"

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
