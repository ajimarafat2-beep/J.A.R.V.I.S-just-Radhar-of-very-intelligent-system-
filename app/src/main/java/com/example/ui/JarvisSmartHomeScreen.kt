package com.example.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WindPower
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.smarthome.LanSmartNode
import com.example.smarthome.SmartDeviceType
import com.example.smarthome.SmartHomeDevice
import com.example.smarthome.SmartHubConfig
import com.example.ui.theme.JarvisAlertRed
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
fun JarvisSmartHomeScreen(
    devices: List<SmartHomeDevice>,
    lanNodes: List<LanSmartNode>,
    isScanningLan: Boolean,
    hubConfig: SmartHubConfig,
    googleHomeInstalled: Boolean,
    statusMessage: String?,
    onOpenGoogleHome: () -> Unit,
    onOpenDeviceControls: () -> Unit,
    onRelayGoogleAssistant: (String) -> Unit,
    onScanLan: () -> Unit,
    onSaveHubConfig: (String, String) -> Unit,
    onAddDevice: (String, String, SmartDeviceType, String) -> Unit,
    onRemoveDevice: (String) -> Unit,
    onTogglePower: (String) -> Unit,
    onSetWashingCycle: (String, String, Boolean) -> Unit,
    onAdjustWashingRpm: (String, Int) -> Unit,
    onAdjustTemp: (String, Int, Boolean) -> Unit,
    onSetMode: (String, String) -> Unit,
    onAdjustTvVolume: (String, Int, Boolean) -> Unit,
    onAdjustLevel: (String, Int) -> Unit,
    onToggleDoorLock: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showHubDialog by remember { mutableStateOf(false) }
    var showAddDeviceDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisNavy),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(modifier = Modifier.widthIn(max = 740.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JarvisHologramOrb(size = 24.dp, activeGold = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "JARVIS // GOOGLE HOME & IoT",
                        style = MaterialTheme.typography.titleLarge,
                        color = JarvisCyan
                    )
                }
                Text(
                    text = "Control Washing Machine, Smart AC, TV, Refrigerator, Lights & LAN Matter/Home Assistant devices",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextMuted
                )
            }
        }

        // Google Home & LAN Hub Bridge Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, if (googleHomeInstalled) JarvisEmerald.copy(0.55f) else JarvisBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(max = 740.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Home, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (googleHomeInstalled) "Google Home App Connected" else "Google Home Bridge Ready",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (googleHomeInstalled) JarvisEmerald else JarvisGold
                            )
                            Text(
                                text = if (hubConfig.hubBaseUrl.isNotBlank()) {
                                    "LAN Hub: ${hubConfig.hubBaseUrl}"
                                } else {
                                    "Tap Open Google Home or configure a Home Assistant / LAN Hub"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextMuted
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenGoogleHome,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisNavy),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp).testTag("smart_open_google_home_button")
                        ) {
                            Text(if (googleHomeInstalled) "Open Google Home" else "Install Google Home", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onOpenDeviceControls,
                            border = BorderStroke(1.dp, JarvisBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp).testTag("smart_device_controls_button")
                        ) {
                            Text("System Controls", color = JarvisTextPrimary)
                        }
                        OutlinedButton(
                            onClick = onScanLan,
                            border = BorderStroke(1.dp, JarvisCyan.copy(0.6f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp).testTag("smart_scan_lan_button")
                        ) {
                            Icon(Icons.Filled.Radar, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isScanningLan) "Scanning…" else "Scan Wi-Fi IoT", color = JarvisCyan)
                        }
                        OutlinedButton(
                            onClick = { showHubDialog = true },
                            border = BorderStroke(1.dp, JarvisGold.copy(0.6f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp).testTag("smart_config_hub_button")
                        ) {
                            Icon(Icons.Filled.Hub, contentDescription = null, tint = JarvisGold, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hub Config", color = JarvisGold)
                        }
                        OutlinedButton(
                            onClick = { showAddDeviceDialog = true },
                            border = BorderStroke(1.dp, JarvisBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp).testTag("smart_add_device_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = JarvisTextPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Device", color = JarvisTextPrimary)
                        }
                    }

                    statusMessage?.let { msg ->
                        Text(text = msg, style = MaterialTheme.typography.labelMedium, color = JarvisCyan)
                    }
                }
            }
        }

        // Discovered real mDNS LAN nodes (Chromecast / Matter / Home Assistant)
        if (lanNodes.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisSurfaceElevated),
                    border = BorderStroke(1.dp, JarvisCyan.copy(0.45f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.widthIn(max = 740.dp).fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Discovered LAN IoT & Google Cast Nodes (${lanNodes.size})", style = MaterialTheme.typography.titleSmall, color = JarvisCyan)
                        lanNodes.forEach { node ->
                            Text(
                                text = "• ${node.serviceName} (${node.serviceType}) — ${node.hostAddress}:${node.port}",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextPrimary
                            )
                        }
                    }
                }
            }
        }

        // Appliance Cards
        items(devices, key = { it.id }) { device ->
            SmartDeviceCard(
                device = device,
                onTogglePower = { onTogglePower(device.id) },
                onSetWashingCycle = { mode, toggleRun -> onSetWashingCycle(device.id, mode, toggleRun) },
                onAdjustWashingRpm = { rpm -> onAdjustWashingRpm(device.id, rpm) },
                onAdjustTemp = { temp, isFreezer -> onAdjustTemp(device.id, temp, isFreezer) },
                onSetMode = { mode -> onSetMode(device.id, mode) },
                onAdjustTvVolume = { delta, mute -> onAdjustTvVolume(device.id, delta, mute) },
                onAdjustLevel = { lvl -> onAdjustLevel(device.id, lvl) },
                onToggleDoorLock = { onToggleDoorLock(device.id) },
                onRelayAssistant = {
                    val phrase = buildAssistantCommand(device)
                    onRelayGoogleAssistant(phrase)
                },
                onDelete = { onRemoveDevice(device.id) }
            )
        }
    }

    if (showHubDialog) {
        HubConfigDialog(
            initialUrl = hubConfig.hubBaseUrl,
            initialToken = hubConfig.bearerToken,
            onDismiss = { showHubDialog = false },
            onSave = { url, token ->
                onSaveHubConfig(url, token)
                showHubDialog = false
            }
        )
    }

    if (showAddDeviceDialog) {
        AddSmartDeviceDialog(
            onDismiss = { showAddDeviceDialog = false },
            onAdd = { name, room, type, entityId ->
                onAddDevice(name, room, type, entityId)
                showAddDeviceDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmartDeviceCard(
    device: SmartHomeDevice,
    onTogglePower: () -> Unit,
    onSetWashingCycle: (String, Boolean) -> Unit,
    onAdjustWashingRpm: (Int) -> Unit,
    onAdjustTemp: (Int, Boolean) -> Unit,
    onSetMode: (String) -> Unit,
    onAdjustTvVolume: (Int, Boolean) -> Unit,
    onAdjustLevel: (Int) -> Unit,
    onToggleDoorLock: () -> Unit,
    onRelayAssistant: () -> Unit,
    onDelete: () -> Unit
) {
    val active = device.isPoweredOn
    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, if (active) JarvisCyan.copy(0.6f) else JarvisBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.widthIn(max = 740.dp).fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(iconForType(device.type), contentDescription = null, tint = if (active) JarvisCyan else JarvisTextMuted)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium, color = JarvisTextPrimary)
                    Text("${device.room} • ${device.lastSyncedStatus}", style = MaterialTheme.typography.labelSmall, color = JarvisTextMuted)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove device", tint = JarvisTextMuted)
                }
                if (device.type == SmartDeviceType.DOOR_LOCK) {
                    IconButton(onClick = onToggleDoorLock, modifier = Modifier.size(48.dp)) {
                        Icon(
                            if (device.isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = "Toggle lock",
                            tint = if (device.isLocked) JarvisEmerald else JarvisAlertRed
                        )
                    }
                } else {
                    Switch(
                        checked = active,
                        onCheckedChange = { onTogglePower() },
                        colors = SwitchDefaults.colors(checkedThumbColor = JarvisNavy, checkedTrackColor = JarvisCyan),
                        modifier = Modifier.testTag("smart_power_${device.id}")
                    )
                }
            }

            when (device.type) {
                SmartDeviceType.WASHING_MACHINE -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSetWashingCycle(device.mode.ifBlank { "Normal" }, true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (device.isActiveCycle) JarvisGold else JarvisCyan,
                                contentColor = JarvisNavy
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(if (device.isActiveCycle) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (device.isActiveCycle) "Pause Wash" else "Start Wash", fontWeight = FontWeight.Bold)
                        }
                        Text("Spin: ${device.primaryValue} RPM", style = MaterialTheme.typography.labelLarge, color = JarvisTextPrimary)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Normal", "Quick 15m", "Heavy", "Delicate", "Eco").forEach { mode ->
                            ModeChip(mode, device.mode == mode) { onSetWashingCycle(mode, false) }
                        }
                        listOf(800, 1200, 1400).forEach { rpm ->
                            ModeChip("${rpm} RPM", device.primaryValue == rpm) { onAdjustWashingRpm(rpm) }
                        }
                    }
                }

                SmartDeviceType.AIR_CONDITIONER -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { onAdjustTemp(device.primaryValue - 1, false) }, modifier = Modifier.height(44.dp)) {
                            Text("-1°C", color = JarvisCyan)
                        }
                        Text("${device.primaryValue}°C", style = MaterialTheme.typography.headlineSmall, color = JarvisCyan)
                        OutlinedButton(onClick = { onAdjustTemp(device.primaryValue + 1, false) }, modifier = Modifier.height(44.dp)) {
                            Text("+1°C", color = JarvisCyan)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Cool", "Dry", "Fan", "Auto").forEach { mode ->
                            ModeChip(mode, device.mode == mode) { onSetMode(mode) }
                        }
                    }
                }

                SmartDeviceType.SMART_TV -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onAdjustTvVolume(-5, false) }, modifier = Modifier.height(44.dp)) {
                            Text("Vol -", color = JarvisTextPrimary)
                        }
                        Text(
                            if (device.isMuted) "MUTED" else "Vol ${device.primaryValue}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (device.isMuted) JarvisGold else JarvisCyan
                        )
                        OutlinedButton(onClick = { onAdjustTvVolume(+5, false) }, modifier = Modifier.height(44.dp)) {
                            Text("Vol +", color = JarvisTextPrimary)
                        }
                        IconButton(onClick = { onAdjustTvVolume(0, true) }, modifier = Modifier.size(44.dp)) {
                            Icon(
                                if (device.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Toggle mute",
                                tint = JarvisGold
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("HDMI 1", "HDMI 2", "Live TV", "Cast").forEach { src ->
                            ModeChip(src, device.mode == src) { onSetMode(src) }
                        }
                    }
                }

                SmartDeviceType.REFRIGERATOR -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Fridge ${device.primaryValue}°C", color = JarvisCyan, style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(onClick = { onAdjustTemp(device.primaryValue - 1, false) }, modifier = Modifier.height(40.dp)) { Text("-") }
                        OutlinedButton(onClick = { onAdjustTemp(device.primaryValue + 1, false) }, modifier = Modifier.height(40.dp)) { Text("+") }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Freezer ${device.secondaryValue}°C", color = JarvisGold, style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(onClick = { onAdjustTemp(device.secondaryValue - 1, true) }, modifier = Modifier.height(40.dp)) { Text("-") }
                        OutlinedButton(onClick = { onAdjustTemp(device.secondaryValue + 1, true) }, modifier = Modifier.height(40.dp)) { Text("+") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Normal", "Eco", "Super Cool", "Vacation").forEach { mode ->
                            ModeChip(mode, device.mode == mode) { onSetMode(mode) }
                        }
                    }
                }

                SmartDeviceType.SMART_LIGHT, SmartDeviceType.CEILING_FAN -> {
                    val max = if (device.type == SmartDeviceType.CEILING_FAN) 5f else 100f
                    val unit = if (device.type == SmartDeviceType.CEILING_FAN) "Speed ${device.primaryValue}" else "${device.primaryValue}%"
                    Text(unit, style = MaterialTheme.typography.labelMedium, color = JarvisCyan)
                    Slider(
                        value = device.primaryValue.toFloat(),
                        onValueChange = { onAdjustLevel(it.toInt()) },
                        valueRange = (if (device.type == SmartDeviceType.CEILING_FAN) 1f else 0f)..max,
                        colors = SliderDefaults.colors(thumbColor = JarvisCyan, activeTrackColor = JarvisCyan)
                    )
                }

                else -> Unit
            }

            OutlinedButton(
                onClick = onRelayAssistant,
                border = BorderStroke(1.dp, JarvisBorder),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Send via Google Home Assistant", color = JarvisCyan)
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = JarvisCyan.copy(0.2f),
            selectedLabelColor = JarvisCyan,
            containerColor = JarvisSurfaceElevated,
            labelColor = JarvisTextPrimary
        )
    )
}

