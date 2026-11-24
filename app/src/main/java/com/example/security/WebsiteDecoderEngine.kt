package com.example.security

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

data class WebsiteDecodedReport(
    val requestedUrl: String,
    val finalUrl: String,
    val scheme: String,
    val host: String,
    val port: Int,
    val decodedPath: String,
    val queryParameters: List<Pair<String, String>>,
    val httpStatusCode: Int?,
    val responseTimeMs: Long?,
    val pageTitle: String,
    val metaDescription: String,
    val detectedTechnologies: List<String>,
    val securityHeaders: List<Pair<String, String>>,
    val tlsCertificateSummary: String,
    val extractedLinks: List<String>,
    val scriptSources: List<String>,
    val decodedHtmlPreview: String,
    val notes: List<String>
)

data class PayloadDecodeResult(
    val detectedFormat: String,
    val decodedOutput: String,
    val details: List<String> = emptyList()
)

/**
 * White-Hat Website & Payload Decoder Engine.
 * - Decodes URL encoding, query parameters, TLS certificates, HTTP security headers,
 *   HTML metadata, detected web technologies, scripts, and outbound links.
 * - Decodes JWTs, Base64, Hexadecimal, URL-encoded strings, and HTML entities offline.
 */
object WebsiteDecoderEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun decodeWebsite(
        rawUrl: String,
        isOfflineIsolated: Boolean
    ): WebsiteDecodedReport = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()
        require(trimmed.isNotBlank()) { "Website URL cannot be blank." }
        val normalized = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val parsed = URL(normalized)
        val decodedPath = safeUrlDecode(parsed.path.ifBlank { "/" })
        val queryParams = parseDecodedQueryParams(parsed.query)
        val notes = mutableListOf<String>()

        if (isOfflineIsolated) {
            notes.add("Offline Isolation Mode active: decoded URL structure and parameters locally without network fetch.")
            return@withContext WebsiteDecodedReport(
                requestedUrl = normalized,
                finalUrl = normalized,
                scheme = parsed.protocol,
                host = parsed.host,
                port = if (parsed.port != -1) parsed.port else parsed.defaultPort,
                decodedPath = decodedPath,
                queryParameters = queryParams,
                httpStatusCode = null,
                responseTimeMs = null,
                pageTitle = "(Skipped in Offline Isolation)",
                metaDescription = "",
                detectedTechnologies = emptyList(),
                securityHeaders = emptyList(),
                tlsCertificateSummary = "Not fetched (Offline Isolation Mode)",
                extractedLinks = emptyList(),
                scriptSources = emptyList(),
                decodedHtmlPreview = "",
                notes = notes
            )
        }

        val startMs = System.currentTimeMillis()
        var statusCode: Int? = null
        var finalUrl = normalized
        var body = ""
        val secHeaders = mutableListOf<Pair<String, String>>()

        try {
            val req = Request.Builder()
                .url(normalized)
                .header("User-Agent", "JARVIS-WhiteHat-Inspector/1.0")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                statusCode = resp.code
                finalUrl = resp.request.url.toString()
                listOf(
                    "Server",
                    "Content-Type",
                    "Content-Security-Policy",
                    "Strict-Transport-Security",
                    "X-Frame-Options",
                    "X-Content-Type-Options",
                    "Cache-Control"
                ).forEach { headerName ->
                    resp.header(headerName)?.let { value ->
                        secHeaders.add(headerName to value)
                    }
                }
                body = resp.body?.string()?.take(250_000).orEmpty()
            }
        } catch (e: Exception) {
            notes.add("Live fetch failed: ${e.javaClass.simpleName} (${e.localizedMessage ?: "network error"})")
        }

        val elapsed = System.currentTimeMillis() - startMs
        val tlsSummary = if (parsed.protocol.equals("https", true)) {
            inspectTlsCertificate(parsed)
        } else {
            "Unencrypted HTTP — no TLS certificate."
        }

        val title = decodeHtmlEntities(
            Regex("(?is)<title[^>]*>(.*?)</title>").find(body)?.groupValues?.get(1)?.trim().orEmpty()
        ).ifBlank { "(No <title> found)" }

        val metaDesc = decodeHtmlEntities(
            Regex("(?is)<meta[^>]+name=[\"']description[\"'][^>]+content=[\"'](.*?)[\"']").find(body)
                ?.groupValues?.get(1)?.trim().orEmpty()
        )

        val scripts = Regex("(?is)<script[^>]+src=[\"'](.*?)[\"']")
            .findAll(body)
            .map { safeUrlDecode(it.groupValues[1].trim()) }
            .distinct()
            .take(20)
            .toList()

        val links = Regex("(?is)<a[^>]+href=[\"'](https?://[^\"']+)[\"']")
            .findAll(body)
            .map { it.groupValues[1].trim() }
            .distinct()
            .take(25)
            .toList()

        val techs = detectTechnologies(body, secHeaders)
        val preview = decodeHtmlEntities(
            body.replace(Regex("(?is)<script.*?</script>"), "")
                .replace(Regex("(?is)<style.*?</style>"), "")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(1200)
        )

        WebsiteDecodedReport(
            requestedUrl = normalized,
            finalUrl = finalUrl,
            scheme = parsed.protocol,
            host = parsed.host,
            port = if (parsed.port != -1) parsed.port else parsed.defaultPort,
            decodedPath = decodedPath,
            queryParameters = queryParams,
            httpStatusCode = statusCode,
            responseTimeMs = elapsed,
            pageTitle = title,
            metaDescription = metaDesc,
            detectedTechnologies = techs,
            securityHeaders = secHeaders,
            tlsCertificateSummary = tlsSummary,
            extractedLinks = links,
            scriptSources = scripts,
            decodedHtmlPreview = preview,
            notes = notes
        )
    }

    /**
     * Decodes encoded strings offline: JWT, URL-encoded text, Base64, Hexadecimal, and HTML Entities.
     */
    fun decodePayload(rawInput: String): PayloadDecodeResult {
        val input = rawInput.trim()
        if (input.isEmpty()) {
            return PayloadDecodeResult("Empty", "Enter a JWT, Base64, Hex, URL-encoded string, or HTML text.")
        }

        // 1. JWT detection (header.payload.signature)
        val jwtParts = input.split(".")
        if (jwtParts.size == 3 && jwtParts[0].startsWith("eyJ")) {
            val headerJson = decodeBase64Url(jwtParts[0])
            val payloadJson = decodeBase64Url(jwtParts[1])
            if (headerJson != null && payloadJson != null) {
                val prettyHeader = prettyJsonOrRaw(headerJson)
                val prettyPayload = prettyJsonOrRaw(payloadJson)
                val details = mutableListOf<String>()
                runCatching {
                    val obj = JSONObject(payloadJson)
                    if (obj.has("iss")) details.add("Issuer: ${obj.optString("iss")}")
                    if (obj.has("sub")) details.add("Subject: ${obj.optString("sub")}")
                    if (obj.has("exp")) details.add("Expires (epoch): ${obj.optLong("exp")}")
                }
                return PayloadDecodeResult(
                    detectedFormat = "JWT (JSON Web Token)",
                    decodedOutput = "HEADER:\n$prettyHeader\n\nPAYLOAD:\n$prettyPayload",
                    details = details
                )
            }
        }

        // 2. URL percent-encoded detection
        if (input.contains("%") && Regex("%[0-9A-Fa-f]{2}").containsMatchIn(input)) {
            val decoded = safeUrlDecode(input)
            if (decoded != input) {
                return PayloadDecodeResult(
                    detectedFormat = "URL Percent-Encoded",
                    decodedOutput = decoded,
                    details = listOf("Length: ${input.length} -> ${decoded.length} chars")
                )
            }
        }

        // 3. Hexadecimal string detection
        val cleanHex = input.replace(Regex("^(0x|\\\\x)"), "").replace(" ", "")
        if (cleanHex.length >= 4 && cleanHex.length % 2 == 0 && Regex("^[0-9A-Fa-f]+$").matches(cleanHex)) {
            val decodedHex = decodeHexToUtf8(cleanHex)
            if (decodedHex != null && decodedHex.any { it.isLetterOrDigit() }) {
                return PayloadDecodeResult(
                    detectedFormat = "Hexadecimal",
                    decodedOutput = decodedHex,
                    details = listOf("${cleanHex.length / 2} bytes decoded")
                )
            }
        }

        // 4. Base64 detection
        if (input.length >= 4 && Regex("^[A-Za-z0-9+/=_-]+$").matches(input)) {
            val decodedB64 = decodeBase64Url(input) ?: decodeBase64Standard(input)
            if (decodedB64 != null && decodedB64.isNotBlank() && decodedB64.none { it == '\uFFFD' }) {
                return PayloadDecodeResult(
                    detectedFormat = "Base64",
                    decodedOutput = prettyJsonOrRaw(decodedB64),
                    details = listOf("UTF-8 Base64 decoded (${decodedB64.length} chars)")
                )
            }
        }

        // 5. HTML entity / Unicode escape fallback
        val unescaped = decodeUnicodeEscapes(decodeHtmlEntities(input))
        return PayloadDecodeResult(
            detectedFormat = if (unescaped != input) "HTML Entities / Unicode Escapes" else "Plain Text",
            decodedOutput = unescaped
        )
    }

    fun parseDecodedQueryParams(query: String?): List<Pair<String, String>> {
        if (query.isNullOrBlank()) return emptyList()
        return query.split("&")
            .filter { it.isNotBlank() }
            .map { pair ->
                val idx = pair.indexOf('=')
                if (idx >= 0) {
                    safeUrlDecode(pair.substring(0, idx)) to safeUrlDecode(pair.substring(idx + 1))
                } else {
                    safeUrlDecode(pair) to ""
                }
            }
    }

    fun safeUrlDecode(value: String): String {
        return try {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }
    }

    fun decodeHtmlEntities(input: String): String {
        if (input.isEmpty()) return input
        var text = input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&nbsp;", " ")
        text = Regex("&#(\\d+);").replace(text) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
        }
        text = Regex("&#x([0-9A-Fa-f]+);").replace(text) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
        }
        return text
    }

    fun decodeUnicodeEscapes(input: String): String {
        return Regex("\\\\u([0-9A-Fa-f]{4})").replace(input) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
        }
    }

    fun decodeHexToUtf8(hex: String): String? {
        return try {
            val bytes = ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeBase64Standard(raw: String): String? {
        return try {
            val padded = raw.padEnd(((raw.length + 3) / 4) * 4, '=')
            val bytes = Base64.decode(padded, Base64.DEFAULT)
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeBase64Url(raw: String): String? {
        return try {
            val padded = raw.padEnd(((raw.length + 3) / 4) * 4, '=')
            val bytes = Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun prettyJsonOrRaw(raw: String): String {
        return try {
            val trimmed = raw.trim()
            if (trimmed.startsWith("{")) JSONObject(trimmed).toString(2) else raw
        } catch (_: Exception) {
            raw
        }
    }

    private fun inspectTlsCertificate(url: URL): String {
        return try {
            val conn = (url.openConnection() as? HttpsURLConnection)?.apply {
                connectTimeout = 4500
                readTimeout = 4500
                requestMethod = "HEAD"
            } ?: return "TLS unavailable"
            conn.connect()
            val certs = conn.serverCertificates
            val leaf = certs.firstOrNull() as? X509Certificate
            conn.disconnect()
            if (leaf != null) {
                "Issued to: ${leaf.subjectX500Principal.name}\nIssuer: ${leaf.issuerX500Principal.name}\nValid until: ${leaf.notAfter}"
            } else {
                "TLS handshake verified."
            }
        } catch (e: Exception) {
            "TLS inspection skipped (${e.javaClass.simpleName})"
        }
    }

    private fun detectTechnologies(html: String, headers: List<Pair<String, String>>): List<String> {
        val found = mutableSetOf<String>()
        val lower = html.lowercase()
        val server = headers.firstOrNull { it.first.equals("Server", true) }?.second.orEmpty().lowercase()

        if (server.contains("cloudflare") || lower.contains("cf-ray")) found.add("Cloudflare")
        if (server.contains("nginx")) found.add("Nginx")
        if (lower.contains("__next_data__") || lower.contains("/_next/")) found.add("Next.js")
        if (lower.contains("react") || lower.contains("data-reactroot")) found.add("React")
        if (lower.contains("wp-content")) found.add("WordPress")
        if (lower.contains("ng-version")) found.add("Angular")
        if (lower.contains("__vue__") || lower.contains("vue.js")) found.add("Vue.js")
        if (lower.contains("tailwind")) found.add("Tailwind CSS")
        if (lower.contains("bootstrap")) found.add("Bootstrap")
        return found.toList()
    }
}
