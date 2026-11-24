package com.example.wifi

import android.Manifest
import android.os.Build
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisWifiManagerTest {

    @Test
    fun `required wifi permissions match Android API level`() {
        val modern = WifiClassifier.requiredWifiPermissions(Build.VERSION_CODES.TIRAMISU)
        assertArrayEquals(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES),
            modern
        )

        val legacy = WifiClassifier.requiredWifiPermissions(Build.VERSION_CODES.S)
        assertArrayEquals(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), legacy)
    }

    @Test
    fun `parse security capabilities accurately and flag weak networks`() {
        assertEquals(WifiSecurityType.WPA3, WifiClassifier.parseSecurityType("[WPA2-PSK-CCMP][SAE-CCMP][ESS]"))
        assertEquals(WifiSecurityType.WPA2, WifiClassifier.parseSecurityType("[WPA2-PSK-CCMP][ESS]"))
        assertEquals(WifiSecurityType.EAP, WifiClassifier.parseSecurityType("[WPA2-EAP-CCMP][ESS]"))
        assertEquals(WifiSecurityType.WEP, WifiClassifier.parseSecurityType("[WEP][ESS]"))
        assertEquals(WifiSecurityType.OPEN, WifiClassifier.parseSecurityType("[ESS]"))

        assertTrue(WifiClassifier.isPotentialSecurityRisk(WifiSecurityType.OPEN))
        assertTrue(WifiClassifier.isPotentialSecurityRisk(WifiSecurityType.WEP))
        assertFalse(WifiClassifier.isPotentialSecurityRisk(WifiSecurityType.WPA2))
        assertFalse(WifiClassifier.isPotentialSecurityRisk(WifiSecurityType.WPA3))
    }

    @Test
    fun `classify frequency bands and signal strength accurately`() {
        assertEquals("2.4 GHz", WifiClassifier.classifyFrequencyBand(2437))
        assertEquals("5 GHz", WifiClassifier.classifyFrequencyBand(5180))
        assertEquals("6 GHz", WifiClassifier.classifyFrequencyBand(6115))

        assertEquals(0, WifiClassifier.calculateSignalPercent(-105))
        assertEquals(100, WifiClassifier.calculateSignalPercent(-45))
        assertEquals(70, WifiClassifier.calculateSignalPercent(-65))
    }

    @Test
    fun `sanitize ssid strips surrounding quotes`() {
        assertEquals("Jarvis_Home_5G", WifiClassifier.sanitizeSsid("\"Jarvis_Home_5G\""))
        assertEquals("Lab_IoT", WifiClassifier.sanitizeSsid("  Lab_IoT  "))
    }
}
