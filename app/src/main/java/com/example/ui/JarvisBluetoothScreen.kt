package com.example.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Watch
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.bluetooth.BluetoothAdapterState
import com.example.bluetooth.BluetoothLogEntry
import com.example.bluetooth.BluetoothUiFilter
import com.example.bluetooth.BondStatus
import com.example.bluetooth.ConnectionStatus
import com.example.bluetooth.DeviceCategory
import com.example.bluetooth.DiscoveryState
import com.example.bluetooth.JarvisBluetoothDevice
import com.example.bluetooth.JarvisBluetoothViewModel
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JarvisBluetoothScreen(
    modifier: Modifier = Modifier,
    viewModel: JarvisBluetoothViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showLogs by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.onPermissionsResult()
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onAdapterStateChanged()
    }

    val isScanning = state.discoveryState is DiscoveryState.Scanning
    val isBonding = state.devices.any { it.bondStatus == BondStatus.BONDING }
    val activeAccent by animateColorAsState(
        targetValue = if (isScanning || isBonding) JarvisGold else JarvisCyan,
        label = "jarvis_accent"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisNavy),
        contentAlignment = Alignment.TopCenter
    ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    JarvisHudHeader(
                        accent = activeAccent,
                        isScanning = isScanning,
                        adapterState = state.adapterState,
                        onOpenSystemSettings = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            }.onFailure {
                                runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                            }
                        }
                    )
                }

                item {
                    JarvisOrbScannerCard(
                        accent = activeAccent,
                        isScanning = isScanning,
                        isBonding = isBonding,
                        adapterState = state.adapterState,
                        hasPermissions = state.hasPermissions,
                        totalDevices = state.devices.size,
                        pairedCount = state.pairedCount,
                        connectedCount = state.connectedCount,
                        onScanToggle = {
                            if (!state.hasPermissions) {
                                permissionLauncher.launch(viewModel.requiredPermissions())
                            } else if (state.adapterState !is BluetoothAdapterState.Enabled) {
                                runCatching {
                                    enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                }.onFailure {
                                    runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                                }
                            } else {
                                viewModel.toggleDiscovery()
                            }
                        }
                    )
                }

                if (state.adapterState is BluetoothAdapterState.Unsupported) {
                    item {
                        StatusAlertCard(
                            title = stringResource(R.string.bt_unsupported_title),
                            body = stringResource(R.string.bt_unsupported_body),
                            icon = Icons.Filled.BluetoothDisabled,
                            accentColor = JarvisAlertRed
                        )
                    }
                } else if (!state.hasPermissions) {
                    item {
                        StatusAlertCard(
                            title = stringResource(R.string.bt_permission_title),
                            body = stringResource(R.string.bt_permission_body),
                            icon = Icons.Filled.BluetoothSearching,
                            accentColor = JarvisGold,
                            actionLabel = stringResource(R.string.bt_grant_permissions),
                            actionTestTag = "grant_permissions_button",
                            onAction = { permissionLauncher.launch(viewModel.requiredPermissions()) }
                        )
                    }
                } else if (state.adapterState is BluetoothAdapterState.Disabled) {
                    item {
                        StatusAlertCard(
                            title = stringResource(R.string.bt_disabled_title),
                            body = stringResource(R.string.bt_disabled_body),
                            icon = Icons.Filled.BluetoothDisabled,
                            accentColor = JarvisGold,
                            actionLabel = stringResource(R.string.bt_enable),
                            actionTestTag = "enable_bluetooth_button",
                            onAction = {
                                runCatching {
                                    enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                }.onFailure {
                                    runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                                }
                            }
                        )
                    }
                }

                state.statusNotice?.let { notice ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JarvisSurfaceElevated),
                            border = BorderStroke(1.dp, JarvisGold.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = notice,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarvisGold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = viewModel::dismissNotice,
                                    modifier = Modifier.testTag("dismiss_notice_button")
                                ) {
                                    Text("OK", color = JarvisCyan)
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(BluetoothUiFilter.entries) { filter ->
                                val selected = state.selectedFilter == filter
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.selectFilter(filter) },
                                    label = {
                                        Text(
                                            text = filter.label,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = JarvisCyan.copy(alpha = 0.18f),
                                        selectedLabelColor = JarvisCyan,
                                        containerColor = JarvisSurface,
                                        labelColor = JarvisTextMuted
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = JarvisBorder,
                                        selectedBorderColor = JarvisCyan
                                    ),
                                    modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { showLogs = !showLogs },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("toggle_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Terminal,
                                contentDescription = stringResource(R.string.bt_telemetry_title),
                                tint = if (showLogs) JarvisCyan else JarvisTextMuted
                            )
                        }
                    }
                }

                item {
                    AnimatedVisibility(visible = showLogs) {
                        TelemetryLogCard(
                            logs = state.logs,
                            onClear = viewModel::clearLogs
                        )
                    }
                }

                if (state.filteredDevices.isEmpty()) {
                    item {
                        EmptyDevicesCard()
                    }
                } else {
                    items(
                        items = state.filteredDevices,
                        key = { it.address }
                    ) { device ->
                        BluetoothDeviceCard(
                            device = device,
                            onPairClick = {
                                if (device.bondStatus == BondStatus.BONDED) {
                                    viewModel.unpairDevice(device.address)
                                } else {
                                    viewModel.pairDevice(device.address)
                                }
                            },
                            onConnectClick = { viewModel.toggleConnection(device) }
                        )
                    }
            }
        }
    }
}

