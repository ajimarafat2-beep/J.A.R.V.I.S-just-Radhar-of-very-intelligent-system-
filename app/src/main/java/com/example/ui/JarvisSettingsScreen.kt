package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.settings.AppLanguage
import com.example.settings.JarvisSettings
import com.example.ui.theme.JarvisAlertRed
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisNavy
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.voice.VoiceDiagnosticReport
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JarvisSettingsScreen(
    settings: JarvisSettings,
    micPermissionGranted: Boolean,
    voiceDiagnostic: VoiceDiagnosticReport?,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSaveWakeWord: (String) -> Unit,
    onSaveOpenAiApiKey: (String) -> Unit,
    onSaveAnthropicApiKey: (String) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onRequestMicPermission: () -> Unit,
    onToggleLiveVoiceChat: (Boolean) -> Unit,
    onToggleVoiceReply: (Boolean) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onSpeechPitchChange: (Float) -> Unit,
    onRunVoiceCheck: () -> Unit,
    onApplyCrystalClearPreset: () -> Unit,
    onToggleOfflineIsolation: (Boolean) -> Unit,
    onClearChat: () -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    var wakeWordDraft by remember { mutableStateOf(settings.wakeWord) }
    val context = LocalContext.current
    LaunchedEffect(settings.wakeWord) {
        wakeWordDraft = settings.wakeWord
    }
    var openAiApiKeyDraft by remember { mutableStateOf(settings.openAiApiKey) }
    var anthropicApiKeyDraft by remember { mutableStateOf(settings.anthropicApiKey) }
    LaunchedEffect(settings.openAiApiKey) {
            openAiApiKeyDraft = settings.openAiApiKey
    }
    LaunchedEffect(settings.anthropicApiKey) {
        anthropicApiKeyDraft = settings.anthropicApiKey
    }        
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisNavy),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JarvisHologramOrb(size = 24.dp, activeGold = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "JARVIS // SETTINGS",
                        style = MaterialTheme.typography.titleLarge,
                        color = JarvisCyan
                    )
                }
                Text(
                    text = "Language, Customizable Wake Word, Voice & Isolation Controls",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextMuted
                )
            }
        }

        // 1. Language Card (Default: English)
        item {
            SettingsCard(
                icon = Icons.Filled.Language,
                title = "Assistant Language",
                subtitle = "Auto-Detect is the default — Jarvis replies in the language you use. Switch anytime."
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        val selected = settings.language == lang
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectLanguage(lang) },
                            label = { Text(lang.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                                selectedLabelColor = JarvisCyan,
                                containerColor = JarvisSurfaceElevated,
                                labelColor = JarvisTextPrimary
                            ),
                            modifier = Modifier.testTag("settings_lang_${lang.name}")
                        )
                    }
                }
            }
        }

        // 2. Wake Word Card ("Hey Google"-style Multi-Alias Custom Names)
        item {
            SettingsCard(
                icon = Icons.Filled.Mic,
                title = "Hands-Free Wake Word (\"Hey Google\" Style)",
                subtitle = "Say \"Jarvis\", \"Hey Jarvis\", \"জারভিস\" or add custom comma-separated names/aliases so J.A.R.V.I.S responds to any of them."
            ) {
                if (!micPermissionGranted) {
                    OutlinedButton(
                        onClick = onRequestMicPermission,
                        border = BorderStroke(1.dp, JarvisGold),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_grant_mic_button")
                    ) {
                        Text("Grant Microphone Permission", color = JarvisGold, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                SettingsSwitchRow(
                    label = "Always-on Wake Word Listener",
                    description = if (settings.wakeWordEnabled) {
                        "Listening hands-free for: ${settings.wakeWord}"
                    } else {
                        "Wake word listener is paused"
                    },
                    checked = settings.wakeWordEnabled,
                    testTag = "settings_wake_switch",
                    onCheckedChange = { enabled ->
                        if (enabled && !micPermissionGranted) {
                            onRequestMicPermission()
                        } else {
                            onToggleWakeWord(enabled)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = wakeWordDraft,
                        onValueChange = { wakeWordDraft = it },
                        singleLine = true,
                        label = { Text("Custom Names / Aliases (e.g. Jarvis, Friday, জার্ভেস)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary,
                            focusedLabelColor = JarvisCyan,
                            unfocusedLabelColor = JarvisTextMuted
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_wake_word_input")
                    )
                    Button(
                        onClick = { onSaveWakeWord(wakeWordDraft) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisCyan,
                            contentColor = JarvisNavy
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("settings_save_wake_word_button")
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap aliases below to add or remove multiple trigger names:",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                val activeTokens = remember(wakeWordDraft) {
                    wakeWordDraft.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Jarvis", "জার্ভেস", "Charves", "Friday", "Stark", "Max").forEach { preset ->
                        val isActive = activeTokens.any { it.equals(preset, ignoreCase = true) }
                        FilterChip(
                            selected = isActive,
                            onClick = {
                                val nextList = activeTokens.toMutableList()
                                val existingIdx = nextList.indexOfFirst { it.equals(preset, ignoreCase = true) }
                                if (existingIdx >= 0) {
                                    if (nextList.size > 1) {
                                        nextList.removeAt(existingIdx)
                                    }
                                } else {
                                    nextList.add(preset)
                                }
                                val updated = nextList.joinToString(", ").ifBlank { "Jarvis" }
                                wakeWordDraft = updated
                                onSaveWakeWord(updated)
                            },
                            label = { Text(if (isActive) "✓ $preset" else "+ $preset") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JarvisGold.copy(alpha = 0.2f),
                                selectedLabelColor = JarvisGold,
                                containerColor = JarvisSurfaceElevated,
                                labelColor = JarvisTextPrimary
                            ),
                            modifier = Modifier.testTag("settings_wake_preset_$preset")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val pm = context.packageManager
                        val target = listOf(
                            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
                            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
                            Intent(Settings.ACTION_SETTINGS)
                        ).firstOrNull { it.resolveActivity(pm) != null }
                            ?: Intent(Settings.ACTION_SETTINGS)
                        runCatching {
                            context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                    border = BorderStroke(1.dp, JarvisCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_default_assistant_button")
                ) {
                    Text("Set J.A.R.V.I.S as Default Phone Assistant", color = JarvisCyan, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        // Multi-AI API Keys
        item {
            SettingsCard(
                icon = Icons.Filled.Shield,
                title = "Multi-AI keys",
                subtitle = "Your own API keys. They stay on this phone and are never shared.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = openAiApiKeyDraft,
                        onValueChange = { openAiApiKeyDraft = it},
                        singleLine = true,
                        label = { Text("OpenAI API key")},
                        placeholder = { Text("sk-...")},
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary,
                            focusedLabelColor = JarvisCyan,
                            unfocusedLabelColor = JarvisTextMuted
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_openai_key_input")
                    )
                    Button(
                        onClick = { onSaveOpenAiApiKey(openAiApiKeyDraft)},
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisCyan,
                            contentColor = JarvisNavy
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("settings_save_openai_key_button")
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = anthropicApiKeyDraft,
                        onValueChange = { anthropicApiKeyDraft = it},
                        singleLine = true,
                        label = { Text("Anthropic API key")},
                        placeholder = { Text("sk-ant-...")},
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary,
                            focusedLabelColor = JarvisCyan,
                            unfocusedLabelColor = JarvisTextMuted
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_anthropic_key_input")
                    )
                    Button(
                        onClick = { onSaveAnthropicApiKey(anthropicApiKeyDraft)},
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisCyan,
                            contentColor = JarvisNavy
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("settings_save_anthropic_key_button")
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Live Voice & Speech Tuning
        item {
            SettingsCard(
                icon = Icons.Filled.RecordVoiceOver,
                title = "Live Voice Chat & Speech",
                subtitle = "Configure conversational voice loop and TTS synthesis."
            ) {
                SettingsSwitchRow(
                    label = "Continuous Live Voice Chat",
                    description = "Automatically re-listen after Jarvis finishes speaking",
                    checked = settings.liveVoiceChatEnabled,
                    testTag = "settings_live_voice_switch",
                    onCheckedChange = onToggleLiveVoiceChat
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSwitchRow(
                    label = "Voice Reply (Text-to-Speech)",
                    description = "Read assistant replies aloud",
                    checked = settings.voiceReplyEnabled,
                    testTag = "settings_voice_reply_switch",
                    onCheckedChange = onToggleVoiceReply
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = String.format(Locale.US, "Speech Rate: %.2fx", settings.speechRate),
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisTextPrimary
                )
                Slider(
                    value = settings.speechRate,
                    onValueChange = onSpeechRateChange,
                    valueRange = 0.6f..1.6f,
                    colors = SliderDefaults.colors(
                        thumbColor = JarvisCyan,
                        activeTrackColor = JarvisCyan
                    ),
                    modifier = Modifier.testTag("settings_speech_rate_slider")
                )

                Text(
                    text = String.format(Locale.US, "Speech Pitch: %.2fx", settings.speechPitch),
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisTextPrimary
                )
                Slider(
                    value = settings.speechPitch,
                    onValueChange = onSpeechPitchChange,
                    valueRange = 0.7f..1.4f,
                    colors = SliderDefaults.colors(
                        thumbColor = JarvisGold,
                        activeTrackColor = JarvisGold
                    ),
                    modifier = Modifier.testTag("settings_speech_pitch_slider")
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onRunVoiceCheck,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("settings_voice_check_button")
                    ) {
                        Icon(Icons.Filled.GraphicEq, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check Voice", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onApplyCrystalClearPreset,
                        border = BorderStroke(1.dp, JarvisGold),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("settings_clear_voice_preset_button")
                    ) {
                        Text("Crystal Clear", color = JarvisGold, fontWeight = FontWeight.Bold)
                    }
                }

                voiceDiagnostic?.let { diag ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = JarvisSurfaceElevated,
                        border = BorderStroke(1.dp, JarvisCyan.copy(0.45f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = diag.summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextPrimary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // 4. Privacy & Offline Isolation
        item {
            SettingsCard(
                icon = Icons.Filled.Shield,
                title = "Privacy & Offline Isolation",
                subtitle = "Hold all outgoing website/search requests in the Task Queue until you turn isolation off."
            ) {
                SettingsSwitchRow(
                    label = "Offline Isolation Mode",
                    description = if (settings.offlineIsolationMode) {
                        "Online connections paused — tasks stay queued"
                    } else {
                        "Normal hybrid mode — online tasks run when connected"
                    },
                    checked = settings.offlineIsolationMode,
                    testTag = "settings_offline_isolation_switch",
                    onCheckedChange = onToggleOfflineIsolation
                )
            }
        }

        // 5. Data & Reset Actions
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onClearChat,
                    border = BorderStroke(1.dp, JarvisBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("settings_clear_chat_button")
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = JarvisTextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Chat", color = JarvisTextPrimary)
                }

                OutlinedButton(
                    onClick = onResetDefaults,
                    border = BorderStroke(1.dp, JarvisAlertRed.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("settings_reset_defaults_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = JarvisAlertRed)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Defaults", color = JarvisAlertRed)
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, JarvisBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = JarvisCyan)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = JarvisTextPrimary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = JarvisTextMuted)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = JarvisTextPrimary)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = JarvisTextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = JarvisNavy,
                checkedTrackColor = JarvisCyan,
                uncheckedThumbColor = JarvisTextMuted,
                uncheckedTrackColor = JarvisSurfaceElevated
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
