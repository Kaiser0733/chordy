package com.kaiser.chordy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kaiser.chordy.R
import com.kaiser.chordy.accessibility.AppForegroundService
import com.kaiser.chordy.audio.AudioPlayer
import com.kaiser.chordy.data.LineBank
import com.kaiser.chordy.data.MoodTier
import com.kaiser.chordy.data.PowerEvent
import com.kaiser.chordy.data.SettingsStore
import com.kaiser.chordy.network.LlmClient
import com.kaiser.chordy.network.TtsClient
import com.kaiser.chordy.overlay.OverlayManager
import org.koin.android.ext.android.inject
import java.util.concurrent.Executors

/**
 * The heartbeat. Foreground service that:
 *  1. registers the power connect/disconnect receiver
 *  2. debounces cable-jiggle noise (< 2s apart = ignored)
 *  3. updates persisted mood state + reconnect counter
 *  4. fires the overlay line: canned first (instant), LLM swap-in when it lands
 *  5. kicks off TTS (silent on failure — text bubble carries the moment)
 */
class PowerMonitorService : Service() {

    private val settings: SettingsStore by inject()
    private val overlay: OverlayManager by inject()
    private val llm: LlmClient by inject()
    private val tts: TtsClient by inject()
    private val audio: AudioPlayer by inject()

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "chordy-line-worker").apply { isDaemon = true }
    }

    private var lastEventAt = 0L
    private val debounceMs = 2_000L

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = System.currentTimeMillis()
            if (now - lastEventAt < debounceMs) return   // jiggle noise
            lastEventAt = now
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> handleEvent(PowerEvent.CONNECTED)
                Intent.ACTION_POWER_DISCONNECTED -> handleEvent(PowerEvent.DISCONNECTED)
            }
        }
    }

    /**
     * UNLOCK has no debounce — power events share one to kill cable-jiggle noise,
     * but unlock is a deliberate human act, not hardware chatter. Every unlock
     * gets its own reaction (per spec: no cooldown on unlock).
     */
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_USER_PRESENT) return
            if (!settings.reactToUnlock) return
            handleEvent(PowerEvent.UNLOCK)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        isRunning = true
        registerReceiver(
            powerReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
        )
        // ACTION_USER_PRESENT is delivery-to-registered-receivers-only on
        // Android 8+, so this one lives purely at runtime (per spec).
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        // restore the face for this boot session
        overlay.show(MoodTier.fromReconnectCount(settings.reconnectCount))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // AppForegroundService forwards APP_OPENED through startForegroundService
        // with this action + package extra; power/unlock paths never carry it.
        if (intent?.action == AppForegroundService.ACTION_APP_OPENED) {
            val pkg = intent.getStringExtra(AppForegroundService.EXTRA_PACKAGE)
            onAppOpened(pkg)
        }
        // Battery optimization exemption is requested by MainActivity on first run;
        // this service just lives forever once started (START_STICKY on OEM kills).
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        unregisterReceiver(powerReceiver)
        unregisterReceiver(unlockReceiver)
        overlay.hide()
        audio.stop()
        worker.shutdownNow()
        super.onDestroy()
    }

    // ---------- event pipeline ----------

    /**
     * APP_OPENED path, cooldown lives HERE — the gate must sit between the
     * sensor event and handleEvent, or it's decoration. 5-minute window,
     * named constant so tuning it later is a one-line change.
     */
    private fun onAppOpened(packageName: String?) {
        if (!settings.reactToAppOpens) return
        val now = System.currentTimeMillis()
        if (now - settings.lastAppReactionTimestamp < APP_OPEN_COOLDOWN_MS) return
        settings.lastAppReactionTimestamp = now
        handleEvent(PowerEvent.APP_OPENED, packageName)
    }

    private fun handleEvent(event: PowerEvent, context: String? = null) {
        val personality = settings.selectedPersonality
        val now = System.currentTimeMillis()

        // state update first — mood tier drives everything downstream.
        // UNLOCK / APP_OPENED are pure reaction events: no counter bumps, no
        // timestamps — they ride the existing mood, they don't escalate it.
        when (event) {
            PowerEvent.CONNECTED -> {
                if (settings.lastDisconnectTs > 0L) {
                    settings.reconnectCount = settings.reconnectCount + 1
                }
                settings.lastConnectTs = now
            }
            PowerEvent.DISCONNECTED -> {
                settings.lastDisconnectTs = now
            }
            PowerEvent.UNLOCK -> { /* reaction-only */ }
            PowerEvent.APP_OPENED -> { /* reaction-only */ }
        }
        val tier = MoodTier.fromReconnectCount(settings.reconnectCount)

        // 1. Instant canned line — the bubble never waits on the network.
        val canned = LineBank.line(personality, tier, event)
        overlay.showLine(canned, tier, audioPending = settings.aiLinesEnabled)

        // 2. LLM upgrade-in-place, only if enabled
        if (settings.aiLinesEnabled) {
            val genAt = lineGeneration.incrementAndGet()
            worker.execute {
                val aiLine = llm.generateLine(
                    baseUrl = settings.llmBaseUrl,
                    apiKey = settings.llmApiKey,
                    model = settings.llmModel,
                    personaPrompt = personality.personaPrompt,
                    moodTierName = tier.name,
                    event = if (context != null) "${event.name} of $context" else event.name,
                    reconnectCount = settings.reconnectCount
                )
                // swap in only if a newer event hasn't superseded us
                if (aiLine != null && genAt == lineGeneration.get()) {
                    overlay.showLine(aiLine, tier, audioPending = true)
                    speakLine(aiLine, personality)
                } else if (aiLine == null && genAt == lineGeneration.get()) {
                    // LLM failed — keep canned, maybe speak it
                    speakLine(canned, personality)
                }
            }
        } else {
            speakLine(canned, personality)
        }
    }

    private fun speakLine(text: String, personality: com.kaiser.chordy.data.Personality) {
        val voiceId = settings.voiceIdFor(personality)
        if (voiceId.isBlank()) {
            overlay.onAudioReady()   // no voice configured — clear the dots
            return
        }
        worker.execute {
            val bytes = tts.synthesize(
                baseUrl = settings.ttsBaseUrl,
                apiKey = settings.ttsApiKey,
                voiceId = voiceId,
                text = text
            )
            if (bytes != null) {
                audio.play(bytes)
                overlay.onAudioReady()
            } else {
                overlay.onAudioReady()   // silent skip per contract — text still shown
            }
        }
    }

    // ---------- notification ----------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW   // required persistent FGS notif, low-key
            ).apply {
                description = getString(R.string.notif_channel_desc)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "chordy_monitor"
        private const val NOTIF_ID = 42

        /** APP_OPENED cooldown — named, not magic. 5 minutes by default. */
        const val APP_OPEN_COOLDOWN_MS: Long = 5 * 60 * 1000L

        /**
         * Live service state for the settings screen: true from onCreate to
         * onDestroy. Lets the status row distinguish "paused by user" from
         * "killed by the system" — both show the wake button.
         */
        @Volatile var isRunning: Boolean = false

        private val lineGeneration = java.util.concurrent.atomic.AtomicInteger(0)

        fun start(context: Context) {
            val intent = Intent(context, PowerMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Overload used by AppForegroundService — carries the APP_OPENED action + package. */
        fun start(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PowerMonitorService::class.java))
        }
    }
}
