package com.example.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlin.math.log2

data class NetworkAttackSurfaceReport(
    val transportSummary: String,
    val vpnActive: Boolean,
    val privateDnsActive: Boolean,
    val privateDnsServer: String?,
    val dnsServers: List<String>,
    val proxyConfigured: String?,
    val integrity: DeviceSecurityIntegrity,
    val hardeningScore: Int,
    val grade: String,
    val findings: List<String>,
    val recommendations: List<String>
)

data class OwaspDomainPostureReport(
    val normalizedUrl: String,
    val host: String,
    val resolvedIps: List<String>,
    val httpStatusCode: Int?,
    val tlsProtocolInfo: String,
    val certificateExpiryDays: Long?,
    val passedChecks: List<String>,
    val missingHeaders: List<String>,
    val warnings: List<String>,
    val score: Int,
    val grade: String
)

data class SecretAndSastFinding(
    val category: String,
    val severity: ThreatLevel,
    val description: String,
    val maskedPreview: String
)

data class SecretAndSastReport(
    val findings: List<SecretAndSastFinding>,
    val overallRisk: ThreatLevel,
    val summary: String
)

data class CryptoHashBundle(
    val inputLength: Int,
    val md5: String,
    val sha1: String,
    val sha256: String,
    val sha512: String
)

data class GeneratedPasswordReport(
    val password: String,
    val length: Int,
    val entropyBits: Int,
    val strengthLabel: String
)

/**
 * White-Hat Defensive Cybersecurity Expert Engine for J.A.R.V.I.S.
 * Provides SOC-grade defensive inspection:
 * - Network & Device Attack Surface Audit (VPN, Private DNS/DoT, HTTP Proxy MITM check, ADB, Lock, Patch)
 * - OWASP Security Header & TLS Posture Grader (A+ to F) + DNS Resolution
 * - Offline Secret Leak & SAST Code Vulnerability Scanner
 * - Cryptographic Hash Lab (MD5, SHA-1, SHA-256, SHA-512) & SecureRandom Password/Entropy Generator
 */
object CyberSecurityExpertEngine {

    private val secureRandom = SecureRandom()

    fun runNetworkAndDeviceHardeningAudit(context: Context): NetworkAttackSurfaceReport {
        val integrity = JarvisSecurityEngine.auditDeviceIntegrity(context)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val linkProps = activeNetwork?.let { cm.getLinkProperties(it) }

        val transport = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Offline / Local"
        }

