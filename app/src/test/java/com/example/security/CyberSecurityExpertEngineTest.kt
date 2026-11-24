package com.example.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CyberSecurityExpertEngineTest {

    @Test
    fun computeHashes_producesAccurateSha256AndMd5() {
        val bundle = CyberSecurityExpertEngine.computeHashes("abc")
        assertEquals(3, bundle.inputLength)
        assertEquals(64, bundle.sha256.length)
        assertEquals(32, bundle.md5.length)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            bundle.sha256
        )
        assertEquals("900150983cd24fb0d6963f7d28e17f72", bundle.md5)
    }

    @Test
    fun generateCryptographicPassword_hasStrongEntropyAndClasses() {
        val report = CyberSecurityExpertEngine.generateCryptographicPassword(20)
        assertEquals(20, report.length)
        assertTrue(report.entropyBits >= 100)
        assertEquals("Military-Grade", report.strengthLabel)
        assertTrue(report.password.any { it.isUpperCase() })
        assertTrue(report.password.any { it.isLowerCase() })
        assertTrue(report.password.any { it.isDigit() })
    }

    @Test
    fun scanSecretsAndVulnerabilities_flagsExposedKeysAndSqli() {
        val snippet = """
            val awsKey = "AKIAIOSFODNN7EXAMPLE123"
            val q = "SELECT * FROM users WHERE id = " + userId
            element.innerHTML = payload
        """.trimIndent()
        val report = CyberSecurityExpertEngine.scanSecretsAndVulnerabilities(snippet)
        assertEquals(ThreatLevel.HIGH_RISK, report.overallRisk)
        assertTrue(report.findings.any { it.category.contains("AWS") })
        assertTrue(report.findings.any { it.category.contains("SQL Injection") })
        assertTrue(report.findings.any { it.category.contains("XSS") })
    }
}
