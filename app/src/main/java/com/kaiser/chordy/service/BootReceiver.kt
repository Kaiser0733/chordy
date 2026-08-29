package com.kaiser.chordy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kaiser.chordy.data.SettingsStore
import org.koin.core.context.GlobalContext

/**
 * Restarts monitoring after reboot. RECEIVE_BOOT_COMPLETED receiver is declared
 * in the manifest but the service start is foreground-safe: Android 12+ blocks
 * startForegroundService from the background — we can't fully fix that without
 * more ceremony than the feature warrants, so BootReceiver only starts the
 * service on Android 11 and below; 12+ users relaunch the app once after reboot.
 *
 * The persisted pause switch is respected: "paused before reboot" must stay
 * "paused after reboot", not silently come back to life.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) return
        val store = GlobalContext.get().get(SettingsStore::class)
        if (!store.monitoringEnabled) return
        PowerMonitorService.start(context)
    }
}
