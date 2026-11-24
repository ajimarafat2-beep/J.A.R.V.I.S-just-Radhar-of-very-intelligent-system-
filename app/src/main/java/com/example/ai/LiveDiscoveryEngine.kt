package com.example.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class LiveVerifiedLink(
    val sourceName: String,
    val url: String,
    val httpStatus: Int?,
    val reachable: Boolean,
    val pageTitle: String?,
    val extractedSnippet: String?
)

/**
 * Live web verification engine for App Discovery (Section 8.1) and Product & Price Finder (Section 8.2).
 * Queries live URLs via OkHttp and reports exact retrieved HTTP status, titles, and detected
 * prices rather than fabricating guessed prices or unverified links.
 */
object LiveDiscoveryEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun discoverAndVerifyApp(query: String): List<LiveVerifiedLink> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val candidates = listOf(
            "Google Play Store" to "https://play.google.com/store/search?q=$encoded&c=apps",
            "F-Droid Open Source" to "https://search.f-droid.org/?q=$encoded",
            "GitHub Repositories" to "https://github.com/search?q=$encoded+android+apk&type=repositories",
            "YouTube Reviews / Walkthroughs" to "https://www.youtube.com/results?search_query=$encoded+app+official+download"
        )
        candidates.map { (name, url) -> probeUrl(name, url, extractPrice = false) }
    }

    suspend fun findLivePrices(productQuery: String): List<LiveVerifiedLink> = withContext(Dispatchers.IO) {
        val clean = productQuery.trim()
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val stores = listOf(
            "Star Tech (BD)" to "https://www.startech.com.bd/product/search?search=$encoded",
            "Daraz (BD)" to "https://www.daraz.com.bd/catalog/?q=$encoded",
            "Amazon Global" to "https://www.amazon.com/s?k=$encoded",
            "Google Shopping" to "https://www.google.com/search?tbm=shop&q=$encoded"
        )
        stores.map { (name, url) -> probeUrl(name, url, extractPrice = true) }
    }

    private fun probeUrl(
        sourceName: String,
        url: String,
        extractPrice: Boolean
    ): LiveVerifiedLink {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                val code = resp.code
                val body = resp.body?.string()?.take(65_000).orEmpty()
                val title = TITLE_REGEX.find(body)?.groupValues?.getOrNull(1)
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                val snippet = if (extractPrice) extractPriceSnippets(body) else null
                LiveVerifiedLink(
                    sourceName = sourceName,
                    url = url,
                    httpStatus = code,
                    reachable = code in 200..399,
                    pageTitle = title,
                    extractedSnippet = snippet
                )
            }
        } catch (e: Exception) {
            LiveVerifiedLink(
                sourceName = sourceName,
                url = url,
                httpStatus = null,
                reachable = false,
                pageTitle = null,
                extractedSnippet = "Live check failed: ${e.javaClass.simpleName}"
            )
        }
    }

    internal fun extractPriceSnippets(html: String): String? {
        val plain = html.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
        val matches = PRICE_REGEX.findAll(plain)
            .map { it.value.trim() }
            .distinct()
            .take(5)
            .toList()
        return if (matches.isEmpty()) null else "Live prices spotted: ${matches.joinToString(" • ")}"
    }

    private val TITLE_REGEX = Regex("(?is)<title[^>]*>(.*?)</title>")
    private val PRICE_REGEX = Regex("(?:৳|BDT\\s?|USD\\s?|\\$|₹|INR\\s?|£|€)\\s?[0-9][0-9,]*(?:\\.[0-9]{1,2})?")
}
