package com.example.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

enum class AppLanguage(val displayName: String, val bcp47Tag: String) {
    ENGLISH("English (Default)", "en-US"),
    BENGALI("বাংলা (Bengali)", "bn-BD"),
    HINDI("हिंदी (Hindi)", "hi-IN"),
    AUTO("Auto-Detect", "en-US")
}

data class JarvisSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val wakeWord: String = "Jarvis",
    val wakeWordEnabled: Boolean = false,
    val liveVoiceChatEnabled: Boolean = true,
    val voiceReplyEnabled: Boolean = true,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val offlineIsolationMode: Boolean = false,
    val openAiApiKey: String = "",
    val anthropicApiKey: String = ""
)

class JarvisSettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<JarvisSettings> = _settings.asStateFlow()

    private fun loadSettings(): JarvisSettings {
        val langName = prefs.getString(KEY_LANGUAGE, AppLanguage.ENGLISH.name)
        val lang = AppLanguage.entries.firstOrNull { it.name == langName } ?: AppLanguage.ENGLISH
        return JarvisSettings(
            language = lang,
            wakeWord = prefs.getString(KEY_WAKE_WORD, "Jarvis")
                ?.takeIf { it.isNotBlank() } ?: "Jarvis",
            wakeWordEnabled = prefs.getBoolean(KEY_WAKE_ENABLED, false),
            liveVoiceChatEnabled = prefs.getBoolean(KEY_LIVE_VOICE, true),
            voiceReplyEnabled = prefs.getBoolean(KEY_VOICE_REPLY, true),
            speechRate = prefs.getFloat(KEY_SPEECH_RATE, 1.0f).coerceIn(0.6f, 1.6f),
            speechPitch = prefs.getFloat(KEY_SPEECH_PITCH, 1.0f).coerceIn(0.7f, 1.4f),
            offlineIsolationMode = prefs.getBoolean(KEY_OFFLINE_ISOLATION, false),
            openAiApiKey = prefs.getString(KEY_OPENAI_API_KEY, "") ?: "",
            anthropicApiKey = prefs.getString(KEY_ANTHROPIC_API_KEY, "") ?: ""
        )
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
        _settings.update { it.copy(language = language) }
    }

    fun setWakeWord(word: String) {
        val clean = word.trim().ifBlank { "Jarvis" }
        prefs.edit().putString(KEY_WAKE_WORD, clean).apply()
        _settings.update { it.copy(wakeWord = clean) }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_ENABLED, enabled).apply()
        _settings.update { it.copy(wakeWordEnabled = enabled) }
    }

    fun setLiveVoiceChatEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_VOICE, enabled).apply()
        _settings.update { it.copy(liveVoiceChatEnabled = enabled) }
    }

    fun setVoiceReplyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_REPLY, enabled).apply()
        _settings.update { it.copy(voiceReplyEnabled = enabled) }
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.6f, 1.6f)
        prefs.edit().putFloat(KEY_SPEECH_RATE, clamped).apply()
        _settings.update { it.copy(speechRate = clamped) }
    }

    fun setSpeechPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.7f, 1.4f)
        prefs.edit().putFloat(KEY_SPEECH_PITCH, clamped).apply()
        _settings.update { it.copy(speechPitch = clamped) }
    }

    fun setOfflineIsolationMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_ISOLATION, enabled).apply()
        _settings.update { it.copy(offlineIsolationMode = enabled) }
    }
    fun setOpenAiApiKey(key: String) {
        val clean = key.trim()
        prefs.edit().putString(KEY_OPENAI_API_KEY, clean).apply()
        _settings.update { it.copy(openAiApiKey = clean) }
    }

    fun setAnthropicApiKey(key: String) {
        val clean = key.trim()
        prefs.edit().putString(KEY_ANTHROPIC_API_KEY, clean).apply()
        _settings.update { it.copy(anthropicApiKey = clean) }
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
        _settings.value = JarvisSettings()
    }

    companion object {
        private const val PREFS_NAME = "jarvis_ai_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_WAKE_WORD = "wake_word"
        private const val KEY_WAKE_ENABLED = "wake_enabled"
        private const val KEY_LIVE_VOICE = "live_voice"
        private const val KEY_VOICE_REPLY = "voice_reply"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_SPEECH_PITCH = "speech_pitch"
        private const val KEY_OFFLINE_ISOLATION = "offline_isolation"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"

        private val jarvisPhoneticVariants = listOf(
            "jarvis", "jarves", "jarvish", "jervis", "charvis", "charves",
            "জারভিস", "জার্ভিস", "জার্ভেস", "চারবেস", "চার্ভেস", "जार्विस"
        )

        fun parseWakeAliases(configuredWakeWord: String): List<String> {
            val baseAliases = configuredWakeWord
                .split(',', '|', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf("Jarvis") }

            val expanded = LinkedHashSet<String>()
            for (alias in baseAliases) {
                val lower = alias.lowercase(Locale.getDefault())
                expanded.add(lower)
                // Strip leading "hey "/"ok " if user configured "Hey Friday" so bare "Friday" also matches
                val bare = lower.removePrefix("hey ").removePrefix("ok ").removePrefix("হেই ").removePrefix("ওকে ").trim()
                if (bare.isNotEmpty()) {
                    expanded.add(bare)
                }
                if (bare == "jarvis" || lower == "jarvis" || bare == "জারভিস" || bare == "জার্ভিস" || bare == "জার্ভেস") {
                    expanded.addAll(jarvisPhoneticVariants)
                }
            }
            // Match longer phrases before shorter ones during extraction
            return expanded.sortedByDescending { it.length }
        }

        fun matchesWakeWord(transcript: String, configuredWakeWord: String): Boolean {
            return findWakeMatch(transcript, configuredWakeWord) != null
        }

        /**
         * If the spoken transcript contains a configured wake word/alias followed by a command
         * (e.g. "Hey Jarvis turn on flashlight" or "জারভিস ফোন স্ক্যান করো"), extracts and returns
         * the trailing command; returns null if only the wake word was spoken.
         */
        fun extractPostWakeCommand(transcript: String, configuredWakeWord: String): String? {
            val match = findWakeMatch(transcript, configuredWakeWord) ?: return null
            val trailing = transcript.substring(match.range.last + 1)
                .trimStart(' ', ',', ':', '-', '—', '.', '!', '?')
                .trim()
            return trailing.takeIf { it.isNotBlank() }
        }

        private fun findWakeMatch(transcript: String, configuredWakeWord: String): MatchResult? {
            val cleanTranscript = transcript.trim()
            if (cleanTranscript.isEmpty() || configuredWakeWord.isBlank()) return null

            val aliases = parseWakeAliases(configuredWakeWord)
            for (alias in aliases) {
                val escaped = Regex.escape(alias)
                // Optional greeting prefix ("hey", "ok", "hello", "হেই", "ওকে") + alias
                val pattern = if (alias.any { it.code > 127 }) {
                    // Unicode scripts (Bengali/Hindi): boundary via start/whitespace/punctuation
                    Regex(
                        "(?:^|[\\s,.!?])(?:(?:hey|ok|hello|হেই|ওকে)\\s+)?$escaped(?=$|[\\s,.!?:\\-])",
                        RegexOption.IGNORE_CASE
                    )
                } else {
                    Regex(
                        "(?:\\b(?:hey|ok|hello)\\s+)?\\b$escaped\\b",
                        RegexOption.IGNORE_CASE
                    )
                }
                val found = pattern.find(cleanTranscript)
                if (found != null) return found
            }
            return null
        }
    }
}
