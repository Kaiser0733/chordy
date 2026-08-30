package com.kaiser.chordy.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.withContext
import com.kaiser.chordy.R
import com.kaiser.chordy.accessibility.AppForegroundService
import com.kaiser.chordy.audio.AudioPlayer
import com.kaiser.chordy.BuildConfig
import com.kaiser.chordy.data.Persona
import com.kaiser.chordy.data.PersonaStore
import com.kaiser.chordy.data.SettingsStore
import com.kaiser.chordy.network.LlmClient
import com.kaiser.chordy.network.TtsClient
import com.kaiser.chordy.service.PowerMonitorService
import com.kaiser.chordy.update.UpdateChecker

/**
 * Home, not a control panel: one glance = "is he on?" One tap = pause/wake.
 * Persona list front and center (tap to pick, pencil to edit, + to create).
 * Everything infrequently touched lives behind one "advanced" expander.
 */
@Composable
fun SettingsScreen(
    store: SettingsStore,
    personas: PersonaStore,
    llm: LlmClient,
    tts: TtsClient,
    audio: AudioPlayer,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var refreshTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var personaList by remember { mutableStateOf(personas.all()) }
    var selectedId by remember { mutableStateOf(store.selectedPersonaId) }
    var monitoringOn by remember(refreshTick) {
        mutableStateOf(PowerMonitorService.isRunning && store.monitoringEnabled)
    }
    var editingPersona by remember { mutableStateOf<Persona?>(null) }
    var creatingNew by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var previewLine by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }

    // Auto-check for updates on open (quietly — never blocks the UI).
    val updateChecker: UpdateChecker = org.koin.core.context.GlobalContext.get().get()
    LaunchedEffect(Unit) {
        updateInfo = withContext(Dispatchers.IO) { updateChecker.checkForUpdate() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ============ home: status ============
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(if (monitoringOn) R.string.status_running else R.string.status_stopped),
            style = MaterialTheme.typography.bodyLarge,
            color = if (monitoringOn) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = {
                if (monitoringOn) {
                    store.monitoringEnabled = false
                    PowerMonitorService.stop(ctx)
                } else {
                    store.monitoringEnabled = true
                    PowerMonitorService.start(ctx)
                }
                monitoringOn = !monitoringOn
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(stringResource(if (monitoringOn) R.string.btn_pause else R.string.btn_resume))
        }

        // ============ update banner (one tap — no uninstall/reinstall) ============
        if (updateInfo != null && !downloading) {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.update_available, updateInfo!!.versionName),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        stringResource(R.string.update_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            downloading = true
                            downloadProgress = 0
                            scope.launch(Dispatchers.IO) {
                                val ok = updateChecker.downloadAndInstall { p ->
                                    scope.launch { downloadProgress = p }
                                }
                                if (!ok) {
                                    withContext(Dispatchers.Main) {
                                        downloading = false
                                        updateInfo = null   // failed — hide banner, try next open
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(stringResource(R.string.btn_update_now))
                    }
                }
            }
        }
        if (downloading) {
            Text(
                stringResource(R.string.update_downloading, downloadProgress),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // ============ personas ============
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.personality_label), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { creatingNew = true }) {
                        Text(stringResource(R.string.btn_new_persona))
                    }
                }
                personaList.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedId = p.id
                                store.selectedPersonaId = p.id
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedId == p.id,
                            onClick = {
                                selectedId = p.id
                                store.selectedPersonaId = p.id
                            }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                p.voiceStyleTag,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { editingPersona = p }) {
                            Text(stringResource(R.string.btn_edit))
                        }
                    }
                }
                var testing by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = {
                        val persona = personaList.firstOrNull { it.id == selectedId } ?: return@OutlinedButton
                        testing = true
                        previewLine = null
                        scope.launch(Dispatchers.IO) {
                            val result = llm.generateLine(
                                baseUrl = store.llmBaseUrl.ifBlank { BuildConfig.NIM_BASE_URL },
                                apiKey = store.llmApiKey.ifBlank { BuildConfig.NIM_API_KEY },
                                model = store.llmModel.ifBlank { BuildConfig.NIM_MODEL },
                                personaPrompt = persona.systemPrompt,
                                moodTierName = "CALM",
                                event = "TEST",
                                reconnectCount = 0
                            )
                            withContext(Dispatchers.Main) {
                                previewLine = when (result) {
                                    is LlmClient.Result.Ok -> result.line
                                    is LlmClient.Result.Fail -> ctx.getString(
                                        R.string.llm_test_fail_prefix, result.reason
                                    )
                                }
                                testing = false
                            }
                        }
                    },
                    enabled = !testing
                ) {
                    Text(stringResource(if (testing) R.string.llm_test_pending else R.string.test_line))
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

        // ============ advanced expander ============
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(
                stringResource(if (showAdvanced) R.string.btn_hide_advanced else R.string.btn_show_advanced),
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (showAdvanced) {
            AdvancedCards(
                store = store,
                personas = personas,
                personaList = personaList,
                selectedId = selectedId,
                onPersonasChanged = { personaList = personas.all() },
                llm = llm,
                tts = tts,
                audio = audio
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    // ---------- editor dialogs (rendered outside the scroll) ----------
    editingPersona?.let { p ->
        PersonaEditorDialog(
            persona = p,
            isNew = false,
            onDismiss = { editingPersona = null },
            onSave = { name, tag, prompt ->
                personas.upsert(p.copy(name = name, voiceStyleTag = tag, systemPrompt = prompt))
                personaList = personas.all()
                editingPersona = null
            }
        )
    }
    if (creatingNew) {
        PersonaEditorDialog(
            persona = null,
            isNew = true,
            onDismiss = { creatingNew = false },
            onSave = { name, tag, prompt ->
                personas.create(name, tag, prompt)
                personaList = personas.all()
                creatingNew = false
            }
        )
    }
}

/**
 * The dense stuff, hidden behind one tap: presence toggles, accessibility
 * status, AI settings with live tests, voice config, counter.
 */
@Composable
private fun AdvancedCards(
    store: SettingsStore,
    personas: PersonaStore,
    personaList: List<Persona>,
    selectedId: String,
    onPersonasChanged: () -> Unit,
    llm: LlmClient,
    tts: TtsClient,
    audio: AudioPlayer
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var reactUnlock by remember { mutableStateOf(store.reactToUnlock) }
    var reactApps by remember { mutableStateOf(store.reactToAppOpens) }
    var aiLines by remember { mutableStateOf(store.aiLinesEnabled) }
    var reconnects by remember { mutableStateOf(store.reconnectCount) }
    var llmKey by remember { mutableStateOf(store.llmApiKey) }
    var llmUrl by remember { mutableStateOf(store.llmBaseUrl) }
    var llmModel by remember { mutableStateOf(store.llmModel) }
    var ttsKey by remember { mutableStateOf(store.ttsApiKey) }
    var ttsUrl by remember { mutableStateOf(store.ttsBaseUrl) }
    var voiceIds by remember { mutableStateOf(store.allVoiceIds(personaList.map { it.id })) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // ---------- presence ----------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.presence_section), style = MaterialTheme.typography.titleMedium)
                ToggleRow(stringResource(R.string.react_unlock_toggle), reactUnlock) {
                    reactUnlock = it; store.reactToUnlock = it
                }
                ToggleRow(stringResource(R.string.react_app_opens_toggle), reactApps) {
                    reactApps = it; store.reactToAppOpens = it
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                val a11yGranted = AppForegroundService.isChordyAccessibilityEnabled(ctx)
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
                        ctx.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) {
                        Text(stringResource(R.string.accessibility_open_settings))
                    }
                }
            }
        }

        // ---------- AI ----------
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.llm_section), style = MaterialTheme.typography.titleMedium)
                ToggleRow(stringResource(R.string.ai_lines_toggle), aiLines) {
                    aiLines = it; store.aiLinesEnabled = it
                }
                Text(
                    stringResource(R.string.llm_bundled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                var llmTestState by remember { mutableStateOf<String?>(null) }
                var llmTestOk by remember { mutableStateOf(false) }
                OutlinedButton(onClick = {
                    val persona = personaList.firstOrNull { it.id == selectedId }
                    llmTestState = ctx.getString(R.string.llm_test_pending)
                    llmTestOk = false
                    scope.launch(Dispatchers.IO) {
                        val result = llm.generateLine(
                            baseUrl = llmUrl.ifBlank { BuildConfig.NIM_BASE_URL },
                            apiKey = llmKey.ifBlank { BuildConfig.NIM_API_KEY },
                            model = llmModel.ifBlank { BuildConfig.NIM_MODEL },
                            personaPrompt = persona?.systemPrompt ?: "",
                            moodTierName = "CALM",
                            event = "TEST",
                            reconnectCount = 0
                        )
                        withContext(Dispatchers.Main) {
                            when (result) {
                                is LlmClient.Result.Ok -> {
                                    llmTestState = result.line
                                    llmTestOk = true
                                }
                                is LlmClient.Result.Fail -> {
                                    llmTestState = ctx.getString(
                                        R.string.llm_test_fail_prefix, result.reason
                                    )
                                    llmTestOk = false
                                }
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.btn_test_llm))
                }
                llmTestState?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (llmTestOk) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Text(stringResource(R.string.llm_override_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            }
        }

        // ---------- voice ----------
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
                    val voiceId = voiceIds[selectedId] ?: ""
                    val persona = personaList.firstOrNull { it.id == selectedId }
                    if (voiceId.isBlank() || persona == null) {
                        ttsTestState = ctx.getString(R.string.test_no_voice)
                    } else {
                        ttsTestState = ctx.getString(R.string.tts_test_pending)
                        scope.launch(Dispatchers.IO) {
                            val bytes = tts.synthesize(
                                baseUrl = ttsUrl,
                                apiKey = ttsKey,
                                voiceId = voiceId,
                                // speak the persona's own name + vibe — no canned bank anymore
                                text = "Hello. I am ${persona.name}. ${persona.voiceStyleTag}."
                            )
                            withContext(Dispatchers.Main) {
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
                ttsTestState?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = if (it == ctx.getString(R.string.tts_test_ok))
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                personaList.forEach { p ->
                    var vid by remember(p.id) { mutableStateOf(voiceIds[p.id] ?: "") }
                    OutlinedTextField(
                        value = vid,
                        onValueChange = {
                            vid = it
                            store.setVoiceId(p.id, it)
                            voiceIds = store.allVoiceIds(personaList.map { it.id })
                        },
                        label = { Text("${p.name} — ${p.voiceStyleTag}") },
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
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
