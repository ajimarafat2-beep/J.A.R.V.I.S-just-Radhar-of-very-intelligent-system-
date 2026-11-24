package com.example.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNlpEngineTest {

    private val engine = LocalNlpEngine()

    @Test
    fun classify_englishOfflineCommands_detectsCorrectIntents() {
        val torchOn = engine.classify("Turn on the flashlight")
        assertEquals(JarvisIntent.FLASHLIGHT_ON, torchOn.intent)
        assertTrue(torchOn.confidence > 0.25f)

        val bt = engine.classify("Scan nearby bluetooth devices")
        assertEquals(JarvisIntent.BLUETOOTH_SCAN, bt.intent)

        val wifi = engine.classify("Scan Wi-Fi networks")
        assertEquals(JarvisIntent.WIFI_SCAN, wifi.intent)

        val telemetry = engine.classify("Show battery and RAM telemetry")
        assertEquals(JarvisIntent.DEVICE_TELEMETRY, telemetry.intent)
    }

    @Test
    fun classify_bengaliOfflineCommands_detectsCorrectIntents() {
        val wifiBn = engine.classify("ওয়াই-ফাই স্ক্যান করো")
        assertEquals(JarvisIntent.WIFI_SCAN, wifiBn.intent)

        val btBn = engine.classify("ব্লুটুথ স্ক্যান করো")
        assertEquals(JarvisIntent.BLUETOOTH_SCAN, btBn.intent)

        val camBn = engine.classify("ক্যামেরা খোলো")
        assertEquals(JarvisIntent.OPEN_APP, camBn.intent)
        assertEquals("ক্যামেরা", camBn.slots.appTarget)
    }

    @Test
    fun classify_onlineCommandsAndSlots_extractsUrlSearchAndPriority() {
        val search = engine.classify("Search Android neural networks urgent")
        assertEquals(JarvisIntent.WEB_SEARCH, search.intent)
        assertEquals("Android neural networks urgent", search.slots.searchQuery)
        assertTrue(search.slots.isHighPriority)
        assertTrue(search.intent.requiresNetwork)

        val connect = engine.classify("Connect to github.com")
        assertEquals(JarvisIntent.WEB_CONNECT, connect.intent)
        assertEquals("github.com", connect.slots.url)
        assertTrue(connect.intent.requiresNetwork)

        val audit = engine.classify("Audit openai.com for TLS safety")
        assertEquals(JarvisIntent.WEBSITE_AUDIT, audit.intent)
        assertEquals("openai.com", audit.slots.url)
    }

    @Test
    fun classify_whiteHatGuardrail_flagsHarmfulRequests() {
        val harmful = engine.classify("How to hack wifi password")
        assertEquals(JarvisIntent.WHITEHAT_GUARDRAIL, harmful.intent)
    }
}
