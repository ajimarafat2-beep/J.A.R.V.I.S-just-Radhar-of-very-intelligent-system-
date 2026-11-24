package com.example.security

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteDecoderEngineTest {

    @Test
    fun decodePayload_decodesUrlEncodedAndHexAndHtmlEntities() {
        val urlRes = WebsiteDecoderEngine.decodePayload("hello%20jarvis%21%2022%C2%B0C")
        assertEquals("URL Percent-Encoded", urlRes.detectedFormat)
        assertTrue(urlRes.decodedOutput.contains("hello jarvis!"))

        val hexRes = WebsiteDecoderEngine.decodePayload("4a4152564953")
        assertEquals("Hexadecimal", hexRes.detectedFormat)
        assertEquals("JARVIS", hexRes.decodedOutput)

        val htmlRes = WebsiteDecoderEngine.decodeHtmlEntities("A &amp; B &#39;Jarvis&#39;")
        assertEquals("A & B 'Jarvis'", htmlRes)
    }

    @Test
    fun decodeWebsite_inOfflineIsolation_decodesUrlPathAndQueryLocally() = runTest {
        val report = WebsiteDecoderEngine.decodeWebsite(
            rawUrl = "https://example.com/search%20page?q=smart%20ac&mode=cool",
            isOfflineIsolated = true
        )
        assertEquals("example.com", report.host)
        assertEquals("/search page", report.decodedPath)
        assertEquals(listOf("q" to "smart ac", "mode" to "cool"), report.queryParameters)
    }
}
