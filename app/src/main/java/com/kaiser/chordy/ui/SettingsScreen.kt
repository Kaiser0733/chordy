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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kaiser.chordy.R
import com.kaiser.chordy.data.LineBank
import com.kaiser.chordy.data.Personality
import com.kaiser.chordy.data.SettingsStore

/**
 * All knobs in one scroll: personality picker, LLM + TTS credentials (keys live
 * in EncryptedSharedPreferences — never source), AI-lines toggle, counter reset,
 * and a preview button to see each voice without unplugging anything.
 */
@Composable
fun SettingsScreen(
    store: SettingsStore,
    modifier: Modifier = Modifier
) {
    var personality by remember { mutableStateOf(store.selectedPersonality) }
    var llmKey by remember { mutableStateOf(store.llmApiKey) }
    var llmUrl by remember { mutableStateOf(store.llmBaseUrl) }
    var llmModel by remember { mutableStateOf(store.llmModel) }
    var ttsKey by remember { mutableStateOf(store.ttsApiKey) }
    var ttsUrl by remember { mutableStateOf(store.ttsBaseUrl) }
    var voiceIds by remember { mutableStateOf(store.allVoiceIds()) }
    var aiLines by remember { mutableStateOf(store.aiLinesEnabled) }
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
