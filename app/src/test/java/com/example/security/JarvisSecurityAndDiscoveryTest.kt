package com.example.security

import android.Manifest
import com.example.ai.LiveDiscoveryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisSecurityAndDiscoveryTest {

    @Test
    fun analyzeMessageOrAlert_flagsHighRiskOtpPhishingAndImpersonation() {
        val phishing = "URGENT: Your bKash account is suspended! Send your OTP and PIN immediately at http://fake-verify.tk/login"
        val report = JarvisSecurityEngine.analyzeMessageOrAlert(phishing)

        assertEquals(ThreatLevel.HIGH_RISK, report.riskLevel)
        assertTrue(report.score >= 50)
        assertTrue(report.extractedUrls.any { it.contains("fake-verify.tk") })
        assertTrue(report.flags.isNotEmpty())
    }

    @Test
    fun analyzeMessageOrAlert_marksNormalFriendlyTextSafe() {
        val report = JarvisSecurityEngine.analyzeMessageOrAlert("Good morning Sir, meeting starts at 10 AM tomorrow.")
        assertEquals(ThreatLevel.SAFE, report.riskLevel)
        assertTrue(report.flags.isEmpty())
    }

    @Test
    fun extractPriceSnippets_extractsCurrencyAmountsIgnoringScripts() {
        val html = """
            <html><head><title>Store</title><script>var secretPrice = "$99999";</script></head>
            <body><div>Sony WH-1000XM5 Price: ৳ 38,500 or $349.99</div></body></html>
        """.trimIndent()

        val snippet = LiveDiscoveryEngine.extractPriceSnippets(html)
        assertNotNull(snippet)
        assertTrue(snippet!!.contains("38,500"))
        assertTrue(snippet.contains("349.99"))
    }

    @Test
    fun extractPriceSnippets_returnsNullWhenNoPricesFound() {
        assertNull(LiveDiscoveryEngine.extractPriceSnippets("<html><body>No listing available</body></html>"))
    }

    @Test
    fun evaluateThreatScore_flagsSideloadedSmsTrojanAsHighRisk() {
        val result = JarvisSecurityEngine.evaluateThreatScore(
            packageName = "com.unknown.free_nitro.trojan",
            appName = "Free Update",
            isSideloaded = true,
            isDebuggable = true,
            permissions = setOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SYSTEM_ALERT_WINDOW,
                Manifest.permission.INTERNET
            )
        )
        assertEquals(ThreatLevel.HIGH_RISK, result.riskLevel)
        assertTrue(result.score >= 55)
        assertTrue(result.flags.any { it.contains("SMS", ignoreCase = true) })
    }

    @Test
    fun evaluateThreatScore_flagsSideloadedStalkerwareComboAsHighRisk() {
        val result = JarvisSecurityEngine.evaluateThreatScore(
            packageName = "com.helper.systemservice",
            appName = "System Helper",
            isSideloaded = true,
            isDebuggable = false,
            permissions = setOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.RECEIVE_BOOT_COMPLETED
            )
        )
        assertEquals(ThreatLevel.HIGH_RISK, result.riskLevel)
        assertTrue(result.score >= 55)
    }

    @Test
    fun evaluateThreatScore_marksOfficialPlayStoreUtilitySafe() {
        val result = JarvisSecurityEngine.evaluateThreatScore(
            packageName = "com.google.android.calculator",
            appName = "Calculator",
            isSideloaded = false,
            isDebuggable = false,
            permissions = setOf(Manifest.permission.VIBRATE)
        )
        assertEquals(ThreatLevel.SAFE, result.riskLevel)
        assertEquals(0, result.score)
    }
}
