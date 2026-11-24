package com.example.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisVoiceClarityTest {

    @Test
    fun formatForClearSpeech_expandsTechnicalUnitsAndCleansMarkdown() {
        val spoken = JarvisVoiceEngine.formatForClearSpeech(
            "J.A.R.V.I.S // Status: • AC at 22°C • Wi-Fi 150Mbps • https://www.example.com"
        )
        assertTrue(spoken.contains("Jarvis"))
        assertTrue(spoken.contains("22 degrees Celsius"))
        assertTrue(spoken.contains("150 megabits per second"))
        assertFalse(spoken.contains("•"))
        assertFalse(spoken.contains("https://"))
    }

    @Test
    fun normalizeRmsDb_clampsIntoUnitInterval() {
        assertEquals(0f, JarvisVoiceEngine.normalizeRmsDb(-5f), 0.001f)
        assertEquals(0.5f, JarvisVoiceEngine.normalizeRmsDb(4f), 0.001f)
        assertEquals(1f, JarvisVoiceEngine.normalizeRmsDb(15f), 0.001f)
    }

    @Test
    fun mapRecognitionError_returnsClearUserMessages() {
        assertTrue(
            JarvisVoiceEngine.mapRecognitionError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                .contains("Microphone permission", ignoreCase = true)
        )
        assertTrue(
            JarvisVoiceEngine.mapRecognitionError(SpeechRecognizer.ERROR_NO_MATCH)
                .contains("Didn't catch that", ignoreCase = true)
        )
        assertTrue(
            JarvisVoiceEngine.mapRecognitionError(SpeechRecognizer.ERROR_NETWORK)
                .contains("Network unavailable", ignoreCase = true)
        )
    }
}
