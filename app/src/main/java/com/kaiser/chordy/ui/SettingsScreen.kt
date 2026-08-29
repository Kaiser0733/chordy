package com.kaiser.chordy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.kaiser.chordy.R
import com.kaiser.chordy.accessibility.AppForegroundService
import com.kaiser.chordy.audio.AudioPlayer
import com.kaiser.chordy.data.LineBank
import com.kaiser.chordy.data.Personality
import com.kaiser.chordy.data.SettingsStore
import com.kaiser.chordy.network.LlmClient
import com.kaiser.chordy.network.TtsClient
import com.kaiser.chordy.service.PowerMonitorService

/**
 * All knobs in one scroll: status/pause, personality picker, presence toggles,
 * LLM + TTS credentials with live test buttons (keys live in
 * EncryptedSharedPreferences — never source), AI-lines toggle, counter reset,
 * and a preview button to see each voice without unplugging anything.
 */
@Composable
fun SettingsScreen(
    store: SettingsStore,
    llm: LlmClient,
    tts: TtsClient,
    audio: AudioPlayer,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var personality by remember { mutableStateOf(store.selectedPersonality) }
    var llmKey by remember { mutableStateOf(store.llmApiKey) }
    var llmUrl by remember { mutableStateOf(store.llmBaseUrl) }
    var llmModel by remember { mutableStateOf(store.llmModel) }
    var ttsKey by remember { mutableStateOf(store.ttsApiKey) }
    var ttsUrl by remember { mutableStateOf(store.ttsBaseUrl) }
    var voiceIds by remember { mutableStateOf(store.allVoiceIds()) }
    var aiLines by remember { mutableStateOf(store.aiLinesEnabled) }
    var reactUnlock by remember { mutableStateOf(store.reactToUnlock) }
    var reactApps by remember { mutableStateOf(store.reactToAppOpens) }
    var reconnects by remember { mutableStateOf(store.reconnectCount) }
    var previewLine by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

        // ---------- status / pause ----------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.status_title), style = MaterialTheme.typography.titleMedium)
                // Re-check service state on every resume — covers both the user
                // pausing here and the system killing the service while away.
                var statusTick by remember { mutableStateOf(0) }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) statusTick++
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                val running = remember(statusTick) {
                    PowerMonitorService.isRunning && store.monitoringEnabled
                }
                Text(
                    stringResource(if (running) R.string.status_running else R.string.status_stopped),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (running) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        if (running) {
                            store.monitoringEnabled = false
                            PowerMonitorService.stop(ctx)
                        } else {
                            store.monitoringEnabled = true
                            PowerMonitorService.start(ctx)
                        }
                        statusTick++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(if (running) R.string.btn_pause else R.string.btn_resume))
                }
            }
        }

        // ---------- personality ----------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.personality_label), style = MaterialTheme.typography.titleMedium)
                Personality.entries.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = personality == p,
                            onClick = {
                                personality = p
                                store.selectedPersonality = p
                            }
                        )
                        Column {
                            Text(p.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                p.voiceStyleTag,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                OutlinedButton(onClick = { previewLine = LineBank.previewLine(personality) }) {
                    Text(stringResource(R.string.test_line))
                }
                previewLine?.let {
                    Text(
                        "\"$it\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ---------- presence ----------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.presence_section), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = reactUnlock,
                        onCheckedChange = {
                            reactUnlock = it
                            store.reactToUnlock = it
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.react_unlock_toggle), style = MaterialTheme.typography.bodyLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = reactApps,
                        onCheckedChange = {
                            reactApps = it
                            store.reactToAppOpens = it
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.react_app_opens_toggle), style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                val lifecycleOwner = LocalLifecycleOwner.current
                var a11yTick by remember { mutableStateOf(0) }
                val a11yGranted = remember(a11yTick) {
                    AppForegroundService.isChordyAccessibilityEnabled(ctx)
                }
                // Re-check the permission every time the activity resumes — that's
                // when the user comes back from the Accessibility settings screen.
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) a11yTick++
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                Text(
                    stringResource(
                        if (a11yGranted) R.string.accessibility_status_on
                        else R.string.accessibility_status_off
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (a11yGranted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!a11yGranted) {
                    OutlinedButton(onClick = {
                        ctx.startActivity(
                            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        )
                    }) {
                        Text(stringResource(R.string.accessibility_open_settings))
                    }
                }
            }
        }

        // ---------- AI lines ----------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.llm_section), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = aiLines,
                        onCheckedChange = {
                            aiLines = it
                            store.aiLinesEnabled = it
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (aiLines) R.string.ai_lines_on_hint else R.string.ai_lines_off_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (aiLines) {
                    OutlinedTextField(
                        value = llmKey,
                        onValueChange = { llmKey = it; store.llmApiKey = it },
                        label = { Text(stringResource(R.string.llm_key_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = llmUrl,
                        onValueChange = { llmUrl = it; store.llmBaseUrl = it },
                        label = { Text(stringResource(R.string.llm_base_url_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = llmModel,
                        onValueChange = { llmModel = it; store.llmModel = it },
                        label = { Text(stringResource(R.string.llm_model_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.llm_saved_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    var llmTestState by remember { mutableStateOf<String?>(null) }
                    OutlinedButton(onClick = {
                        llmTestState = ctx.getString(R.string.llm_test_pending)
                        scope.launch(Dispatchers.IO) {
                            val reply = llm.generateLine(
                                baseUrl = llmUrl,
                                apiKey = llmKey,
                                model = llmModel,
                                personaPrompt = personality.personaPrompt,
                                moodTierName = "CALM",
                                event = "TEST",
                                reconnectCount = 0
                            )
                            llmTestState = reply
                                ?: ctx.getString(R.string.llm_test_fail)
                        }
                    }) {
                        Text(stringResource(R.string.btn_test_llm))
                    }
                    llmTestState?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (it == ctx.getString(R.string.llm_test_fail) ||
                                it == ctx.getString(R.string.llm_test_pending))
                            MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // ---------- voice / TTS ----------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tts_section), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = ttsKey,
                    onValueChange = { ttsKey = it; store.ttsApiKey = it },
                    label = { Text(stringResource(R.string.tts_key_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ttsUrl,
                    onValueChange = { ttsUrl = it; store.ttsBaseUrl = it },
                    label = { Text(stringResource(R.string.tts_base_url_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Text(stringResource(R.string.tts_voice_ids_label), style = MaterialTheme.typography.bodyMedium)
                var ttsTestState by remember { mutableStateOf<String?>(null) }
                OutlinedButton(onClick = {
                    val voiceId = voiceIds[personality] ?: ""
                    if (voiceId.isBlank()) {
                        ttsTestState = ctx.getString(R.string.test_no_voice)
                    } else {
                        ttsTestState = ctx.getString(R.string.tts_test_pending)
                        scope.launch(Dispatchers.IO) {
                            val bytes = tts.synthesize(
                                baseUrl = ttsUrl,
                                apiKey = ttsKey,
                                voiceId = voiceId,
                                text = LineBank.previewLine(personality)
                            )
                            // MediaPlayer wants a Looper thread — hop to main to play.
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                if (bytes != null) {
                                    audio.play(bytes)
                                    ttsTestState = ctx.getString(R.string.tts_test_ok)
                                } else {
                                    ttsTestState = ctx.getString(R.string.tts_test_fail)
                                }
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.btn_test_tts))
                }
                Text(
                    stringResource(R.string.tts_saved_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ttsTestState?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it == ctx.getString(R.string.tts_test_ok))
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Personality.entries.forEach { p ->
                    var vid by remember(p) { mutableStateOf(voiceIds[p] ?: "") }
                    OutlinedTextField(
                        value = vid,
                        onValueChange = {
                            vid = it
                            store.setVoiceId(p, it)
                            voiceIds = store.allVoiceIds()
                        },
                        label = { Text("${p.displayName} — ${p.voiceStyleTag}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ---------- counter ----------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.counter_value, reconnects),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.mood_tiers_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        store.resetCounter()
                        reconnects = 0
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.reset_counter))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
