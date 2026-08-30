package com.kaiser.chordy

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kaiser.chordy.R
import com.kaiser.chordy.data.PersonaStore
import com.kaiser.chordy.data.SettingsStore
import com.kaiser.chordy.audio.AudioPlayer
import com.kaiser.chordy.network.LlmClient
import com.kaiser.chordy.network.TtsClient
import com.kaiser.chordy.service.PowerMonitorService
import com.kaiser.chordy.ui.ChordyTheme
import com.kaiser.chordy.ui.SettingsScreen
import org.koin.android.ext.android.inject

/**
 * First-launch flow: SYSTEM_ALERT_WINDOW -> POST_NOTIFICATIONS (13+) ->
 * battery-optimization exemption -> start PowerMonitorService. Later launches
 * go straight to Settings; the service keeps itself alive from then on.
 */
class MainActivity : ComponentActivity() {

    private val store: SettingsStore by inject()
    private val personas: PersonaStore by inject()
    private val llm: LlmClient by inject()
    private val tts: TtsClient by inject()
    private val audio: AudioPlayer by inject()

    // Bumped whenever a permission hop returns so the scaffold recomposes and
    // re-reads live system state instead of trusting stale remembered values.
    private var refreshTick by mutableIntStateOf(0)

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshTick++ }

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshTick++ }

    private val batteryPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshTick++ }

    private var uiStage by mutableStateOf(UiStage.SETTINGS)

    private enum class UiStage { PERMISSIONS, SETTINGS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Re-run the gauntlet if any permission was revoked while away —
        // cheap check, catches real cases (user revoked overlay in settings).
        if (needsPermissions()) uiStage = UiStage.PERMISSIONS

        // Common-sense revival: if the user never paused Chordy but the system
        // killed the service (swipe-away, OEM cleanup), opening the app brings
        // him back. Users who paused stay paused — the wake button is theirs.
        if (!needsPermissions() && store.firstRunDone &&
            store.monitoringEnabled && !PowerMonitorService.isRunning
        ) {
            PowerMonitorService.start(this)
        }

        setContent {
            ChordyTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when (uiStage) {
                        UiStage.PERMISSIONS -> PermissionScaffold(
                            onAllGranted = {
                                store.firstRunDone = true
                                uiStage = UiStage.SETTINGS
                                PowerMonitorService.start(this)
                            }
                        )
                        UiStage.SETTINGS -> SettingsScreen(
                            store = store,
                            personas = personas,
                            llm = llm,
                            tts = tts,
                            audio = audio
                        )
                    }
                }
            }
        }
    }

    private fun needsPermissions(): Boolean =
        !Settings.canDrawOverlays(this) ||
                (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED)

    /**
     * The gauntlet: overlay -> notifications -> battery exemption -> go.
     * Each "has*" recomputes on refreshTick so returning from system
     * settings is reflected immediately. Battery exemption is strongly
     * recommended but has a skip — no hard block on an optional grant.
     */
    @Composable
    private fun PermissionScaffold(onAllGranted: () -> Unit) {
        val hasOverlay = remember(refreshTick) { Settings.canDrawOverlays(this) }
        val hasNotif = remember(refreshTick) {
            // NB: binary operators must not start a line inside a lambda body —
            // K2 parses the previous line as a complete statement and dies on "== ...".
            Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        val hasBattery = remember(refreshTick) { isIgnoringBatteryOptimizations() }
        var batterySkipped by remember { mutableStateOf(false) }

        LaunchedEffect(hasOverlay, hasNotif, hasBattery, batterySkipped) {
            // Overlay + notif are hard requirements; battery is recommended but
            // skippable. Keys include all four states so a later battery grant
            // (or skip) restarts this effect and advances — the old version keyed
            // only on (hasOverlay, hasNotif) and never fired after the battery hop.
            if (hasOverlay && hasNotif && (hasBattery || batterySkipped)) onAllGranted()
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                !hasOverlay -> {
                    Text(
                        stringResource(R.string.overlay_permission_prompt),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = {
                        overlayPermissionLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }) { Text(stringResource(R.string.btn_grant)) }
                }
                !hasNotif -> {
                    Text(
                        stringResource(R.string.notif_permission_prompt),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) { Text(stringResource(R.string.btn_grant)) }
                }
                !hasBattery && !batterySkipped -> {
                    Text(
                        stringResource(R.string.battery_permission_prompt),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = {
                        batteryPermissionLauncher.launch(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }) { Text(stringResource(R.string.btn_grant)) }
                    Button(onClick = { batterySkipped = true }) { Text(stringResource(R.string.btn_skip)) }
                }
                else -> {
                    Text(
                        stringResource(R.string.all_set),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LaunchedEffect(Unit) { onAllGranted() }
                }
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }
}
