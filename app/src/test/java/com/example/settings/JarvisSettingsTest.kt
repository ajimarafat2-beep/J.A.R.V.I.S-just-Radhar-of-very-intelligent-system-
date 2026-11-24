package com.example.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisSettingsTest {

    @Test
    fun defaultSettings_usesEnglishLanguageAndJarvisWakeWord() {
        val defaults = JarvisSettings()
        assertEquals(AppLanguage.ENGLISH, defaults.language)
        assertEquals("Jarvis", defaults.wakeWord)
        assertTrue(defaults.liveVoiceChatEnabled)
        assertTrue(defaults.voiceReplyEnabled)
        assertFalse(defaults.offlineIsolationMode)
    }

    @Test
    fun matchesWakeWord_recognizesJarvisAndCharvesPhonetics() {
        assertTrue(JarvisSettingsRepository.matchesWakeWord("Hey Jarvis open camera", "Jarvis"))
        assertTrue(JarvisSettingsRepository.matchesWakeWord("charves please scan wifi", "Jarvis"))
        assertTrue(JarvisSettingsRepository.matchesWakeWord("জারভিস টর্চ অন করো", "Jarvis"))
        assertFalse(JarvisSettingsRepository.matchesWakeWord("open calculator", "Jarvis"))
    }

    @Test
    fun matchesWakeWord_supportsCustomWakeWord() {
        assertTrue(JarvisSettingsRepository.matchesWakeWord("Hey Friday check telemetry", "Friday"))
        assertFalse(JarvisSettingsRepository.matchesWakeWord("Hey Jarvis check telemetry", "Friday"))
    }

    @Test
    fun matchesWakeWord_supportsCommaSeparatedMultipleAliasesAndBengali() {
        val aliases = "Jarvis, Friday, জার্ভেস"
        assertTrue(JarvisSettingsRepository.matchesWakeWord("Hey Friday scan phone", aliases))
        assertTrue(JarvisSettingsRepository.matchesWakeWord("জার্ভেস ফোন স্ক্যান করো", aliases))
        assertTrue(JarvisSettingsRepository.matchesWakeWord("হেই জার্ভেস টর্চ জ্বালাও", aliases))
        assertTrue(JarvisSettingsRepository.matchesWakeWord("Jarvis", aliases))
    }

    @Test
    fun extractPostWakeCommand_returnsInlineCommandOrNullOnBareWakeWord() {
        assertEquals(
            "turn on flashlight",
            JarvisSettingsRepository.extractPostWakeCommand("Hey Jarvis turn on flashlight", "Jarvis")
        )
        assertEquals(
            "ফোন স্ক্যান করো",
            JarvisSettingsRepository.extractPostWakeCommand("জার্ভেস, ফোন স্ক্যান করো", "Jarvis, জার্ভেস")
        )
        assertEquals(
            null,
            JarvisSettingsRepository.extractPostWakeCommand("Hey Jarvis", "Jarvis")
        )
        assertEquals(
            null,
            JarvisSettingsRepository.extractPostWakeCommand("হেই জার্ভিস", "Jarvis")
        )
    }
}