@Composable
private fun JarvisHudHeader(
    accent: Color,
    isScanning: Boolean,
    adapterState: BluetoothAdapterState,
    onOpenSystemSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(accent, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.bt_hub_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = accent
                )
            }
            Text(
                text = when {
                    isScanning -> "Scanning Classic + BLE frequencies..."
                    adapterState is BluetoothAdapterState.Enabled -> stringResource(R.string.bt_hub_subtitle)
                    else -> "Radio standby • Ready for activation"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = JarvisTextMuted
            )
        }

        IconButton(
            onClick = onOpenSystemSettings,
            modifier = Modifier
                .size(48.dp)
                .testTag("system_bt_settings_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.bt_open_settings),
                tint = JarvisCyan
            )
        }
    }
}

@Composable
private fun JarvisOrbScannerCard(
    accent: Color,
    isScanning: Boolean,
    isBonding: Boolean,
    adapterState: BluetoothAdapterState,
    hasPermissions: Boolean,
    totalDevices: Int,
    pairedCount: Int,
    connectedCount: Int,
    onScanToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isScanning) 2600 else 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_sweep"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .drawWithCache {
                            val radius = size.minDimension / 2f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radialBrush = Brush.radialGradient(
                                colors = listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                                center = center,
                                radius = radius
                            )
                            val outerStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            val innerStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            val inset = 10.dp.toPx()
                            val innerTopLeft = Offset(inset, inset)
                            val innerSize = androidx.compose.ui.geometry.Size(
                                size.width - inset * 2f,
                                size.height - inset * 2f
                            )
                            val outerColor = accent.copy(alpha = 0.85f)
                            val innerColor = accent.copy(alpha = 0.55f)
                            onDrawBehind {
                                drawCircle(
                                    brush = radialBrush,
                                    radius = radius,
                                    center = center,
                                    alpha = pulse
                                )
                                drawArc(
                                    color = outerColor,
                                    startAngle = rotation,
                                    sweepAngle = 220f,
                                    useCenter = false,
                                    style = outerStroke
                                )
                                drawArc(
                                    color = innerColor,
                                    startAngle = -rotation * 1.4f,
                                    sweepAngle = 150f,
                                    useCenter = false,
                                    topLeft = innerTopLeft,
                                    size = innerSize,
                                    style = innerStroke
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {

                    Icon(
                        imageVector = when {
                            isScanning -> Icons.Filled.Radar
                            connectedCount > 0 -> Icons.Filled.BluetoothConnected
                            else -> Icons.Filled.Bluetooth
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isBonding -> "PAIRING SEQUENCE ACTIVE"
                            isScanning -> "DISCOVERY ACTIVE"
                            adapterState is BluetoothAdapterState.Enabled -> "BLUETOOTH ONLINE"
                            adapterState is BluetoothAdapterState.Unsupported -> "HARDWARE OFFLINE"
                            else -> "ADAPTER STANDBY"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatPill("FOUND", totalDevices.toString(), JarvisCyan, Modifier.weight(1f))
                        TelemetryStatPill("PAIRED", pairedCount.toString(), JarvisGold, Modifier.weight(1f))
                        TelemetryStatPill("LINKED", connectedCount.toString(), JarvisEmerald, Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onScanToggle,
                enabled = adapterState !is BluetoothAdapterState.Unsupported,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) JarvisGold else JarvisCyan,
                    contentColor = JarvisNavy
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("scan_toggle_button")
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Filled.Stop else Icons.Filled.BluetoothSearching,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        !hasPermissions -> stringResource(R.string.bt_grant_permissions)
                        adapterState !is BluetoothAdapterState.Enabled -> stringResource(R.string.bt_enable)
                        isScanning -> stringResource(R.string.bt_scan_stop)
                        else -> stringResource(R.string.bt_scan_start)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TelemetryStatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = JarvisSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, JarvisBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = JarvisTextMuted
            )
        }
    }
}

@Composable
private fun BluetoothDeviceCard(
    device: JarvisBluetoothDevice,
    onPairClick: () -> Unit,
    onConnectClick: () -> Unit
) {
    val isConnected = device.connectionStatus == ConnectionStatus.CONNECTED
    val isConnecting = device.connectionStatus == ConnectionStatus.CONNECTING
    val isBonded = device.bondStatus == BondStatus.BONDED
    val isBonding = device.bondStatus == BondStatus.BONDING

    val borderColor = when {
        isConnected -> JarvisEmerald.copy(alpha = 0.7f)
        isBonding || isConnecting -> JarvisGold.copy(alpha = 0.7f)
        isBonded -> JarvisCyan.copy(alpha = 0.45f)
        else -> JarvisBorder
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_card_${device.address}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconForCategory(device.category),
                            contentDescription = device.category.label,
                            tint = if (isConnected) JarvisEmerald else JarvisCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${device.address} • ${device.category.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                device.rssi?.let { rssi ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(JarvisSurfaceElevated, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SignalCellularAlt,
                            contentDescription = "Signal strength",
                            tint = signalColor(rssi),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${rssi} dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = signalColor(rssi)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusTag(device.transportType.name, JarvisCyan)
                StatusTag(
                    text = when (device.bondStatus) {
                        BondStatus.BONDED -> "PAIRED"
                        BondStatus.BONDING -> "PAIRING..."
                        BondStatus.NONE -> "UNPAIRED"
                    },
                    color = when (device.bondStatus) {
                        BondStatus.BONDED -> JarvisCyan
                        BondStatus.BONDING -> JarvisGold
                        BondStatus.NONE -> JarvisTextMuted
                    }
                )
                StatusTag(
                    text = device.connectionStatus.name,
                    color = when (device.connectionStatus) {
                        ConnectionStatus.CONNECTED -> JarvisEmerald
                        ConnectionStatus.CONNECTING,
                        ConnectionStatus.DISCONNECTING -> JarvisGold
                        ConnectionStatus.FAILED -> JarvisAlertRed
                        ConnectionStatus.DISCONNECTED -> JarvisTextMuted
                    }
                )
                if (device.servicesCount > 0) {
                    StatusTag("${device.servicesCount} GATT", JarvisEmerald)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPairClick,
                    enabled = !isBonding,
                    border = BorderStroke(1.dp, if (isBonded) JarvisBorder else JarvisGold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("pair_button_${device.address}")
                ) {
                    Text(
                        text = if (isBonded) stringResource(R.string.bt_unpair) else stringResource(R.string.bt_pair),
                        color = if (isBonded) JarvisTextMuted else JarvisGold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Button(
                    onClick = onConnectClick,
                    enabled = !isConnecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) JarvisAlertRed.copy(alpha = 0.85f) else JarvisCyan,
                        contentColor = if (isConnected) JarvisTextPrimary else JarvisNavy
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("connect_button_${device.address}")
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Filled.LinkOff else Icons.Filled.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) {
                            stringResource(R.string.bt_disconnect)
                        } else {
                            stringResource(R.string.bt_connect)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusTag(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun StatusAlertCard(
    title: String,
    body: String,
    icon: ImageVector,
    accentColor: Color,
    actionLabel: String? = null,
    actionTestTag: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = accentColor)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = JarvisTextMuted
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = JarvisNavy
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .then(if (actionTestTag != null) Modifier.testTag(actionTestTag) else Modifier)
                ) {
                    Text(text = actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TelemetryLogCard(
    logs: List<BluetoothLogEntry>,
    onClear: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, JarvisBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.bt_telemetry_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = JarvisCyan
                )
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.testTag("clear_logs_button")
                ) {
                    Text(stringResource(R.string.bt_clear_log), color = JarvisTextMuted)
                }
            }
            if (logs.isEmpty()) {
                Text(
                    text = "No Bluetooth events logged yet.",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextMuted
                )
            } else {
                logs.take(8).forEach { entry ->
                    val time = formatter.format(Date(entry.timestampMillis))
                    Text(
                        text = "[$time] ${entry.message}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entry.isAlert) JarvisAlertRed else JarvisTextPrimary,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDevicesCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, JarvisBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.BluetoothSearching,
                contentDescription = null,
                tint = JarvisCyan,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.bt_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = JarvisTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.bt_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = JarvisTextMuted
            )
        }
    }
}

private fun iconForCategory(category: DeviceCategory): ImageVector {
    return when (category) {
        DeviceCategory.AUDIO -> Icons.Filled.Headset
        DeviceCategory.PHONE -> Icons.Filled.PhoneAndroid
        DeviceCategory.COMPUTER -> Icons.Filled.Computer
        DeviceCategory.WEARABLE -> Icons.Filled.Watch
        DeviceCategory.PERIPHERAL -> Icons.Filled.Keyboard
        DeviceCategory.IOT_HEALTH,
        DeviceCategory.UNKNOWN -> Icons.Filled.DevicesOther
    }
}

private fun signalColor(rssi: Int): Color {
    return when {
        rssi >= -60 -> JarvisEmerald
        rssi >= -80 -> JarvisCyan
        else -> JarvisGold
    }
}
