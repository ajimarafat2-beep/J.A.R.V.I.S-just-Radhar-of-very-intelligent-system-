package com.example.security

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

enum class ThreatLevel {
    SAFE,
    CAUTION,
    HIGH_RISK
}

data class WebsiteAuditReport(
    val normalizedUrl: String,
    val host: String,
    val isHttps: Boolean,
    val httpStatusCode: Int?,
    val riskLevel: ThreatLevel,
    val findings: List<String>
) {
    val safeToOpen: Boolean
        get() = riskLevel != ThreatLevel.HIGH_RISK
}

data class AppSecurityAudit(
    val packageName: String,
    val appName: String,
    val isSideloaded: Boolean,
    val riskFlags: List<String>,
    val riskLevel: ThreatLevel,
    val requestsCamera: Boolean = false,
    val requestsMicrophone: Boolean = false,
    val threatScore: Int = 0,
    val sha256Signature: String? = null,
    val isDebuggable: Boolean = false,
    val apkSizeMb: Double = 0.0,
    val isTrustedWhitelisted: Boolean = false
)

data class DeviceSecurityIntegrity(
    val deviceLockEnabled: Boolean = true,
    val adbDebuggingEnabled: Boolean = false,
    val securityPatchLevel: String = Build.VERSION.SECURITY_PATCH,
    val integrityScore: Int = 100,
    val warnings: List<String> = emptyList()
)

data class DeepDeviceScanReport(
    val scannedAtMillis: Long = System.currentTimeMillis(),
    val totalAppsScanned: Int = 0,
    val highRiskApps: List<AppSecurityAudit> = emptyList(),
    val cautionApps: List<AppSecurityAudit> = emptyList(),
    val safeApps: List<AppSecurityAudit> = emptyList(),
    val allAudits: List<AppSecurityAudit> = emptyList(),
    val integrity: DeviceSecurityIntegrity = DeviceSecurityIntegrity(),
    val overallScore: Int = 100
)

data class ScamAnalysisReport(
    val riskLevel: ThreatLevel,
    val score: Int,
    val extractedUrls: List<String>,
    val flags: List<String>,
    val recommendation: String
)

/**
 * White-Hat Defensive Security & Threat Shield Engine for J.A.R.V.I.S.
 * Performs continuous deep scanning of installed apps, APK signing certificates,
 * spyware/stalkerware/SMS-trojan heuristics, OS integrity checks, scam/phishing analysis,
 * and launches system-verified removal prompts for detected threats.
 */
object JarvisSecurityEngine {

    private const val PREFS_TRUST = "jarvis_security_shield_prefs"
    private const val KEY_TRUSTED_PKGS = "trusted_packages"
    private const val KEY_CONTINUOUS_SHIELD = "continuous_shield_enabled"

    private val highRiskTlds = setOf("zip", "mov", "tk", "ml", "ga", "cf", "gq", "top", "xyz")
    private val trustedStoreInstallers = setOf(
        "com.android.vending",
        "com.google.android.feedback",
        "com.sec.android.app.samsungapps"
    )
    private val suspiciousNamePatterns = Regex(
        "(trojan|spyware|stalker|keylog|stealer|hacktool|rat_|fake_update|free_nitro|mod_apk|cracked)",
        RegexOption.IGNORE_CASE
    )

