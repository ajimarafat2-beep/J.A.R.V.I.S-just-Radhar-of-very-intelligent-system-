package com.example.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.connector.InstalledAppEntry
import com.example.connector.UniversalConnector
import java.util.Locale

/**
 * App Connector — Jarvis can discover, find and open any app in the world.
 * - Lists every installed app (via UniversalConnector).
 * - Opens any installed app by its spoken or typed name, in Bangla, Hindi or English,
 *   including partial names and common Bangla names.
 * - When an app is not installed, opens Play Store search so the user can find and install it.
 * Deep control inside an app is not possible without that app's own support — Jarvis opens
 * apps through their normal launch entry points.
 */
class AppConnector(
    private val context: Context,
    private val connector: UniversalConnector
) {

    fun listApps(): List<InstalledAppEntry> = connector.listInstalledApps()

    /**
     * Resolve a spoken or typed app name to the best installed match, or null.
     * Matching stages, in order: exact name, name starts with query, name contains query,
     * query contains name, package name contains query, all query words present in name.
     */
    fun resolveApp(queryRaw: String): InstalledAppEntry? {
        val query = normalizeQuery(queryRaw)
        if (query.isEmpty()) return null
        val apps = connector.listInstalledApps()
        if (apps.isEmpty()) return null
        val normalized = apps.map { app -> app to normalizeName(app.appName) }
        // 1. Exact name.
        normalized.firstOrNull { (_, name) -> name == query }?.let { return it.first }
        // 2. Name starts with the query.
        normalized.firstOrNull { (_, name) -> name.startsWith(query) }?.let { return it.first }
        // 3. Name contains the query.
        normalized.firstOrNull { (_, name) -> name.contains(query) }?.let { return it.first }
        // 4. Query contains the name (e.g. "open youtube music app" -> "youtube music").
        normalized.firstOrNull { (_, name) -> name.isNotEmpty() && query.contains(name) }?.let { return it.first }
        // 5. Package name contains the query (e.g. "whatsapp" -> com.whatsapp).
        normalized.firstOrNull { (app, _) ->
            app.packageName.lowercase(Locale.getDefault()).substringAfterLast('.').contains(query)
        }?.let { return it.first }
        // 6. Every query word appears in the name.
        val words = query.split(' ').filter { it.isNotEmpty() }
        if (words.size > 1) {
            normalized.firstOrNull { (_, name) -> words.all { name.contains(it) } }?.let { return it.first }
        }
        return null
    }

    /**
     * Strict app-name match: exact name or name starts with the query only.
     * Used for bare messages ("youtube") so a whole chat sentence can never
     * fuzzy-match an unrelated app ("hi" must not open TikTok).
     */
    fun resolveAppStrict(queryRaw: String): InstalledAppEntry? {
        val query = normalizeQuery(queryRaw)
        if (query.isEmpty()) return null
        val apps = connector.listInstalledApps()
        if (apps.isEmpty()) return null
        val normalized = apps.map { app -> app to normalizeName(app.appName) }
        normalized.firstOrNull { (_, name) -> name == query }?.let { return it.first }
        normalized.firstOrNull { (_, name) -> name.startsWith(query) }?.let { return it.first }
        return null
    }

    /** Open an installed app by name. Returns true when it launched. */
    fun openAppByName(query: String): Boolean {
        val match = resolveApp(query) ?: return false
        return connector.launchApp(match.packageName).isSuccess
    }

    /**
     * Open Play Store search for an app that is not installed, so the user can find it.
     * Returns true when a store view was opened.
     */
    fun openPlayStoreSearch(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        val encoded = Uri.encode(trimmed)
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(marketIntent)
            true
        } catch (e: Exception) {
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/search?q=$encoded&c=apps")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(webIntent)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    private fun normalizeName(name: String): String {
        return name.lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeQuery(queryRaw: String): String {
        var text = queryRaw.trim().lowercase(Locale.getDefault())
        for ((bangla, english) in BANGLA_APP_NAMES) {
            if (text.contains(bangla)) text = text.replace(bangla, english)
        }
        return normalizeName(text)
    }

    companion object {
        private val BANGLA_APP_NAMES = mapOf(
            "ইউটিউব" to "youtube",
            "হোয়াটসঅ্যাপ" to "whatsapp",
            "হোয়াটসঅ্যাপ" to "whatsapp",
            "ফেসবুক" to "facebook",
            "টেলিগ্রাম" to "telegram",
            "ইনস্টাগ্রাম" to "instagram",
            "মেসেঞ্জার" to "messenger",
            "জিমেইল" to "gmail",
            "গুগল" to "google",
            "ক্রোম" to "chrome",
            "ম্যাপ" to "maps",
            "গ্যালারি" to "gallery",
            "ক্যামেরা" to "camera",
            "সেটিংস" to "settings",
            "মেসেজ" to "messages",
            "ফোন" to "phone",
            "ঘড়ি" to "clock",
            "ঘড়ি" to "clock",
            "ক্যালকুলেটর" to "calculator",
            "ক্যালেন্ডার" to "calendar",
            "কন্টাক্ট" to "contacts",
            "ফটো" to "photos",
            "ড্রাইভ" to "drive",
            "মিউজিক" to "music",
            "প্লে স্টোর" to "play store"
        )
    }
}
