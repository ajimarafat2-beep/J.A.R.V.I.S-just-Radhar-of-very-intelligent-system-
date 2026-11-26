package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.security.CryptoHashBundle
import com.example.security.CyberSecurityExpertEngine
import com.example.security.GeneratedPasswordReport
import com.example.security.NetworkAttackSurfaceReport
import com.example.security.OwaspDomainPostureReport
import com.example.security.SecretAndSastReport
import com.example.security.ThreatLevel
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NetworkAttackSurfaceCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<NetworkAttackSurfaceReport?>(null) }
    LaunchedEffect(context) {
        report = withContext(Dispatchers.IO) {
            CyberSecurityExpertEngine.runNetworkAndDeviceHardeningAudit(context)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, JarvisGold.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.VpnKey, contentDescription = null, tint = JarvisGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SOC Network & Attack Surface Audit",
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisTextPrimary
                    )
                }
                report?.let { r ->
                    Surface(
                        color = JarvisGold.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, JarvisGold),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "Grade ${r.grade} (${r.hardeningScore}%)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = JarvisGold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Inspects VPN tunnel, Private DNS (DoT), MITM proxy exposure, active DNS resolvers, ADB, and OS patch posture.",
                style = MaterialTheme.typography.bodySmall,
                color = JarvisTextMuted
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    scope.launch {
                        report = withContext(Dispatchers.IO) {
                            CyberSecurityExpertEngine.runNetworkAndDeviceHardeningAudit(context)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("cyber_network_audit_button")
            ) {
                Text("Run Live Attack Surface Audit", fontWeight = FontWeight.Bold)
            }

            report?.let { r ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.dp, JarvisBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Transport: ${r.transportSummary} • VPN: ${if (r.vpnActive) "ACTIVE" else "OFF"} • Private DNS: ${if (r.privateDnsActive) "ON" else "OFF"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = JarvisCyan
                        )
                        Text(
                            text = "HTTP Proxy: ${r.proxyConfigured ?: "None (Direct)"} • Patch: ${r.integrity.securityPatchLevel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextPrimary
                        )
                        r.findings.forEach { f ->
                            Text("• $f", style = MaterialTheme.typography.bodySmall, color = JarvisTextMuted)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        r.recommendations.forEach { rec ->
                            Text("→ $rec", style = MaterialTheme.typography.bodySmall, color = JarvisEmerald)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OwaspPostureAuditorCard(
    offlineIsolation: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var domainInput by remember { mutableStateOf("") }
    var isAuditing by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<OwaspDomainPostureReport?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, JarvisBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Http, contentDescription = null, tint = JarvisCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "OWASP Header, TLS & DNS Posture Grader",
                    style = MaterialTheme.typography.titleMedium,
                    color = JarvisTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Grades HSTS, CSP, X-Frame-Options, CORS, TLS cipher & certificate expiry, and DNS resolution (A+ to F).",
                style = MaterialTheme.typography.bodySmall,
                color = JarvisTextMuted
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = domainInput,
                onValueChange = { domainInput = it },
                label = { Text("https://example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("cyber_owasp_input")
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    errorMsg = null
                    isAuditing = true
                    scope.launch {
                        runCatching { CyberSecurityExpertEngine.auditOwaspPosture(domainInput, offlineIsolation) }
                            .onSuccess { report = it }
                            .onFailure {
                                report = null
                                errorMsg = "Enter a valid domain or URL."
                            }
                        isAuditing = false
                    }
                },
                enabled = !isAuditing && domainInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("cyber_owasp_button")
            ) {
                Text(if (isAuditing) "Auditing OWASP & TLS…" else "Grade OWASP Security Posture", fontWeight = FontWeight.Bold)
            }

            errorMsg?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = JarvisAlertRed, style = MaterialTheme.typography.bodySmall)
            }

            report?.let { rep ->
                val gradeColor = when {
                    rep.score >= 85 -> JarvisEmerald
                    rep.score >= 60 -> JarvisGold
                    else -> JarvisAlertRed
                }
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.dp, gradeColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${rep.host} • Grade ${rep.grade} (${rep.score}/100)" +
                                (rep.httpStatusCode?.let { " • HTTP $it" } ?: ""),
                            style = MaterialTheme.typography.labelLarge,
                            color = gradeColor
                        )
                        if (rep.resolvedIps.isNotEmpty()) {
                            Text("DNS IPs: ${rep.resolvedIps.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = JarvisCyan)
                        }
                        Text(rep.tlsProtocolInfo, style = MaterialTheme.typography.bodySmall, color = JarvisTextMuted)
                        rep.passedChecks.forEach { p ->
                            Text("✓ $p", style = MaterialTheme.typography.bodySmall, color = JarvisEmerald)
                        }
                        rep.missingHeaders.forEach { m ->
                            Text("✗ $m", style = MaterialTheme.typography.bodySmall, color = JarvisGold)
                        }
                        rep.warnings.forEach { w ->
                            Text("! $w", style = MaterialTheme.typography.bodySmall, color = JarvisAlertRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecretAndSastScannerCard(modifier: Modifier = Modifier) {
    var codeInput by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<SecretAndSastReport?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, JarvisBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BugReport, contentDescription = null, tint = JarvisGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Offline Secret Leak & Code SAST Scanner",
                    style = MaterialTheme.typography.titleMedium,
                    color = JarvisTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Detects leaked API keys, AWS/GitHub tokens, PEM private keys, SQL injection, XSS sinks, and weak crypto offline.",
                style = MaterialTheme.typography.bodySmall,
                color = JarvisTextMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = codeInput,
                onValueChange = { codeInput = it },
                label = { Text("Paste source code, .env, config, or log snippet…") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("cyber_sast_input")
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { report = CyberSecurityExpertEngine.scanSecretsAndVulnerabilities(codeInput) },
                enabled = codeInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("cyber_sast_button")
            ) {
                Text("Scan Secrets & Vulnerabilities Offline", fontWeight = FontWeight.Bold)
            }

            report?.let { rep ->
                val tint = when (rep.overallRisk) {
                    ThreatLevel.SAFE -> JarvisEmerald
                    ThreatLevel.CAUTION -> JarvisGold
                    ThreatLevel.HIGH_RISK -> JarvisAlertRed
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.dp, tint),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(rep.summary, style = MaterialTheme.typography.labelMedium, color = tint)
                        rep.findings.forEach { f ->
                            Text(
                                text = "• [${f.severity.name}] ${f.category} (${f.maskedPreview}): ${f.description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CryptoHashAndKeyLabCard(modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    var hashInput by remember { mutableStateOf("") }
    var hashBundle by remember { mutableStateOf<CryptoHashBundle?>(null) }
    var generatedPw by remember { mutableStateOf<GeneratedPasswordReport?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, JarvisBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = JarvisCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Offline Cryptography & Password Entropy Lab",
                    style = MaterialTheme.typography.titleMedium,
                    color = JarvisTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = hashInput,
                onValueChange = { hashInput = it },
                label = { Text("Enter text or checksum input to hash…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("cyber_hash_input")
            )
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { hashBundle = CyberSecurityExpertEngine.computeHashes(hashInput) },
                    enabled = hashInput.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(46.dp).testTag("cyber_compute_hash_button")
                ) {
                    Text("Compute SHA-256 / MD5", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { generatedPw = CyberSecurityExpertEngine.generateCryptographicPassword(20) },
                    border = BorderStroke(1.dp, JarvisGold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(46.dp).testTag("cyber_gen_password_button")
                ) {
                    Icon(Icons.Filled.Key, contentDescription = null, tint = JarvisGold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generate 20-Char Key", color = JarvisGold)
                }
            }

            hashBundle?.let { h ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.dp, JarvisCyan.copy(0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("SHA-256: ${h.sha256}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = JarvisEmerald)
                        Text("SHA-512: ${h.sha512.take(64)}…", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = JarvisCyan)
                        Text("SHA-1: ${h.sha1}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = JarvisTextMuted)
                        Text("MD5: ${h.md5}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = JarvisTextMuted)
                    }
                }
            }

            generatedPw?.let { pw ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.dp, JarvisGold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${pw.strengthLabel} • ${pw.entropyBits} bits entropy",
                                style = MaterialTheme.typography.labelMedium,
                                color = JarvisGold
                            )
                            Text(
                                text = pw.password,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = JarvisTextPrimary
                            )
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(pw.password)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy generated password", tint = JarvisGold)
                        }
                    }
                }
            }
        }
    }
}