    fun isContinuousShieldPreferred(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_TRUST, Context.MODE_PRIVATE)
            .getBoolean(KEY_CONTINUOUS_SHIELD, true)
    }

    fun setContinuousShieldPreferred(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_TRUST, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CONTINUOUS_SHIELD, enabled)
            .apply()
    }

    fun getTrustedPackages(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS_TRUST, Context.MODE_PRIVATE)
            .getStringSet(KEY_TRUSTED_PKGS, emptySet())
            ?.toSet()
            .orEmpty()
    }

    fun trustPackage(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_TRUST, Context.MODE_PRIVATE)
        val updated = getTrustedPackages(context).toMutableSet().apply { add(packageName) }
        prefs.edit().putStringSet(KEY_TRUSTED_PKGS, updated).apply()
    }

    fun untrustPackage(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_TRUST, Context.MODE_PRIVATE)
        val updated = getTrustedPackages(context).toMutableSet().apply { remove(packageName) }
        prefs.edit().putStringSet(KEY_TRUSTED_PKGS, updated).apply()
    }

    suspend fun runDeepDeviceScan(context: Context): DeepDeviceScanReport = withContext(Dispatchers.IO) {
        val audits = auditInstalledApps(context)
        val integrity = auditDeviceIntegrity(context)

        val highRisk = audits.filter { it.riskLevel == ThreatLevel.HIGH_RISK && !it.isTrustedWhitelisted }
        val caution = audits.filter { it.riskLevel == ThreatLevel.CAUTION && !it.isTrustedWhitelisted }
        val safe = audits.filter { it.riskLevel == ThreatLevel.SAFE || it.isTrustedWhitelisted }

        val deduction = (highRisk.size * 25) + (caution.size * 6) + (100 - integrity.integrityScore)
        val overall = (100 - deduction).coerceIn(5, 100)

        DeepDeviceScanReport(
            scannedAtMillis = System.currentTimeMillis(),
            totalAppsScanned = audits.size,
            highRiskApps = highRisk,
            cautionApps = caution,
            safeApps = safe,
            allAudits = audits,
            integrity = integrity,
            overallScore = overall
        )
    }

    fun inspectSinglePackage(context: Context, packageName: String): AppSecurityAudit? {
        if (packageName == context.packageName) return null
        val pm = context.packageManager
        val trusted = getTrustedPackages(context)
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return null
            val label = pm.getApplicationLabel(appInfo).toString()
            buildAppAudit(pm, packageName, label, appInfo, trusted)
        } catch (_: Exception) {
            null
        }
    }

    fun auditDeviceIntegrity(context: Context): DeviceSecurityIntegrity {
        val warnings = mutableListOf<String>()
        var score = 100

        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val locked = km?.isDeviceSecure == true
        if (!locked) {
            warnings.add("Screen lock PIN/Fingerprint is not configured")
            score -= 25
        }

        val adbOn = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        }.getOrDefault(false)
        if (adbOn) {
            warnings.add("USB Debugging (ADB) is currently enabled")
            score -= 10
        }

        val patch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "Unknown"
        return DeviceSecurityIntegrity(
            deviceLockEnabled = locked,
            adbDebuggingEnabled = adbOn,
            securityPatchLevel = patch,
            integrityScore = score.coerceIn(0, 100),
            warnings = warnings
        )
    }

    fun auditInstalledApps(context: Context): List<AppSecurityAudit> {
        val pm = context.packageManager
        val trusted = getTrustedPackages(context)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val seenPackages = HashSet<String>()
        val audits = mutableListOf<AppSecurityAudit>()

        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (!seenPackages.add(pkg) || pkg == context.packageName) continue

            val appInfo = info.activityInfo.applicationInfo
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystemApp) continue

            val label = info.loadLabel(pm).toString()
            audits.add(buildAppAudit(pm, pkg, label, appInfo, trusted))
        }

        return audits.sortedWith(
            compareByDescending<AppSecurityAudit> { if (it.isTrustedWhitelisted) -1 else it.riskLevel.ordinal }
                .thenByDescending { it.threatScore }
        )
    }

    private fun buildAppAudit(
        pm: PackageManager,
        pkg: String,
        label: String,
        appInfo: ApplicationInfo,
        trustedPackages: Set<String>
    ): AppSecurityAudit {
        val installer = getInstallerPackage(pm, pkg)
        val sideloaded = installer == null || installer !in trustedStoreInstallers
        val isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val apkSizeMb = runCatching {
            val f = File(appInfo.sourceDir)
            if (f.exists()) f.length() / (1024.0 * 1024.0) else 0.0
        }.getOrDefault(0.0)

        var perms: Set<String> = emptySet()
        var sha256: String? = null
        try {
            val flags = PackageManager.GET_PERMISSIONS or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
                else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, flags)
            }
            perms = pkgInfo.requestedPermissions?.toSet().orEmpty()
            sha256 = extractCertSha256(pkgInfo)
        } catch (_: Exception) {
        }

        val eval = evaluateThreatScore(
            packageName = pkg,
            appName = label,
            isSideloaded = sideloaded,
            isDebuggable = isDebuggable,
            permissions = perms
        )

        return AppSecurityAudit(
            packageName = pkg,
            appName = label,
            isSideloaded = sideloaded,
            riskFlags = eval.flags,
            riskLevel = if (pkg in trustedPackages) ThreatLevel.SAFE else eval.riskLevel,
            requestsCamera = Manifest.permission.CAMERA in perms,
            requestsMicrophone = Manifest.permission.RECORD_AUDIO in perms,
            threatScore = eval.score,
            sha256Signature = sha256,
            isDebuggable = isDebuggable,
            apkSizeMb = apkSizeMb,
            isTrustedWhitelisted = pkg in trustedPackages
        )
    }

    internal data class ThreatEvalResult(
        val score: Int,
        val riskLevel: ThreatLevel,
        val flags: List<String>
    )

    internal fun evaluateThreatScore(
        packageName: String,
        appName: String,
        isSideloaded: Boolean,
        isDebuggable: Boolean,
        permissions: Set<String>
    ): ThreatEvalResult {
        val flags = mutableListOf<String>()
        var score = 0

        if (isSideloaded) {
            flags.add("Sideloaded outside official Play/OEM store")
            score += 22
        }
        if (isDebuggable) {
            flags.add("APK built with debuggable flag enabled")
            score += 20
        }
        if (suspiciousNamePatterns.containsMatchIn(packageName) || suspiciousNamePatterns.containsMatchIn(appName)) {
            flags.add("Suspicious malware/crack naming pattern detected")
            score += 40
        }

        val hasSms = Manifest.permission.READ_SMS in permissions || Manifest.permission.RECEIVE_SMS in permissions
        if (hasSms) {
            flags.add("Requests SMS read/OTP intercept access")
            score += if (isSideloaded) 38 else 18
        }

        val hasOverlay = Manifest.permission.SYSTEM_ALERT_WINDOW in permissions
        if (hasOverlay) {
            flags.add("Requests screen overlay permission (phishing overlay vector)")
            score += if (isSideloaded) 28 else 12
        }

        val hasCam = Manifest.permission.CAMERA in permissions
        val hasMic = Manifest.permission.RECORD_AUDIO in permissions
        val hasLoc = Manifest.permission.ACCESS_FINE_LOCATION in permissions
        if (hasCam && hasMic && hasLoc && isSideloaded) {
            flags.add("Sideloaded stalkerware combo (Camera + Mic + Location)")
            score += 32
        } else if (hasCam && hasMic) {
            flags.add("Holds both Camera & Microphone access")
            score += 8
        }

        if (Manifest.permission.RECEIVE_BOOT_COMPLETED in permissions && isSideloaded) {
            flags.add("Sideloaded app auto-starts on device boot")
            score += 18
        }

        if (Manifest.permission.REQUEST_INSTALL_PACKAGES in permissions && isSideloaded) {
            flags.add("Sideloaded app can drop/install additional APKs")
            score += 30
        }

        if ((Manifest.permission.READ_CALL_LOG in permissions || Manifest.permission.READ_CONTACTS in permissions) &&
            Manifest.permission.INTERNET in permissions && isSideloaded
        ) {
            flags.add("Sideloaded app accesses contacts/call logs with Internet")
            score += 22
        }

        val clamped = score.coerceIn(0, 100)
        val level = when {
            clamped >= 55 -> ThreatLevel.HIGH_RISK
            clamped >= 22 -> ThreatLevel.CAUTION
            else -> ThreatLevel.SAFE
        }
        return ThreatEvalResult(clamped, level, flags)
    }

    private fun extractCertSha256(pkgInfo: PackageInfo): String? {
        return try {
            val sigBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.signatures?.firstOrNull()?.toByteArray()
            } ?: return null

            val digest = MessageDigest.getInstance("SHA-256").digest(sigBytes)
            digest.joinToString(":") { "%02X".format(it) }.take(35) + "…"
        } catch (_: Exception) {
            null
        }
    }

    fun analyzeMessageOrAlert(rawText: String): ScamAnalysisReport {
        val text = rawText.trim()
        val lower = text.lowercase()
        val flags = mutableListOf<String>()
        var score = 0

        val urls = Regex("https?://\\S+|www\\.\\S+", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { it.value.trimEnd('.', ',', ')', ']') }
            .distinct()
            .toList()

        if (Regex("(otp|pin|password|cvv|passcode|পিন|ওটিপি|পাসওয়ার্ড)").containsMatchIn(lower) &&
            Regex("(share|send|verify|confirm|urgent|suspended|blocked|দাও|পাঠাও|ব্লক)").containsMatchIn(lower)
        ) {
            flags.add("Requests sensitive OTP/PIN/Password with urgency")
            score += 45
        }
        if (Regex("(lottery|won prize|free money|reward claim|urgent action|account suspended|kyc update|লটারি|পুরস্কার)").containsMatchIn(lower)) {
            flags.add("Social engineering urgency / reward lure detected")
            score += 30
        }
        if (Regex("(bit\\.ly|tinyurl\\.com|t\\.co|rb\\.gy|shorturl|tiny\\.cc)").containsMatchIn(lower)) {
            flags.add("Obfuscated shortened URL detected")
            score += 25
        }
        urls.forEach { url ->
            if (url.startsWith("http://", ignoreCase = true)) {
                flags.add("Unencrypted HTTP link: $url")
                score += 25
            }
            val host = url.substringAfter("://").substringBefore("/").lowercase()
            val tld = host.substringAfterLast('.', "")
            if (tld in highRiskTlds || host.contains("xn--")) {
                flags.add("Suspicious link domain/TLD: $host")
                score += 35
            }
        }
        if (Regex("(bkash|nagad|bank|airbnb|paypal|amazon)").containsMatchIn(lower) &&
            urls.any { u -> !Regex("(bkash\\.com|nagad\\.com\\.bd|airbnb\\.com|paypal\\.com|amazon\\.com)", RegexOption.IGNORE_CASE).containsMatchIn(u) }
        ) {
            flags.add("Brand impersonation link mismatch (does not match official domain)")
            score += 40
        }

        val clamped = score.coerceIn(0, 100)
        val level = when {
            clamped >= 50 -> ThreatLevel.HIGH_RISK
            clamped >= 20 -> ThreatLevel.CAUTION
            else -> ThreatLevel.SAFE
        }
        val rec = when (level) {
            ThreatLevel.HIGH_RISK -> "Do NOT tap links or share OTP/PIN. Block sender and verify directly inside your official bank or Airbnb app."
            ThreatLevel.CAUTION -> "Proceed carefully. Verify the sender through official customer support before responding."
            ThreatLevel.SAFE -> "No obvious scam or phishing indicators detected locally."
        }
        return ScamAnalysisReport(
            riskLevel = level,
            score = clamped,
            extractedUrls = urls,
            flags = flags,
            recommendation = rec
        )
    }

    suspend fun inspectWebsite(
        rawUrl: String,
        isOfflineIsolated: Boolean
    ): WebsiteAuditReport = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()
        val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val parsed = URL(normalized)
        val host = parsed.host.lowercase()
        val isHttps = parsed.protocol.equals("https", ignoreCase = true)
        val findings = mutableListOf<String>()

        if (!isHttps) findings.add("Unencrypted HTTP connection (no TLS)")
        if (host.contains("xn--")) findings.add("Punycode domain detected (possible lookalike attack)")
        if (Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$").matches(host)) findings.add("Direct IPv4 host instead of verified domain")
        if (normalized.contains("@")) findings.add("Contains '@' credential redirect pattern")
        if (host.split(".").size > 4) findings.add("Unusually deep subdomain chain")
        val tld = host.substringAfterLast('.', "")
        if (tld in highRiskTlds) findings.add("High-risk TLD (.$tld)")

        var statusCode: Int? = null
        if (!isOfflineIsolated) {
            try {
                val conn = (parsed.openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 4500
                    readTimeout = 4500
                    instanceFollowRedirects = false
                }
                statusCode = conn.responseCode
                conn.disconnect()
                if (statusCode in 300..399) {
                    findings.add("Redirects externally (HTTP $statusCode)")
                }
            } catch (e: Exception) {
                findings.add("Reachability check failed: ${e.javaClass.simpleName}")
            }
        } else {
            findings.add("Offline Isolation Mode active: checked locally without contacting server")
        }

        val riskLevel = when {
            !isHttps || host.contains("xn--") || normalized.contains("@") ||
                Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$").matches(host) -> ThreatLevel.HIGH_RISK
            findings.any { !it.startsWith("Offline Isolation") } -> ThreatLevel.CAUTION
            else -> ThreatLevel.SAFE
        }

        WebsiteAuditReport(
            normalizedUrl = normalized,
            host = host,
            isHttps = isHttps,
            httpStatusCode = statusCode,
            riskLevel = riskLevel,
            findings = findings
        )
    }

    @Suppress("DEPRECATION")
    private fun getInstallerPackage(pm: PackageManager, pkg: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(pkg).installingPackageName
            } else {
                pm.getInstallerPackageName(pkg)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun createBrowserIntent(url: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createUninstallIntent(packageName: String): Intent {
        return Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createAppDetailsIntent(packageName: String): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
