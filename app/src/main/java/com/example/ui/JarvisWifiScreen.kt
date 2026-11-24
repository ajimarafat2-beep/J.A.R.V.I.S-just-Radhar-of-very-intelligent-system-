package com.example.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
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
import com.example.wifi.ActiveWifiLinkInfo
import com.example.wifi.JarvisWifiNetwork
import com.example.wifi.JarvisWifiViewModel
import com.example.wifi.WifiConnectionMode
import com.example.wifi.WifiConnectionStatus
import com.example.wifi.WifiLogEntry
import com.example.wifi.WifiRadioState
import com.example.wifi.WifiScanState
import com.example.wifi.WifiSecurityType
import com.example.wifi.WifiUiFilter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JarvisWifiScreen(
    modifier: Modifier = Modifier,
    viewModel: JarvisWifiViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showLogs by remember { mutableStateOf(false) }
    var dialogTarget by remember { mutableStateOf<WifiConnectDialogTarget?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.onPermissionsResult()
    }

    val wifiPanelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onRadioPanelClosed()
    }

    val openWifiPanel: () -> Unit = {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        wifiPanelLauncher.launch(intent)
    }

    val isScanning = state.scanState is WifiScanState.Scanning
    val accent by animateColorAsState(
        targetValue = if (isScanning) JarvisGold else JarvisCyan,
        label = "wifi_accent"
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
                                text = stringResource(R.string.wifi_hub_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = accent
                            )
                        }
                        Text(
                            text = stringResource(R.string.wifi_hub_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextMuted
                        )
                    }

                    IconButton(
                        onClick = openWifiPanel,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("wifi_panel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.wifi_open_panel),
                            tint = JarvisCyan
                        )
                    }
                }
            }

            item {
                WifiHubControlCard(
                    accent = accent,
                    isScanning = isScanning,
                    radioState = state.radioState,
                    hasPermissions = state.hasPermissions,
                    activeLink = state.activeLink,
                    totalNetworks = state.networks.size,
                    savedCount = state.savedCount,
                    onScanClick = {
                        if (!state.hasPermissions) {
                            permissionLauncher.launch(viewModel.requiredPermissions())
                        } else if (state.radioState !is WifiRadioState.Enabled) {
                            openWifiPanel()
                        } else {
                            viewModel.startScan()
                        }
                    },
                    onAddCustomClick = {
                        dialogTarget = WifiConnectDialogTarget(
                            ssid = "",
                            securityType = WifiSecurityType.WPA2,
                            isCustomSsid = true
                        )
                    }
                )
            }

            if (state.radioState is WifiRadioState.Unsupported) {
                item {
                    WifiBannerCard(
                        title = stringResource(R.string.wifi_unsupported_title),
                        body = stringResource(R.string.wifi_unsupported_body),
                        icon = Icons.Filled.WifiOff,
                        color = JarvisAlertRed
                    )
                }
            } else if (!state.hasPermissions) {
                item {
                    WifiBannerCard(
                        title = stringResource(R.string.wifi_permission_title),
                        body = stringResource(R.string.wifi_permission_body),
                        icon = Icons.Filled.WifiFind,
                        color = JarvisGold,
                        actionLabel = stringResource(R.string.wifi_grant_permissions),
                        testTag = "wifi_grant_permissions_button",
                        onAction = { permissionLauncher.launch(viewModel.requiredPermissions()) }
                    )
                }
            } else if (state.radioState is WifiRadioState.Disabled) {
                item {
                    WifiBannerCard(
                        title = stringResource(R.string.wifi_disabled_title),
                        body = stringResource(R.string.wifi_disabled_body),
                        icon = Icons.Filled.WifiOff,
                        color = JarvisGold,
                        actionLabel = stringResource(R.string.wifi_open_panel),
                        testTag = "wifi_enable_panel_button",
                        onAction = openWifiPanel
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
                                modifier = Modifier.testTag("wifi_dismiss_notice")
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(WifiUiFilter.entries) { filter ->
                            val selected = state.selectedFilter == filter
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectFilter(filter) },
                                label = {
                                    Text(filter.label, style = MaterialTheme.typography.labelMedium)
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
                                modifier = Modifier.testTag("wifi_filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showLogs = !showLogs },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("wifi_toggle_logs")
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
                    WifiTelemetryCard(logs = state.logs, onClear = viewModel::clearLogs)
                }
            }

            if (state.filteredNetworks.isEmpty()) {
                item {
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
                                imageVector = Icons.Filled.WifiFind,
                                contentDescription = null,
                                tint = JarvisCyan,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.wifi_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisTextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.wifi_empty_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextMuted
                            )
                        }
                    }
                }
            } else {
                items(
                    items = state.filteredNetworks,
                    key = { "${it.ssid}_${it.bssid}" }
                ) { net ->
                    WifiNetworkCard(
                        network = net,
                        onConnectClick = {
                            dialogTarget = WifiConnectDialogTarget(
                                ssid = net.ssid,
                                securityType = net.securityType,
                                isCustomSsid = false
                            )
                        },
                        onDisconnectClick = viewModel::disconnectLocalLink,
                        onForgetClick = { viewModel.forgetProfile(net.ssid) }
                    )
                }
            }
        }
    }

    dialogTarget?.let { target ->
        WifiConnectDialog(
            target = target,
            onDismiss = { dialogTarget = null },
            onConfirm = { ssid, pass, security, mode ->
                viewModel.connect(ssid, pass, security, mode)
                dialogTarget = null
            }
        )
    }
}

