package com.kaiser.chordy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Restarts monitoring after reboot. RECEIVE_BOOT_COMPLETED receiver is declared
 * in the manifest but the service start is foreground-safe: Android 12+ blocks
 * startForegroundService from the background — we can't fully fix that without
 * more ceremony than the feature warrants, so BootReceiver only starts the
 * service on Android 11 and below; 12+ users relaunch the app once after reboot.
 * (Battery-optimization-exempt apps get some slack, but the platform still
 * restricts FGS-on-boot; keeping the code simple and honest about it.)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            PowerMonitorService.start(context)
        }
    }
}
