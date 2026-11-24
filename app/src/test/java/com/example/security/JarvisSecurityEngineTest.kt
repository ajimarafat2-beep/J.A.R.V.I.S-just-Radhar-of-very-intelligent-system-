package com.example.security

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisSecurityEngineTest {

    @Test
    fun `offline inspection flags unencrypted http and punycode`() = runTest {
        val httpReport = JarvisSecurityEngine.inspectWebsite(
            rawUrl = "http://login-verify.example.com",
            isOfflineIsolated = true
        )
        assertFalse(httpReport.isHttps)
        assertEquals(ThreatLevel.HIGH_RISK, httpReport.riskLevel)
        assertFalse(httpReport.safeToOpen)

        val punyReport = JarvisSecurityEngine.inspectWebsite(
            rawUrl = "https://xn--pple-43d.com",
            isOfflineIsolated = true
        )
        assertEquals(ThreatLevel.HIGH_RISK, punyReport.riskLevel)
    }

    @Test
    fun `offline inspection passes clean https domain`() = runTest {
        val clean = JarvisSecurityEngine.inspectWebsite(
            rawUrl = "https://developer.android.com",
            isOfflineIsolated = true
        )
        assertTrue(clean.isHttps)
        assertEquals(ThreatLevel.SAFE, clean.riskLevel)
        assertTrue(clean.safeToOpen)
    }
}