@Composable
private fun HubConfigDialog(
    initialUrl: String,
    initialToken: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf(initialToken) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisSurface,
        title = { Text("Home Assistant / LAN Hub", color = JarvisCyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Optional: enter your Home Assistant or local IoT hub URL for direct Wi-Fi REST execution.", color = JarvisTextMuted)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("http://192.168.1.50:8123") }, singleLine = true)
                OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Bearer Token (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(url, token) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddSmartDeviceDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, SmartDeviceType, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("Home") }
    var entityId by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(SmartDeviceType.WASHING_MACHINE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisSurface,
        title = { Text("Add Smart Appliance", color = JarvisCyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmartDeviceType.entries.forEach { t ->
                        ModeChip(t.displayLabel, selectedType == t) { selectedType = t }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Device Name") }, singleLine = true)
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("Room") }, singleLine = true)
                OutlinedTextField(value = entityId, onValueChange = { entityId = it }, label = { Text("Entity ID (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(name, room, selectedType, entityId) }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun iconForType(type: SmartDeviceType): ImageVector = when (type) {
    SmartDeviceType.WASHING_MACHINE -> Icons.Filled.LocalLaundryService
    SmartDeviceType.AIR_CONDITIONER -> Icons.Filled.AcUnit
    SmartDeviceType.SMART_TV -> Icons.Filled.Tv
    SmartDeviceType.REFRIGERATOR -> Icons.Filled.Kitchen
    SmartDeviceType.SMART_LIGHT -> Icons.Filled.Lightbulb
    SmartDeviceType.CEILING_FAN -> Icons.Filled.WindPower
    SmartDeviceType.SMART_PLUG -> Icons.Filled.Power
    SmartDeviceType.DOOR_LOCK -> Icons.Filled.Lock
}

private fun buildAssistantCommand(device: SmartHomeDevice): String = when (device.type) {
    SmartDeviceType.WASHING_MACHINE -> "Hey Google, ${if (device.isActiveCycle) "pause" else "start"} ${device.name} on ${device.mode}"
    SmartDeviceType.AIR_CONDITIONER -> "Hey Google, set ${device.name} to ${device.primaryValue} degrees"
    SmartDeviceType.SMART_TV -> "Hey Google, ${if (device.isPoweredOn) "turn off" else "turn on"} ${device.name}"
    SmartDeviceType.REFRIGERATOR -> "Hey Google, set ${device.name} to ${device.mode} mode"
    SmartDeviceType.DOOR_LOCK -> "Hey Google, ${if (device.isLocked) "unlock" else "lock"} ${device.name}"
    else -> "Hey Google, ${if (device.isPoweredOn) "turn off" else "turn on"} ${device.name}"
}
