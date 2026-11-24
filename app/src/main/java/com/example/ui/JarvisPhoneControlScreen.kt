package com.example.ui

import android.graphics.Bitmap
import android.media.AudioManager
import android.view.KeyEvent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.control.PhoneControlSnapshot
import com.example.control.PhoneRingerMode
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisNavy
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JarvisPhoneControlScreen(
    controlState: PhoneControlSnapshot,
    onRefreshStatus: () -> Unit,
    onRequestCameraAndMic: () -> Unit,
    onCapturedCameraFrame: (Bitmap) -> Unit,
    onToggleTorch: () -> Unit,
    onSetBrightness: (Int) -> Unit,
    onToggleAutoBrightness: (Boolean) -> Unit,
    onSetStreamVolume: (Int, Int) -> Unit,
    onSetRingerMode: (PhoneRingerMode) -> Unit,
    onDispatchMediaKey: (Int) -> Unit,
    onGlobalAction: (String) -> Unit,
    onOpenSystemPanel: (String) -> Unit,
    onOpenDialer: (String) -> Unit,
    onOpenCameraApp: (Boolean) -> Unit,
    onSetTimerMinutes: (Int) -> Unit,
    onStartVoiceListening: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var phoneNumberDraft by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisNavy),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JarvisHologramOrb(size = 24.dp, activeGold = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "JARVIS // FULL PHONE CONTROL",
                        style = MaterialTheme.typography.titleLarge,
                        color = JarvisCyan
                    )
                }
                Text(
                    text = "Camera, Microphone, Hardware Switches, Display, Audio & System-Wide Accessibility Control",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisTextMuted
                )
            }
        }

        // 1. Access & Permissions Hub (Camera, Mic, Accessibility, Write Settings, DND)
        item {
            PhoneSectionCard(
                icon = Icons.Filled.AccessibilityNew,
                title = "Full Access & Sensor Permissions",
                subtitle = "Grant Camera, Microphone, and System Accessibility access for complete hands-free phone control."
            ) {
                AccessStatusRow("Camera Access (Live Vision)", controlState.cameraGranted)
                AccessStatusRow("Microphone Access (Live Voice & Wake)", controlState.micGranted)
                AccessStatusRow("Full System Control (Accessibility)", controlState.accessibilityEnabled)
                AccessStatusRow("Brightness & Display Write Access", controlState.writeSettingsGranted)
                AccessStatusRow("DND / Silent Mode Policy Access", controlState.dndPolicyGranted)

                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!controlState.cameraGranted || !controlState.micGranted) {
                        Button(
                            onClick = onRequestCameraAndMic,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(46.dp).testTag("phone_grant_camera_mic_button")
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Grant Camera & Mic", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (!controlState.accessibilityEnabled) {
                        Button(
                            onClick = { onOpenSystemPanel("accessibility") },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisNavy),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(46.dp).testTag("phone_enable_accessibility_button")
                        ) {
                            Text("Enable System Control", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (!controlState.writeSettingsGranted) {
                        OutlinedButton(
                            onClick = { onOpenSystemPanel("write_settings") },
                            border = BorderStroke(1.dp, JarvisGold),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Text("Allow Brightness Control", color = JarvisGold)
                        }
                    }
                    if (!controlState.dndPolicyGranted) {
                        OutlinedButton(
                            onClick = { onOpenSystemPanel("dnd") },
                            border = BorderStroke(1.dp, JarvisBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Text("Allow Silent / DND", color = JarvisCyan)
                        }
                    }
                    OutlinedButton(
                        onClick = onRefreshStatus,
                        border = BorderStroke(1.dp, JarvisBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Text("Refresh Status", color = JarvisTextPrimary)
                    }
                }

                controlState.lastControlStatus?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(msg, style = MaterialTheme.typography.labelMedium, color = JarvisCyan)
                }
            }
        }

        // 2. Live CameraX Viewfinder inside Phone Control
        item {
            JarvisCameraCaptureCard(
                onCapturedBitmap = onCapturedCameraFrame,
                onPermissionUpdated = onRefreshStatus,
                captureButtonLabel = "Snap Photo & Send to JARVIS Vision",
                modifier = Modifier.widthIn(max = 760.dp)
            )
        }

        // 2b. JARVIS Offline Voice Access Engine (Google Voice Access Feature Architecture)
        item {
            PhoneSectionCard(
                icon = Icons.Filled.Mic,
                title = "Offline Voice Access Engine",
                subtitle = if (controlState.voiceAccessOverlayActive) {
                    "Numbered Tap Grid ACTIVE (${controlState.voiceAccessTargetCount} buttons). Say 'Tap 1..${controlState.voiceAccessTargetCount}' or 'ট্যাপ ১'."
                } else {
                    "Voice Access–style screen navigation, numbered tap grid, voice typing & text editing (English + বাংলা)."
                }
            ) {
                AccessStatusRow(
                    label = "Numbered Tap Grid Overlay",
                    granted = controlState.voiceAccessOverlayActive
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (controlState.voiceAccessOverlayActive) {
                                onGlobalAction("hide_numbers")
                            } else {
                                onGlobalAction("show_numbers")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (controlState.voiceAccessOverlayActive) JarvisGold else JarvisCyan,
                            contentColor = JarvisNavy
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(46.dp)
                            .testTag("phone_voice_access_numbers_button")
                    ) {
                        Text(
                            text = if (controlState.voiceAccessOverlayActive) "Hide Tap Numbers" else "Show Tap Numbers (1,2,3)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = onStartVoiceListening,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(46.dp).testTag("phone_voice_listen_button")
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Speak Command", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onOpenSystemPanel("speech_packs") },
                        border = BorderStroke(1.dp, JarvisBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Text("Offline Speech Packs", color = JarvisCyan)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Voice Access Offline Commands (English + বাংলা):",
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisGold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• Tap: \"Show numbers\" / \"নম্বর দেখাও\" -> \"Tap 3\" / \"ট্যাপ ৩\"\n" +
                        "• Navigate: \"Go Back\", \"Go Home\", \"Scroll Up/Down/Left/Right\"\n" +
                        "• Voice Typing: \"Type <text>\" / \"লিখো <টেক্সট>\", \"Append <text>\"\n" +
                        "• Text Edit: \"Clear text\" / \"সব মুছো\", \"Delete last word\" / \"শেষের শব্দ মুছো\", \"Select all\", \"Copy\", \"Paste\"\n" +
                        "• Apps: \"Open YouTube\" / \"ইউটিউব খোলো\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisTextMuted
                )
            }
        }

        // 3. System-Wide Phone Actions (Accessibility Global Actions)
        item {
            PhoneSectionCard(
                icon = Icons.Filled.Tune,
                title = "System Navigation, Screen & Gestures",
                subtitle = if (controlState.accessibilityEnabled) {
                    "System Accessibility Bridge CONNECTED — controls any screen across the entire phone."
                } else {
                    "Enable Accessibility above to execute Back, Home, Recents, Screenshot, Lock & Scroll across all apps."
                }
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlobalActionButton("Back", Icons.AutoMirrored.Filled.ArrowBack) { onGlobalAction("back") }
                    GlobalActionButton("Home", Icons.Filled.Home) { onGlobalAction("home") }
                    GlobalActionButton("Recents", Icons.Filled.ViewCarousel) { onGlobalAction("recents") }
                    GlobalActionButton("Notifications", Icons.Filled.Notifications) { onGlobalAction("notifications") }
                    GlobalActionButton("Quick Settings", Icons.Filled.Settings) { onGlobalAction("quick_settings") }
                    GlobalActionButton("Screenshot", Icons.Filled.Screenshot) { onGlobalAction("screenshot") }
                    GlobalActionButton("Lock Screen", Icons.Filled.Lock) { onGlobalAction("lock") }
                    GlobalActionButton("Power Menu", Icons.Filled.PowerSettingsNew) { onGlobalAction("power") }
                    OutlinedButton(
                        onClick = { onGlobalAction("scroll_up") },
                        border = BorderStroke(1.dp, JarvisBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) { Text("Scroll Up", color = JarvisCyan) }
                    OutlinedButton(
                        onClick = { onGlobalAction("scroll_down") },
                        border = BorderStroke(1.dp, JarvisBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) { Text("Scroll Down", color = JarvisCyan) }
                    OutlinedButton(
                        onClick = { onGlobalAction("scroll_left") },
                        border = BorderStroke(1.dp, JarvisBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) { Text("Scroll Left", color = JarvisCyan) }
                    OutlinedButton(
                        onClick = { onGlobalAction("scroll_right") },
                        border = BorderStroke(1.dp, JarvisBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) { Text("Scroll Right", color = JarvisCyan) }
                }
            }
        }

        // 4. Hardware Display, Flashlight & Audio Controls
        item {
            PhoneSectionCard(
                icon = Icons.Filled.BrightnessHigh,
                title = "Display, Torch & Audio Console",
                subtitle = "Real-time hardware control for Flashlight, Screen Brightness, Ringer Mode, and Volume streams."
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onToggleTorch,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (controlState.torchOn) JarvisGold else JarvisSurfaceElevated,
                            contentColor = if (controlState.torchOn) JarvisNavy else JarvisCyan
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("phone_torch_button")
                    ) {
                        Icon(Icons.Filled.FlashlightOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (controlState.torchOn) "Torch: ON" else "Torch: OFF", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onOpenCameraApp(false) },
                        border = BorderStroke(1.dp, JarvisCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Camera App", color = JarvisCyan)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Screen Brightness: ${controlState.brightnessPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text("Auto", style = MaterialTheme.typography.labelSmall, color = JarvisTextMuted)
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = controlState.autoBrightness,
                        onCheckedChange = onToggleAutoBrightness,
                        colors = SwitchDefaults.colors(checkedThumbColor = JarvisNavy, checkedTrackColor = JarvisCyan)
                    )
                }
                Slider(
                    value = controlState.brightnessPercent.toFloat(),
                    onValueChange = { onSetBrightness(it.toInt()) },
                    valueRange = 5f..100f,
                    colors = SliderDefaults.colors(thumbColor = JarvisGold, activeTrackColor = JarvisGold),
                    modifier = Modifier.testTag("phone_brightness_slider")
                )

                Text(
                    text = "Media Volume: ${controlState.mediaVolumePercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisTextPrimary
                )
                Slider(
                    value = controlState.mediaVolumePercent.toFloat(),
                    onValueChange = { onSetStreamVolume(AudioManager.STREAM_MUSIC, it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = JarvisCyan, activeTrackColor = JarvisCyan)
                )

                Text(
                    text = "Ringtone Volume: ${controlState.ringVolumePercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisTextPrimary
                )
                Slider(
                    value = controlState.ringVolumePercent.toFloat(),
                    onValueChange = { onSetStreamVolume(AudioManager.STREAM_RING, it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = JarvisCyan, activeTrackColor = JarvisCyan)
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text("Ringer Mode", style = MaterialTheme.typography.labelMedium, color = JarvisTextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhoneRingerMode.entries.forEach { mode ->
                        FilterChip(
                            selected = controlState.ringerMode == mode,
                            onClick = { onSetRingerMode(mode) },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JarvisCyan.copy(alpha = 0.22f),
                                selectedLabelColor = JarvisCyan,
                                containerColor = JarvisSurfaceElevated,
                                labelColor = JarvisTextPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onDispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                        border = BorderStroke(1.dp, JarvisBorder),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous track", tint = JarvisTextPrimary) }
                    OutlinedButton(
                        onClick = { onDispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
                        border = BorderStroke(1.dp, JarvisCyan),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) { Icon(Icons.Filled.Pause, contentDescription = "Play/Pause", tint = JarvisCyan) }
                    OutlinedButton(
                        onClick = { onDispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) },
                        border = BorderStroke(1.dp, JarvisBorder),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) { Icon(Icons.Filled.SkipNext, contentDescription = "Next track", tint = JarvisTextPrimary) }
                }
            }
        }

        // 5. Dialer, Timers & System Quick Panels
        item {
            PhoneSectionCard(
                icon = Icons.Filled.VolumeUp,
                title = "Phone Calls, Timers & Quick System Panels",
                subtitle = "Dial any number, start timers, launch video recording, or open system control panels."
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = phoneNumberDraft,
                        onValueChange = { phoneNumberDraft = it },
                        singleLine = true,
                        label = { Text("Phone number to dial…") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onOpenDialer(phoneNumberDraft) },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisEmerald, contentColor = JarvisNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dial", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlobalActionButton("5m Timer", Icons.Filled.Timer) { onSetTimerMinutes(5) }
                    GlobalActionButton("Record Video", Icons.Filled.CameraAlt) { onOpenCameraApp(true) }
                    GlobalActionButton("Internet Panel", Icons.Filled.Settings) { onOpenSystemPanel("internet") }
                    GlobalActionButton("Volume Panel", Icons.Filled.VolumeUp) { onOpenSystemPanel("volume") }
                    GlobalActionButton("Battery", Icons.Filled.Tune) { onOpenSystemPanel("battery") }
                    GlobalActionButton("All Settings", Icons.Filled.Settings) { onOpenSystemPanel("settings") }
                }
            }
        }
    }
}

@Composable
private fun AccessStatusRow(label: String, granted: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (granted) JarvisEmerald else JarvisGold,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: ${if (granted) "ACTIVE" else "TAP BELOW TO ENABLE"}",
            style = MaterialTheme.typography.bodySmall,
            color = if (granted) JarvisEmerald else JarvisTextPrimary
        )
    }
}

@Composable
private fun GlobalActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceElevated, contentColor = JarvisCyan),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(44.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PhoneSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, JarvisBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = JarvisCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = JarvisTextPrimary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = JarvisTextMuted)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