@Composable
private fun WifiHubControlCard(
    accent: Color,
    isScanning: Boolean,
    radioState: WifiRadioState,
    hasPermissions: Boolean,
    activeLink: ActiveWifiLinkInfo?,
    totalNetworks: Int,
    savedCount: Int,
    onScanClick: () -> Unit,
    onAddCustomClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.5.dp, accent),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                isScanning -> Icons.Filled.Radar
                                activeLink != null -> Icons.Filled.Wifi
                                else -> Icons.Filled.WifiOff
                            },
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (activeLink != null) {
                            "LINKED: ${activeLink.ssid}"
                        } else if (radioState is WifiRadioState.Enabled) {
                            "WI-FI RADIO ONLINE"
                        } else {
                            "WI-FI STANDBY"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (activeLink != null) JarvisEmerald else accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (activeLink != null) {
                            "${activeLink.linkSpeedMbps} Mbps • ${activeLink.rssi} dBm • " +
                                if (activeLink.hasInternet) "Internet Ready" else "Local LAN Only"
                        } else {
                            "$totalNetworks nearby APs • $savedCount saved profile(s)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onScanClick,
                    enabled = radioState !is WifiRadioState.Unsupported,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = JarvisNavy
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("wifi_scan_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.WifiFind,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            !hasPermissions -> stringResource(R.string.wifi_grant_permissions)
                            radioState !is WifiRadioState.Enabled -> stringResource(R.string.wifi_open_panel)
                            isScanning -> stringResource(R.string.wifi_scanning)
                            else -> stringResource(R.string.wifi_scan_start)
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onAddCustomClick,
                    border = BorderStroke(1.dp, JarvisCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("wifi_add_custom_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.wifi_add_custom),
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SSID", color = JarvisCyan, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun WifiNetworkCard(
    network: JarvisWifiNetwork,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onForgetClick: () -> Unit
) {
    val isConnected = network.connectionStatus == WifiConnectionStatus.CONNECTED
    val isConnecting = network.connectionStatus == WifiConnectionStatus.CONNECTING

    val borderColor = when {
        isConnected -> JarvisEmerald.copy(alpha = 0.75f)
        isConnecting -> JarvisGold.copy(alpha = 0.7f)
        network.isSecurityWarning -> JarvisAlertRed.copy(alpha = 0.5f)
        else -> JarvisBorder
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wifi_card_${network.ssid}")
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
                            imageVector = if (network.isSecurityWarning) Icons.Filled.LockOpen else Icons.Filled.Lock,
                            contentDescription = network.securityType.label,
                            tint = when {
                                isConnected -> JarvisEmerald
                                network.isSecurityWarning -> JarvisAlertRed
                                else -> JarvisCyan
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = network.ssid,
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${network.bandLabel} • ${network.securityType.label}" +
                            if (network.bssid.isNotBlank()) " • ${network.bssid}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = JarvisSurfaceElevated,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${network.signalPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (network.signalPercent >= 60) JarvisEmerald else JarvisCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isConnected) {
                    WifiBadge("CONNECTED", JarvisEmerald)
                } else if (isConnecting) {
                    WifiBadge("CONNECTING…", JarvisGold)
                }
                if (network.isSavedProfile) {
                    WifiBadge("SAVED PROFILE", JarvisCyan)
                }
                if (network.isSecurityWarning) {
                    WifiBadge("UNENCRYPTED / WEAK", JarvisAlertRed)
                } else {
                    WifiBadge("WHITE-HAT SAFE", JarvisEmerald)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (network.isSavedProfile) {
                    OutlinedButton(
                        onClick = onForgetClick,
                        border = BorderStroke(1.dp, JarvisBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("wifi_forget_${network.ssid}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.wifi_forget),
                            tint = JarvisTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Button(
                    onClick = if (isConnected) onDisconnectClick else onConnectClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) JarvisSurfaceElevated else JarvisCyan,
                        contentColor = if (isConnected) JarvisCyan else JarvisNavy
                    ),
                    border = if (isConnected) BorderStroke(1.dp, JarvisCyan) else null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("wifi_connect_${network.ssid}")
                ) {
                    Text(
                        text = if (isConnected) {
                            stringResource(R.string.wifi_disconnect)
                        } else {
                            stringResource(R.string.wifi_connect)
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private data class WifiConnectDialogTarget(
    val ssid: String,
    val securityType: WifiSecurityType,
    val isCustomSsid: Boolean
)

@Composable
private fun WifiConnectDialog(
    target: WifiConnectDialogTarget,
    onDismiss: () -> Unit,
    onConfirm: (String, String, WifiSecurityType, WifiConnectionMode) -> Unit
) {
    var ssid by remember { mutableStateOf(target.ssid) }
    var passphrase by remember { mutableStateOf("") }
    var security by remember { mutableStateOf(target.securityType) }
    var mode by remember { mutableStateOf(WifiConnectionMode.INTERNET_SUGGESTION) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisSurface,
        titleContentColor = JarvisCyan,
        textContentColor = JarvisTextPrimary,
        title = {
            Text(
                text = if (target.isCustomSsid) "Connect to Any Wi-Fi" else "Connect: ${target.ssid}",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (target.isCustomSsid) {
                    OutlinedTextField(
                        value = ssid,
                        onValueChange = { ssid = it },
                        label = { Text("Network Name (SSID)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wifi_dialog_ssid_input")
                    )

                    Text("Security Protocol", style = MaterialTheme.typography.labelMedium, color = JarvisTextMuted)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(WifiSecurityType.WPA2, WifiSecurityType.WPA3, WifiSecurityType.OPEN)) { s ->
                            FilterChip(
                                selected = security == s,
                                onClick = { security = s },
                                label = { Text(s.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                if (security != WifiSecurityType.OPEN) {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Wi-Fi Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wifi_dialog_password_input")
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = JarvisAlertRed)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open network: traffic is unencrypted.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisAlertRed
                        )
                    }
                }

                Text("Connection Mode", style = MaterialTheme.typography.labelMedium, color = JarvisTextMuted)
                WifiConnectionMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m },
                        label = { Text(m.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(ssid, passphrase, security, mode) },
                enabled = ssid.isNotBlank() && (security == WifiSecurityType.OPEN || passphrase.length >= 8),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisNavy),
                modifier = Modifier.testTag("wifi_dialog_confirm")
            ) {
                Text("Connect", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = JarvisTextMuted)
            }
        }
    )
}

@Composable
private fun WifiBadge(text: String, color: Color) {
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
private fun WifiBannerCard(
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    actionLabel: String? = null,
    testTag: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = color)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = color)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = JarvisTextMuted)
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = JarvisNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                ) {
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WifiTelemetryCard(logs: List<WifiLogEntry>, onClear: () -> Unit) {
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
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.bt_clear_log), color = JarvisTextMuted)
                }
            }
            if (logs.isEmpty()) {
                Text(
                    text = "No Wi-Fi events logged yet.",
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
