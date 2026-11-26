package com.example.ui

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.security.JarvisSecurityEngine
import com.example.security.ScamAnalysisReport
import com.example.security.ShieldLiveState
import com.example.security.ThreatLevel
import com.example.security.WebsiteAuditReport
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
import kotlinx.coroutines.launch

@Composable
fun JarvisSecurityScreen(
    shieldState: ShieldLiveState = ShieldLiveState(),
    notificationPermissionGranted: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
    onToggleContinuousShield: (Boolean) -> Unit = {},
    onRunDeepScan: () -> Unit = {},
    onDeleteThreat: (String) -> Unit = {},
    onDeleteNextThreat: () -> Unit = {},
    onTrustPackage: (String) -> Unit = {},
    onUntrustPackage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var offlineIsolation by remember { mutableStateOf(false) }
    var scamInput by remember { mutableStateOf("") }
    var scamReport by remember { mutableStateOf<ScamAnalysisReport?>(null) }
    var urlInput by remember { mutableStateOf("") }
    var urlReport by remember { mutableStateOf<WebsiteAuditReport?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }

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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JarvisHologramOrb(size = 24.dp, activeGold = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = JarvisCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.sec_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = JarvisTextPrimary
                        )
                        Text(
                            text = stringResource(R.string.sec_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextMuted
                        )
                    }
                }
            }

            // 24/7 Always-On Threat & Virus Shield Hero Card
            item {
                val report = shieldState.report
                val highCount = report.highRiskApps.size
                val shieldTint = when {
                    highCount > 0 -> JarvisAlertRed
                    shieldState.continuousProtectionEnabled -> JarvisEmerald
                    else -> JarvisGold
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                    border = BorderStroke(1.2.dp, shieldTint.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = shieldTint)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "24/7 Always-On Threat & Virus Shield",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = JarvisTextPrimary
                                    )
                                    Text(
                                        text = when {
                                            shieldState.isScanningNow -> "Deep scanning installed packages…"
                                            highCount > 0 -> "ALERT: $highCount high-risk threat(s) detected"
                                            shieldState.continuousProtectionEnabled -> "Real-time monitor active (${if (shieldState.shieldServiceActive) "Background Service ON" else "Standby"})"
                                            else -> "Continuous background protection paused"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = shieldTint
                                    )
                                }
                            }
                            Surface(
                                color = shieldTint.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, shieldTint),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = "${report.overallScore}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = shieldTint,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }

                        if (shieldState.isScanningNow) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                color = JarvisCyan,
                                trackColor = JarvisSurfaceElevated,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = "Continuous Real-Time Phone Protection",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = JarvisTextPrimary
                                )
                                Text(
                                    text = "Automatically scans new installs, spyware permissions & background threats",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextMuted
                                )
                            }
                            Switch(
                                checked = shieldState.continuousProtectionEnabled,
                                onCheckedChange = onToggleContinuousShield,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = JarvisNavy,
                                    checkedTrackColor = JarvisEmerald
                                ),
                                modifier = Modifier.testTag("security_continuous_shield_switch")
                            )
                        }

                        if (!notificationPermissionGranted) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onRequestNotificationPermission,
                                border = BorderStroke(1.dp, JarvisGold),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("security_grant_notif_button")
                            ) {
                                Text("Enable Background Virus Alert Notifications", color = JarvisGold, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = JarvisSurfaceElevated,
                            border = BorderStroke(1.dp, JarvisBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Scanned: ${report.totalAppsScanned} apps • High Risk: $highCount • Caution: ${report.cautionApps.size} • Safe: ${report.safeApps.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = JarvisTextPrimary
                                )
                                Text(
                                    text = "OS Security Patch: ${report.integrity.securityPatchLevel} • Screen Lock: ${if (report.integrity.deviceLockEnabled) "Secured" else "Missing"} • USB Debug: ${if (report.integrity.adbDebuggingEnabled) "ON" else "OFF"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextMuted
                                )
                                report.integrity.warnings.forEach { w ->
                                    Text("• $w", style = MaterialTheme.typography.bodySmall, color = JarvisGold)
                                }
                            }
                        }

                        shieldState.latestAlertMessage?.let { alertMsg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = alertMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisAlertRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onRunDeepScan,
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisNavy),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("scan_installed_apps_button")
                            ) {
                                Text("Scan Phone Now", fontWeight = FontWeight.Bold)
                            }

                            if (highCount > 0 || report.cautionApps.isNotEmpty()) {
                                Button(
                                    onClick = onDeleteNextThreat,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (highCount > 0) JarvisAlertRed else JarvisGold,
                                        contentColor = JarvisNavy
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("security_delete_all_threats_button")
                                ) {
                                    Text(
                                        text = if (highCount > 0) "Delete Threats ($highCount)" else "Review Caution",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Network Isolation Mode ("ইন্টারনেট থেকে সরে আসা")
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                    border = BorderStroke(
                        1.dp,
                        if (offlineIsolation) JarvisGold else JarvisBorder
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AirplanemodeActive,
                                    contentDescription = null,
                                    tint = if (offlineIsolation) JarvisGold else JarvisCyan
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Offline Isolation Mode",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = JarvisTextPrimary
                                    )
                                    Text(
                                        text = if (offlineIsolation) {
                                            "Active: outbound web checks paused"
                                        } else {
                                            "Tap to isolate from network checks"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = JarvisTextMuted
                                    )
                                }
                            }
                            Switch(
                                checked = offlineIsolation,
                                onCheckedChange = { offlineIsolation = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = JarvisNavy,
                                    checkedTrackColor = JarvisGold
                                ),
                                modifier = Modifier.testTag("offline_isolation_switch")
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                            },
                            border = BorderStroke(1.dp, JarvisBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_wireless_settings_button")
                        ) {
                            Text("System Airplane & Network Controls", color = JarvisCyan)
                        }
                    }
                }
            }

            // Scam, Spam, Phishing Link & Fake Alert Analyzer (Masterpiece Section 4)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                    border = BorderStroke(1.dp, JarvisBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Scam, Spam & Phishing Alert Analyzer",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Paste any suspicious SMS, WhatsApp message, call claim, or bank/Airbnb alert link for instant fraud & phishing detection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = scamInput,
                            onValueChange = { scamInput = it },
                            label = { Text("Paste suspicious SMS, message, or alert…") },
                            modifier = Modifier.fillMaxWidth().testTag("security_scam_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { scamReport = JarvisSecurityEngine.analyzeMessageOrAlert(scamInput) },
                            enabled = scamInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("security_scam_analyze_button")
                        ) {
                            Text("Analyze for Scam / Phishing", fontWeight = FontWeight.Bold)
                        }
                        scamReport?.let { rep ->
                            val c = colorForThreat(rep.riskLevel)
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = JarvisSurfaceElevated,
                                border = BorderStroke(1.dp, c),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "${rep.riskLevel.name} (Risk Score ${rep.score}/100)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = c
                                    )
                                    rep.flags.forEach { flag ->
                                        Text("• $flag", style = MaterialTheme.typography.bodySmall, color = JarvisTextPrimary)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(rep.recommendation, style = MaterialTheme.typography.bodyMedium, color = JarvisCyan)
                                }
                            }
                        }
                    }
                }
            }

            // Cybersecurity Expert SOC, OWASP, SAST & Cryptography Suite
            item {
                NetworkAttackSurfaceCard()
            }
            item {
                OwaspPostureAuditorCard(offlineIsolation = offlineIsolation)
            }
            item {
                SecretAndSastScannerCard()
            }
            item {
                CryptoHashAndKeyLabCard()
            }

            // Website & Payload Decoder ("বিভিন্ন ওয়েবসাইট ডিকোড")
            item {
                WebsiteDecoderCard(offlineIsolation = offlineIsolation)
            }
            item {
                UniversalPayloadDecoderCard()
            }

            // Website Safety Checker & Safe Browser Launcher
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                    border = BorderStroke(1.dp, JarvisBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Language, contentDescription = null, tint = JarvisCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Website Safety Checker & Browser",
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Verifies TLS/HTTPS, lookalike Punycode, and redirects before opening in your browser.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextMuted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("https://example.com") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("security_url_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                urlError = null
                                scope.launch {
                                    runCatching {
                                        JarvisSecurityEngine.inspectWebsite(urlInput, offlineIsolation)
                                    }.onSuccess { report ->
                                        urlReport = report
                                    }.onFailure {
                                        urlReport = null
                                        urlError = "Invalid website URL."
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisCyan,
                                contentColor = JarvisNavy
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("security_url_check_button")
                        ) {
                            Text("Inspect Website", fontWeight = FontWeight.Bold)
                        }

                        urlError?.let { err ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = err, color = JarvisAlertRed, style = MaterialTheme.typography.bodyMedium)
                        }

                        urlReport?.let { report ->
                            val color = colorForThreat(report.riskLevel)
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = JarvisSurfaceElevated,
                                border = BorderStroke(1.dp, color),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "${report.riskLevel.name} • ${report.host}" +
                                            (report.httpStatusCode?.let { " (HTTP $it)" } ?: ""),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = color
                                    )
                                    if (report.findings.isEmpty()) {
                                        Text(
                                            text = "HTTPS verified with no local phishing flags.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = JarvisEmerald
                                        )
                                    } else {
                                        report.findings.forEach { f ->
                                            Text(
                                                text = "• $f",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = JarvisTextPrimary
                                            )
                                        }
                                    }
                                    if (!offlineIsolation && report.safeToOpen) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = {
                                                context.startActivity(
                                                    JarvisSecurityEngine.createBrowserIntent(report.normalizedUrl)
                                                )
                                            },
                                            border = BorderStroke(1.dp, JarvisCyan),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                                .testTag("open_verified_browser_button")
                                        ) {
                                            Icon(Icons.Filled.OpenInBrowser, contentDescription = null, tint = JarvisCyan)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Open in Browser", color = JarvisCyan)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Live Installed-App Threat Audit & One-Tap OS Deletion
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                    border = BorderStroke(1.dp, JarvisBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = JarvisCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Installed App Threat & Virus Scanner (${shieldState.report.allAudits.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Continuously scores APK origins, SHA-256 certificates, SMS/OTP hijackers, overlay phishing, and stalkerware. Tap Delete to remove threats via Android.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextMuted
                        )
                    }
                }
            }

            if (shieldState.report.allAudits.isEmpty()) {
                item {
                    Text(
                        text = if (shieldState.isScanningNow) "Scanning installed apps…" else "No third-party user apps flagged on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisEmerald
                    )
                }
            } else {
                items(shieldState.report.allAudits, key = { it.packageName }) { audit ->
                    val color = colorForThreat(audit.riskLevel)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = audit.appName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = JarvisTextPrimary
                                    )
                                    Text(
                                        text = audit.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisTextMuted
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (audit.isTrustedWhitelisted) "TRUSTED" else audit.riskLevel.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = color
                                    )
                                    Text(
                                        text = "Risk ${audit.threatScore}/100",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisTextMuted
                                    )
                                }
                            }
                            audit.sha256Signature?.let { sha ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "APK Cert SHA-256: $sha",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisTextMuted
                                )
                            }
                            if (audit.requestsCamera || audit.requestsMicrophone) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val sensors = listOfNotNull(
                                    if (audit.requestsCamera) "Camera" else null,
                                    if (audit.requestsMicrophone) "Microphone" else null
                                ).joinToString(" + ")
                                Text(
                                    text = "Privacy Guard: App declares $sensors permission",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisGold
                                )
                            }
                            if (audit.riskFlags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                audit.riskFlags.forEach { flag ->
                                    Text(
                                        text = "• $flag",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = JarvisTextMuted
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(
                                            JarvisSecurityEngine.createAppDetailsIntent(audit.packageName)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Text("Inspect", color = JarvisCyan)
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (audit.isTrustedWhitelisted) {
                                            onUntrustPackage(audit.packageName)
                                        } else {
                                            onTrustPackage(audit.packageName)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Text(
                                        text = if (audit.isTrustedWhitelisted) "Untrust" else "Trust",
                                        color = JarvisGold
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onDeleteThreat(audit.packageName) },
                                    border = BorderStroke(1.dp, JarvisAlertRed),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("delete_app_${audit.packageName}")
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete app",
                                        tint = JarvisAlertRed
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete", color = JarvisAlertRed)
                                }
                            }
                        }
                    }
                }
            }

            // Password Strength Analyzer
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                    border = BorderStroke(1.dp, JarvisBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = JarvisCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Local Password Analyzer",
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Enter password to check strength") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("security_password_input")
                        )
                        if (password.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val (label, color) = remember(password) { evaluatePassword(password) }
                            Text(
                                text = "$label • Length ${password.length}",
                                style = MaterialTheme.typography.labelMedium,
                                color = color
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun colorForThreat(level: ThreatLevel): Color {
    return when (level) {
        ThreatLevel.SAFE -> JarvisEmerald
        ThreatLevel.CAUTION -> JarvisGold
        ThreatLevel.HIGH_RISK -> JarvisAlertRed
    }
}

private fun evaluatePassword(pw: String): Pair<String, Color> {
    var score = 0
    if (pw.length >= 8) score++
    if (pw.length >= 12) score++
    if (pw.any { it.isLowerCase() } && pw.any { it.isUpperCase() }) score++
    if (pw.any { it.isDigit() }) score++
    if (pw.any { !it.isLetterOrDigit() }) score++

    return when {
        score <= 1 -> "Weak" to JarvisAlertRed
        score <= 3 -> "Moderate" to JarvisGold
        score == 4 -> "Strong" to JarvisCyan
        else -> "Very Strong" to JarvisEmerald
    }
}
