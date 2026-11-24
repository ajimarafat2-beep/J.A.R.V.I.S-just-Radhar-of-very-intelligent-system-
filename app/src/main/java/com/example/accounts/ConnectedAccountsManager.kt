package com.example.accounts

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.example.connector.InstalledAppEntry
import com.example.security.JarvisSecurityEngine
import com.example.security.ThreatLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

data class BankPortalEntry(
    val bankName: String,
    val url: String,
    val category: String = "Bank / Finance"
)

data class AirbnbReservation(
    val summary: String,
    val checkIn: String,
    val checkOut: String
)

/**
 * Provides protected, transparent access to Airbnb and Bank apps/portals:
 * - Launches official Airbnb app or verified HTTPS Airbnb sections (Trips, Hosting, Account).
 * - Syncs an optional user-provided Airbnb iCal (.ics) reservation feed live over HTTPS.
 * - Detects installed banking / mobile financial apps on the phone and opens verified HTTPS bank portals.
 * - Uses Android Keyguard / Device Lock status for vault unlock without storing raw credentials.
 */
class ConnectedAccountsManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _vaultUnlocked = MutableStateFlow(!isDeviceSecured())
    val vaultUnlocked: StateFlow<Boolean> = _vaultUnlocked.asStateFlow()

    private val _airbnbIcalUrl = MutableStateFlow(prefs.getString(KEY_AIRBNB_ICAL, "").orEmpty())
    val airbnbIcalUrl: StateFlow<String> = _airbnbIcalUrl.asStateFlow()

    private val _airbnbReservations = MutableStateFlow<List<AirbnbReservation>>(emptyList())
    val airbnbReservations: StateFlow<List<AirbnbReservation>> = _airbnbReservations.asStateFlow()

    private val _bankPortals = MutableStateFlow(loadBankPortals())
    val bankPortals: StateFlow<List<BankPortalEntry>> = _bankPortals.asStateFlow()

    fun isDeviceSecured(): Boolean {
        val km = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return km.isDeviceSecure
    }

    @Suppress("DEPRECATION")
    fun createUnlockIntent(): Intent? {
        val km = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return null
        if (!km.isDeviceSecure) return null
        return km.createConfirmDeviceCredentialIntent(
            "Unlock JARVIS Financial & Travel Vault",
            "Confirm your device lock to open Bank & Airbnb accounts"
        )
    }

    fun markVaultUnlocked(unlocked: Boolean) {
        _vaultUnlocked.value = unlocked
    }

    fun isAirbnbAppInstalled(): Boolean = isPackageInstalled(AIRBNB_PACKAGE)

    fun openAirbnb(sectionUrl: String = "https://www.airbnb.com/trips"): Result<String> {
        if (sectionUrl == "https://www.airbnb.com/trips" && isAirbnbAppInstalled()) {
            val appIntent = appContext.packageManager.getLaunchIntentForPackage(AIRBNB_PACKAGE)
            if (appIntent != null) {
                appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(appIntent)
                return Result.success("Launched official Airbnb app.")
            }
        }
        return openVerifiedUrl(sectionUrl)
    }

    fun saveAirbnbIcalUrl(url: String) {
        val clean = url.trim()
        prefs.edit().putString(KEY_AIRBNB_ICAL, clean).apply()
        _airbnbIcalUrl.value = clean
    }

    suspend fun syncAirbnbIcalFeed(): Result<List<AirbnbReservation>> = withContext(Dispatchers.IO) {
        val url = _airbnbIcalUrl.value.trim()
        if (url.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Paste your Airbnb iCal (.ics) feed URL first."))
        }
        if (!url.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(IllegalArgumentException("Only verified HTTPS iCal URLs are permitted."))
        }
        return@withContext try {
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Airbnb iCal fetch returned HTTP ${resp.code}."))
                }
                val ics = resp.body?.string().orEmpty()
                val parsed = parseIcalEvents(ics)
                _airbnbReservations.value = parsed
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun detectInstalledBankApps(allApps: List<InstalledAppEntry>): List<InstalledAppEntry> {
        return allApps.filter { app ->
            val nameLower = app.appName.lowercase(Locale.getDefault())
            val pkgLower = app.packageName.lowercase(Locale.getDefault())
            BANK_KEYWORDS.any { kw -> nameLower.contains(kw) || pkgLower.contains(kw) }
        }
    }

    suspend fun verifyAndSaveBankPortal(bankName: String, rawUrl: String): Result<BankPortalEntry> {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Bank URL cannot be empty."))
        val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        val audit = JarvisSecurityEngine.inspectWebsite(normalized, isOfflineIsolated = false)
        if (audit.riskLevel == ThreatLevel.HIGH_RISK) {
            return Result.failure(
                IllegalStateException("Blocked unsafe banking URL (${audit.host}): ${audit.findings.joinToString(", ")}")
            )
        }
        val cleanTitle = bankName.trim().ifBlank { audit.host }
        val entry = BankPortalEntry(cleanTitle, audit.normalizedUrl)
        val updated = (_bankPortals.value.filterNot { it.url == entry.url } + entry)
            .sortedBy { it.bankName.lowercase(Locale.getDefault()) }
        persistBankPortals(updated)
        _bankPortals.value = updated
        return Result.success(entry)
    }

    fun removeBankPortal(url: String) {
        val updated = _bankPortals.value.filterNot { it.url == url }
        persistBankPortals(updated)
        _bankPortals.value = updated
    }

    suspend fun verifyAndOpenPortal(rawUrl: String): Result<String> {
        val audit = JarvisSecurityEngine.inspectWebsite(rawUrl, isOfflineIsolated = false)
        if (audit.riskLevel == ThreatLevel.HIGH_RISK) {
            return Result.failure(
                IllegalStateException("Security Sentinel blocked high-risk link (${audit.host}): ${audit.findings.joinToString(", ")}")
            )
        }
        return openVerifiedUrl(audit.normalizedUrl)
    }

    private fun openVerifiedUrl(url: String): Result<String> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            Result.success("Opened $url securely.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(pkg, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun loadBankPortals(): List<BankPortalEntry> {
        val saved = prefs.getStringSet(KEY_BANK_PORTALS, null) ?: return DEFAULT_BANK_PORTALS
        return saved.mapNotNull { line ->
            val parts = line.split("|", limit = 2)
            if (parts.size == 2) BankPortalEntry(parts[0], parts[1]) else null
        }.sortedBy { it.bankName.lowercase(Locale.getDefault()) }
    }

    private fun persistBankPortals(list: List<BankPortalEntry>) {
        val encoded = list.map { "${it.bankName}|${it.url}" }.toSet()
        prefs.edit().putStringSet(KEY_BANK_PORTALS, encoded).apply()
    }

    companion object {
        private const val PREFS_NAME = "jarvis_connected_accounts"
        private const val KEY_AIRBNB_ICAL = "airbnb_ical_url"
        private const val KEY_BANK_PORTALS = "bank_portals"
        const val AIRBNB_PACKAGE = "com.airbnb.android"

        private val BANK_KEYWORDS = listOf(
            "bank", "bkash", "nagad", "rocket", "upay", "nexuspay", "citytouch",
            "astha", "cellfin", "ibbl", "brac", "ebl", "skybanking", "paypal",
            "wise", "revolut", "chase", "citi", "hsbc", "finance", "wallet", "pay"
        )

        private val DEFAULT_BANK_PORTALS = listOf(
            BankPortalEntry("bKash Portal", "https://www.bkash.com"),
            BankPortalEntry("BRAC Bank Astha", "https://www.bracbank.com"),
            BankPortalEntry("City Bank (Citytouch)", "https://www.thecitybank.com"),
            BankPortalEntry("Dutch-Bangla Bank", "https://www.dutchbanglabank.com"),
            BankPortalEntry("PayPal", "https://www.paypal.com")
        )

        internal fun parseIcalEvents(icsContent: String): List<AirbnbReservation> {
            val events = mutableListOf<AirbnbReservation>()
            val blocks = icsContent.split("BEGIN:VEVENT")
            for (block in blocks.drop(1)) {
                val endIdx = block.indexOf("END:VEVENT")
                val body = if (endIdx >= 0) block.substring(0, endIdx) else block
                var summary = "Airbnb Reservation"
                var dtStart = ""
                var dtEnd = ""
                body.lines().forEach { line ->
                    when {
                        line.startsWith("SUMMARY:") -> summary = line.removePrefix("SUMMARY:").trim()
                        line.startsWith("DTSTART") -> dtStart = line.substringAfter(':').trim()
                        line.startsWith("DTEND") -> dtEnd = line.substringAfter(':').trim()
                    }
                }
                if (dtStart.isNotBlank()) {
                    events.add(AirbnbReservation(summary, dtStart, dtEnd))
                }
            }
            return events
        }
    }
}
