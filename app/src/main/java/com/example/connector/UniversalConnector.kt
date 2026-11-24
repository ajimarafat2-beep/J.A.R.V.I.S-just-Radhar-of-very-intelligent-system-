package com.example.connector

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.net.URLEncoder
import java.util.Locale

data class InstalledAppEntry(
    val appName: String,
    val packageName: String,
    val isPinned: Boolean = false
)

data class ConnectedWebsiteEntry(
    val title: String,
    val url: String
)

/**
 * Universal connector allowing Jarvis to discover and launch any installed app on the phone
 * and connect to any website or web service worldwide.
 */
class UniversalConnector(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun listInstalledApps(): List<InstalledAppEntry> {
        val pm = appContext.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val pinned = getPinnedPackages()
        val seen = HashSet<String>()
        return resolved.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (!seen.add(pkg) || pkg == appContext.packageName) return@mapNotNull null
            val label = info.loadLabel(pm).toString().trim()
            InstalledAppEntry(
                appName = label.ifEmpty { pkg },
                packageName = pkg,
                isPinned = pkg in pinned
            )
        }.sortedWith(
            compareByDescending<InstalledAppEntry> { it.isPinned }
                .thenBy { it.appName.lowercase(Locale.getDefault()) }
        )
    }

    fun launchApp(packageName: String): Result<String> {
        val pm = appContext.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
            ?: return Result.failure(IllegalStateException("App cannot be launched."))
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            Result.success(packageName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun togglePinApp(packageName: String): Set<String> {
        val current = getPinnedPackages().toMutableSet()
        if (!current.add(packageName)) {
            current.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_PINNED_APPS, current).apply()
        return current
    }

    fun getPinnedPackages(): Set<String> =
        prefs.getStringSet(KEY_PINNED_APPS, emptySet()).orEmpty()

    fun getConnectedWebsites(): List<ConnectedWebsiteEntry> {
        val savedRaw = prefs.getStringSet(KEY_CONNECTED_SITES, null)
        if (savedRaw == null) {
            return DEFAULT_WEBSITES
        }
        return savedRaw.mapNotNull { line ->
            val parts = line.split("|", limit = 2)
            if (parts.size == 2) ConnectedWebsiteEntry(parts[0], parts[1]) else null
        }.sortedBy { it.title.lowercase(Locale.getDefault()) }
    }

    fun saveWebsite(title: String, rawUrl: String): ConnectedWebsiteEntry {
        val normalized = normalizeUrl(rawUrl)
        val cleanTitle = title.trim().ifBlank {
            Uri.parse(normalized).host ?: normalized
        }
        val entry = ConnectedWebsiteEntry(cleanTitle, normalized)
        val existing = getConnectedWebsites().filterNot { it.url == normalized } + entry
        persistWebsites(existing)
        return entry
    }

    fun removeWebsite(url: String) {
        val updated = getConnectedWebsites().filterNot { it.url == url }
        persistWebsites(updated)
    }

    private fun persistWebsites(list: List<ConnectedWebsiteEntry>) {
        val encoded = list.map { "${it.title}|${it.url}" }.toSet()
        prefs.edit().putStringSet(KEY_CONNECTED_SITES, encoded).apply()
    }

    fun openWebsite(rawUrl: String): Result<String> {
        val normalized = normalizeUrl(rawUrl)
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            Result.success(normalized)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openWebSearch(query: String): Result<String> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        return openWebsite("https://www.google.com/search?q=$encoded")
    }

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    companion object {
        private const val PREFS_NAME = "jarvis_universal_connector"
        private const val KEY_PINNED_APPS = "pinned_apps"
        private const val KEY_CONNECTED_SITES = "connected_sites"

        private val DEFAULT_WEBSITES = listOf(
            ConnectedWebsiteEntry("Google Search", "https://www.google.com"),
            ConnectedWebsiteEntry("YouTube", "https://www.youtube.com"),
            ConnectedWebsiteEntry("GitHub", "https://github.com"),
            ConnectedWebsiteEntry("Wikipedia", "https://www.wikipedia.org")
        )
    }
}