        val vpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val privateDnsActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            linkProps?.isPrivateDnsActive == true
        } else {
            false
        }
        val privateDnsServer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            linkProps?.privateDnsServerName
        } else {
            null
        }
        val dnsServers = linkProps?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
        val proxyInfo = linkProps?.httpProxy
        val proxyStr = proxyInfo?.host?.takeIf { it.isNotBlank() }?.let { "$it:${proxyInfo.port}" }

        val findings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        var score = integrity.integrityScore

        findings.addAll(integrity.warnings)
        if (!integrity.deviceLockEnabled) {
            recommendations.add("Enable a screen lock PIN, pattern, or biometric authentication.")
        }
        if (integrity.adbDebuggingEnabled) {
            recommendations.add("Disable USB Debugging (ADB) in Developer Options when not actively debugging.")
        }

        if (proxyStr != null) {
            findings.add("Active HTTP proxy configured ($proxyStr) — inspect for unexpected traffic interception")
            recommendations.add("Verify HTTP proxy '$proxyStr' in Wi-Fi settings if you did not configure it.")
            score -= 18
        }

        if (caps != null && !vpnActive && transport == "Wi-Fi") {
            findings.add("Connected to Wi-Fi without an active VPN tunnel")
            recommendations.add("Use a trusted WireGuard/VPN on public Wi-Fi networks.")
            score -= 8
        }

        if (caps != null && !privateDnsActive) {
            findings.add("Private DNS (DNS-over-TLS) is not explicitly active")
            recommendations.add("Enable Private DNS (e.g. dns.google or one.one.one.one) in Android Network Settings.")
            score -= 6
        } else if (privateDnsActive) {
            findings.add("Encrypted DNS-over-TLS active${privateDnsServer?.let { " ($it)" } ?: ""}")
        }

        if (dnsServers.isNotEmpty()) {
            findings.add("Active DNS resolvers: ${dnsServers.joinToString(", ")}")
        }

        val clamped = score.coerceIn(5, 100)
        val grade = scoreToGrade(clamped)
        if (recommendations.isEmpty()) {
            recommendations.add("Device network & OS posture looks solid. Keep 24/7 Threat Shield active.")
        }

        return NetworkAttackSurfaceReport(
            transportSummary = transport,
            vpnActive = vpnActive,
            privateDnsActive = privateDnsActive,
            privateDnsServer = privateDnsServer,
            dnsServers = dnsServers,
            proxyConfigured = proxyStr,
            integrity = integrity,
            hardeningScore = clamped,
            grade = grade,
            findings = findings,
            recommendations = recommendations
        )
    }

    suspend fun auditOwaspPosture(
        rawUrl: String,
        offlineIsolated: Boolean
    ): OwaspDomainPostureReport = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()
        require(trimmed.isNotBlank()) { "Domain or URL cannot be blank." }
        val normalized = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val parsed = URL(normalized)
        val host = parsed.host.lowercase()

        val passed = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var score = 100

        if (!parsed.protocol.equals("https", true)) {
            warnings.add("Protocol is unencrypted HTTP — vulnerable to eavesdropping & MITM.")
            score -= 40
        } else {
            passed.add("HTTPS transport enforced")
        }

        if (host.contains("xn--")) {
            warnings.add("Punycode/IDN homograph domain detected ($host).")
            score -= 25
        }

        if (offlineIsolated) {
            warnings.add("Offline Isolation Mode active: DNS, TLS handshake, and live header checks paused.")
            return@withContext OwaspDomainPostureReport(
                normalizedUrl = normalized,
                host = host,
                resolvedIps = emptyList(),
                httpStatusCode = null,
                tlsProtocolInfo = "Skipped (Offline Isolation Mode)",
                certificateExpiryDays = null,
                passedChecks = passed,
                missingHeaders = listOf("Live header inspection paused while Offline Isolation is ON"),
                warnings = warnings,
                score = score.coerceIn(0, 100),
                grade = scoreToGrade(score)
            )
        }

        val ips = runCatching {
            InetAddress.getAllByName(host).mapNotNull { it.hostAddress }.distinct()
        }.getOrDefault(emptyList())
        if (ips.isEmpty()) {
            warnings.add("DNS resolution returned no addresses for $host.")
            score -= 15
        }

        var statusCode: Int? = null
        var tlsInfo = "Not encrypted (HTTP)"
        var expiryDays: Long? = null

        val headerNames = listOf(
            "Strict-Transport-Security" to 14,
            "Content-Security-Policy" to 16,
            "X-Frame-Options" to 10,
            "X-Content-Type-Options" to 8,
            "Referrer-Policy" to 6,
            "Permissions-Policy" to 6
        )

        try {
            val conn = parsed.openConnection().apply {
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (conn is HttpsURLConnection) {
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "JARVIS-SOC-Auditor/1.0")
                conn.connect()
                statusCode = conn.responseCode

                val cipher = runCatching { conn.cipherSuite }.getOrNull() ?: "Verified TLS"
                val certs = runCatching { conn.serverCertificates }.getOrNull()
                val leaf = certs?.firstOrNull() as? X509Certificate
                if (leaf != null) {
                    val diffMs = leaf.notAfter.time - Date().time
                    expiryDays = TimeUnit.MILLISECONDS.toDays(diffMs)
                    tlsInfo = "Cipher: $cipher • Issuer: ${leaf.issuerX500Principal.name.take(55)}"
                    if ((expiryDays ?: 0L) < 14L) {
                        warnings.add("TLS certificate expires in ${expiryDays ?: 0} day(s) — renew immediately.")
                        score -= 15
                    } else {
                        passed.add("TLS certificate valid (${expiryDays} days remaining)")
                    }
                } else {
                    tlsInfo = "Cipher: $cipher"
                }

                for ((header, weight) in headerNames) {
                    val value = conn.getHeaderField(header)
                    if (!value.isNullOrBlank()) {
                        passed.add("$header: ${value.take(64)}")
                    } else {
                        missing.add("$header missing (-$weight pts)")
                        score -= weight
                    }
                }

                val cors = conn.getHeaderField("Access-Control-Allow-Origin")
                if (cors == "*") {
                    warnings.add("CORS wildcard '*' detected (Access-Control-Allow-Origin: *).")
                    score -= 6
                }
                val serverBanner = conn.getHeaderField("Server")
                if (!serverBanner.isNullOrBlank() && Regex("\\d+\\.\\d+").containsMatchIn(serverBanner)) {
                    warnings.add("Server header exposes version fingerprint: $serverBanner")
                    score -= 4
                }
                conn.disconnect()
            } else {
                val httpConn = conn as java.net.HttpURLConnection
                httpConn.requestMethod = "HEAD"
                httpConn.connect()
                statusCode = httpConn.responseCode
                httpConn.disconnect()
                missing.addAll(headerNames.map { "${it.first} missing" })
                score -= 35
            }
        } catch (e: Exception) {
            warnings.add("Remote audit failed: ${e.javaClass.simpleName} (${e.localizedMessage ?: "unreachable"})")
            score -= 20
        }

        val clamped = score.coerceIn(5, 100)
        OwaspDomainPostureReport(
            normalizedUrl = normalized,
            host = host,
            resolvedIps = ips,
            httpStatusCode = statusCode,
            tlsProtocolInfo = tlsInfo,
            certificateExpiryDays = expiryDays,
            passedChecks = passed,
            missingHeaders = missing,
            warnings = warnings,
            score = clamped,
            grade = scoreToGrade(clamped)
        )
    }

    fun scanSecretsAndVulnerabilities(rawInput: String): SecretAndSastReport {
        val text = rawInput.trim()
        if (text.isEmpty()) {
            return SecretAndSastReport(
                findings = emptyList(),
                overallRisk = ThreatLevel.SAFE,
                summary = "Paste source code, .env configs, logs, or API snippets to scan offline."
            )
        }

        val findings = mutableListOf<SecretAndSastFinding>()

        fun addMatch(
            regex: Regex,
            category: String,
            severity: ThreatLevel,
            description: String
        ) {
            regex.findAll(text).take(3).forEach { match ->
                val raw = match.value
                val masked = if (raw.length > 10) {
                    "${raw.take(4)}••••${raw.takeLast(4)}"
                } else {
                    "••••"
                }
                findings.add(SecretAndSastFinding(category, severity, description, masked))
            }
        }

        // Exposed Secrets & Keys
        addMatch(Regex("AIza[0-9A-Za-z\\-_]{30,}"), "Exposed Google/Firebase Key", ThreatLevel.HIGH_RISK, "Revoke and move key to server-side secrets manager.")
        addMatch(Regex("AKIA[0-9A-Z]{16}"), "Exposed AWS Access Key ID", ThreatLevel.HIGH_RISK, "Rotate AWS IAM credential immediately.")
        addMatch(Regex("gh[pousr]_[A-Za-z0-9]{20,}"), "Exposed GitHub Token", ThreatLevel.HIGH_RISK, "Revoke Personal Access Token on GitHub.")
        addMatch(Regex("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"), "Exposed Private Key PEM", ThreatLevel.HIGH_RISK, "Never commit private cryptographic keys in code.")
        addMatch(Regex("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"), "Embedded JWT Bearer Token", ThreatLevel.CAUTION, "Avoid hardcoding JWT tokens in client source.")
        addMatch(Regex("(?i)(?:password|passwd|secret|api_key|apikey|token)\\s*[:=]\\s*[\"'][^\"']{4,}[\"']"), "Hardcoded Secret Assignment", ThreatLevel.HIGH_RISK, "Move credential out of source code into BuildConfig/env.")

        // Static Code Vulnerability Patterns (SAST Lite)
        if (Regex("(?i)(?:select|insert|update|delete)\\s+.+?(?:\\+|\\$\\{|%s|\\{\\})").containsMatchIn(text)) {
            findings.add(
                SecretAndSastFinding(
                    category = "Potential SQL Injection",
                    severity = ThreatLevel.HIGH_RISK,
                    description = "String concatenation/interpolation inside SQL query. Use parameterized prepared statements.",
                    maskedPreview = "SQL string concatenation"
                )
            )
        }
        if (Regex("(?i)(\\.innerHTML\\s*=|dangerouslySetInnerHTML|document\\.write\\(|\\beval\\s*\\()").containsMatchIn(text)) {
            findings.add(
                SecretAndSastFinding(
                    category = "Potential XSS / Code Eval Sink",
                    severity = ThreatLevel.HIGH_RISK,
                    description = "Unsafe DOM/eval execution sink. Use safe framework bindings or sanitize inputs.",
                    maskedPreview = "DOM/eval sink"
                )
            )
        }
        if (Regex("(?i)(Runtime\\.getRuntime\\(\\)\\.exec|ProcessBuilder|os\\.system|child_process\\.exec)").containsMatchIn(text)) {
            findings.add(
                SecretAndSastFinding(
                    category = "Command Execution Sink",
                    severity = ThreatLevel.CAUTION,
                    description = "Validate and allowlist arguments passed to OS command runners.",
                    maskedPreview = "Shell exec call"
                )
            )
        }
        if (Regex("(?i)(MessageDigest\\.getInstance\\(\\s*[\"']MD5[\"']\\)|AES/ECB|DES/|InsecureTrustManager)").containsMatchIn(text)) {
            findings.add(
                SecretAndSastFinding(
                    category = "Weak Cryptography",
                    severity = ThreatLevel.CAUTION,
                    description = "Replace MD5/DES/AES-ECB with SHA-256/Argon2 and AES-GCM.",
                    maskedPreview = "Weak cipher/hash"
                )
            )
        }
        if (Regex("http://(?!localhost|127\\.0\\.0\\.1|10\\.0\\.2\\.2)[a-zA-Z0-9.-]+").containsMatchIn(text)) {
            findings.add(
                SecretAndSastFinding(
                    category = "Cleartext HTTP Endpoint",
                    severity = ThreatLevel.CAUTION,
                    description = "Upgrade plain http:// URLs to https:// to prevent traffic tampering.",
                    maskedPreview = "http://..."
                )
            )
        }

        val overall = when {
            findings.any { it.severity == ThreatLevel.HIGH_RISK } -> ThreatLevel.HIGH_RISK
            findings.isNotEmpty() -> ThreatLevel.CAUTION
            else -> ThreatLevel.SAFE
        }
        val summary = when (overall) {
            ThreatLevel.HIGH_RISK -> "CRITICAL: ${findings.size} secret/vulnerability finding(s) detected. Remediate before shipping."
            ThreatLevel.CAUTION -> "CAUTION: ${findings.size} hardening recommendation(s) found."
            ThreatLevel.SAFE -> "Clean: No exposed API keys, private keys, SQLi/XSS sinks, or weak crypto detected."
        }
        return SecretAndSastReport(findings = findings, overallRisk = overall, summary = summary)
    }

    fun computeHashes(input: String): CryptoHashBundle {
        val bytes = input.toByteArray(StandardCharsets.UTF_8)
        return CryptoHashBundle(
            inputLength = input.length,
            md5 = digestHex("MD5", bytes),
            sha1 = digestHex("SHA-1", bytes),
            sha256 = digestHex("SHA-256", bytes),
            sha512 = digestHex("SHA-512", bytes)
        )
    }

    fun generateCryptographicPassword(length: Int = 20): GeneratedPasswordReport {
        val targetLen = length.coerceIn(12, 64)
        val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val lower = "abcdefghijkmnopqrstuvwxyz"
        val digits = "23456789"
        val symbols = "!@#\$%^&*()-_=+[]{}"
        val all = upper + lower + digits + symbols

        val chars = ArrayList<Char>(targetLen)
        chars.add(upper[secureRandom.nextInt(upper.length)])
        chars.add(lower[secureRandom.nextInt(lower.length)])
        chars.add(digits[secureRandom.nextInt(digits.length)])
        chars.add(symbols[secureRandom.nextInt(symbols.length)])
        while (chars.size < targetLen) {
            chars.add(all[secureRandom.nextInt(all.length)])
        }
        // Fisher-Yates shuffle via SecureRandom
        for (i in chars.lastIndex downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        val pw = String(chars.toCharArray())
        return evaluatePasswordEntropy(pw)
    }

    fun evaluatePasswordEntropy(pw: String): GeneratedPasswordReport {
        var pool = 0
        if (pw.any { it.isLowerCase() }) pool += 26
        if (pw.any { it.isUpperCase() }) pool += 26
        if (pw.any { it.isDigit() }) pool += 10
        if (pw.any { !it.isLetterOrDigit() }) pool += 32
        val entropy = if (pw.isEmpty() || pool == 0) 0 else (pw.length * log2(pool.toDouble())).toInt()
        val label = when {
            entropy < 40 -> "Weak"
            entropy < 60 -> "Moderate"
            entropy < 85 -> "Strong"
            else -> "Military-Grade"
        }
        return GeneratedPasswordReport(
            password = pw,
            length = pw.length,
            entropyBits = entropy,
            strengthLabel = label
        )
    }

    private fun digestHex(algorithm: String, bytes: ByteArray): String {
        val digest = MessageDigest.getInstance(algorithm).digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun scoreToGrade(score: Int): String = when {
        score >= 95 -> "A+"
        score >= 85 -> "A"
        score >= 72 -> "B"
        score >= 58 -> "C"
        score >= 40 -> "D"
        else -> "F"
    }
}
