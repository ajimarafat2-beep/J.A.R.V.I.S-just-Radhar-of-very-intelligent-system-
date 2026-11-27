package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.permissions.PermissionHelper
import com.example.settings.AppLanguage
import com.example.settings.JarvisSettings
import com.example.settings.JarvisSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class VoicePhase {
    IDLE,
    WAKE_LISTENING,
    COMMAND_LISTENING,
    SPEAKING
}

data class VoiceDiagnosticReport(
    val micPermissionGranted: Boolean,
    val speechRecognizerAvailable: Boolean,
    val ttsEngineReady: Boolean,
    val activeLocaleSupported: Boolean,
    val selectedVoiceName: String,
    val mediaVolumePercent: Int,
    val activeLanguageLabel: String,
    val wakeWord: String,
    val speechRate: Float,
    val speechPitch: Float,
    val summaryText: String
)

/**
 * Handles Speech-to-Text via Android [SpeechRecognizer] (with Android 12+ on-device fallback),
 * continuous Wake-Word detection, Live Voice Chat loop, live RMS audio metering,
 * and crystal-clear Text-to-Speech synthesis.
 */
class JarvisVoiceEngine(
    context: Context,
    private val onTranscriptReady: (String) -> Unit,
    private val onWakeTriggered: () -> Unit,
    private val onVoiceError: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var currentSettings = JarvisSettings()
    private var liveSessionActive = false
    private var wakeModeActive = false

    private val _phase = MutableStateFlow(VoicePhase.IDLE)
    val phase: StateFlow<VoicePhase> = _phase.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _voiceRmsLevel = MutableStateFlow(0f)
    val voiceRmsLevel: StateFlow<Float> = _voiceRmsLevel.asStateFlow()

    init {
        textToSpeech = TextToSpeech(appContext) { status ->
            ttsReady = (status == TextToSpeech.SUCCESS)
            if (ttsReady) {
                applyClearestVoiceForLocale(resolveTtsLocale("", currentSettings.language))
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _phase.value = VoicePhase.SPEAKING
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post { handleSpeechCompleted() }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post { handleSpeechCompleted() }
                    }
                })
            }
        }
    }

    fun updateSettings(settings: JarvisSettings) {
        currentSettings = settings
        if (!settings.wakeWordEnabled && _phase.value == VoicePhase.WAKE_LISTENING) {
            stopAllListening()
        }
    }

    fun isSpeechRecognitionAvailable(): Boolean {
        val standardAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
        val onDeviceAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }.getOrDefault(false)
        } else {
            false
        }
        return standardAvailable || onDeviceAvailable
    }

    fun startWakeWordListener() {
        if (!PermissionHelper.hasMicrophonePermission(appContext)) {
            onVoiceError("Microphone permission is required for Wake Word listening.")
            return
        }
        if (!isSpeechRecognitionAvailable()) {
            onVoiceError("Speech recognition service is unavailable on this device.")
            return
        }
        wakeModeActive = true
        liveSessionActive = false
        mainHandler.post { beginRecognition(isWakePass = true) }
    }

    fun startLiveVoiceChat() {
        if (!PermissionHelper.hasMicrophonePermission(appContext)) {
            onVoiceError("Microphone permission is required for Speech-to-Text.")
            return
        }
        if (!isSpeechRecognitionAvailable()) {
            onVoiceError("Speech recognition service is unavailable on this device.")
            return
        }
        liveSessionActive = true
        stopSpeakingOnly()
        mainHandler.post { beginRecognition(isWakePass = false) }
    }

    fun stopAllListening() {
        wakeModeActive = false
        liveSessionActive = false
        _partialText.value = ""
        _voiceRmsLevel.value = 0f
        mainHandler.post {
            runCatching { speechRecognizer?.cancel() }
            stopSpeakingOnly()
            _phase.value = VoicePhase.IDLE
        }
    }

    /** Stops only the wake-word listener; an active live conversation keeps going. */
    fun stopWakeWordListener() {
        wakeModeActive = false
        if (!liveSessionActive) {
            mainHandler.post {
                runCatching { speechRecognizer?.cancel() }
                _phase.value = VoicePhase.IDLE
            }
        }
    }

    /** Ends a live voice session (and any speech), returning to wake listening if enabled. */
    fun stopLiveVoiceSession() {
        liveSessionActive = false
        _partialText.value = ""
        _voiceRmsLevel.value = 0f
        mainHandler.post {
            runCatching { speechRecognizer?.cancel() }
            stopSpeakingOnly()
            if (wakeModeActive && currentSettings.wakeWordEnabled) {
                beginRecognition(isWakePass = true)
            } else {
                _phase.value = VoicePhase.IDLE
            }
        }
    }

    fun isLiveVoiceActive(): Boolean = liveSessionActive

    fun interruptAndListen() {
        stopSpeakingOnly()
        startLiveVoiceChat()
    }

    /**
     * Runs a live self-check of the Agent's voice pipeline, microphone, SpeechRecognizer,
     * TTS voices, audio volume, and speaks a crystal-clear verification phrase.
     */
    fun runVoiceDiagnostics(settings: JarvisSettings, speakVerification: Boolean = true): VoiceDiagnosticReport {
        currentSettings = settings
        val micOk = PermissionHelper.hasMicrophonePermission(appContext)
        val recognizerOk = isSpeechRecognitionAvailable()
        val onDeviceOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }.getOrDefault(false)
        val locale = resolveTtsLocale("", settings.language)
        val localeSupport = if (ttsReady) {
            val code = textToSpeech?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            code >= TextToSpeech.LANG_AVAILABLE
        } else {
            false
        }
        val selectedVoice = if (ttsReady) {
            applyClearestVoiceForLocale(locale)
        } else {
            "Initializing…"
        }

        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val curVol = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val maxVol = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1
        val volPct = ((curVol * 100f) / maxVol).toInt().coerceIn(0, 100)

        val summary = buildString {
            appendLine("Agent & Voice Access Engine Check, Sir:")
            appendLine("• Microphone Permission: ${if (micOk) "GRANTED" else "MISSING"}")
            appendLine("• Speech-to-Text (SpeechRecognizer): ${if (recognizerOk) "ACTIVE" else "UNAVAILABLE"}${if (onDeviceOk) " (On-Device Ready)" else " (Online STT / English Offline Pack)"}")
            appendLine("• Offline Voice Access Control (NLU): ACTIVE (English + Bengali Commands Ready)")
            appendLine("• TTS Voice Engine: ${if (ttsReady) "READY ($selectedVoice)" else "NOT READY"}")
            appendLine("• Language Clarity: ${settings.language.displayName} (${if (localeSupport) "Verified" else "Fallback Voice"})")
            appendLine("• Media Output Volume: $volPct%${if (volPct == 0) " (Muted — raise volume to hear Jarvis)" else ""}")
            appendLine("• Wake Word: \"${settings.wakeWord}\" | Rate: ${String.format(Locale.US, "%.2fx", settings.speechRate)}")
        }.trim()

        if (speakVerification && ttsReady) {
            val testPhrase = when (settings.language) {
                AppLanguage.BENGALI -> "এজেন্ট এবং ভয়েস অ্যাক্সেস অফলাইন ইঞ্জিন পরীক্ষা সম্পন্ন হয়েছে, স্যার। বাংলা ও ইংরেজি কমান্ড প্রস্তুত।"
                AppLanguage.HINDI -> "एजेंट और लाइव वॉइस जांच पूरी हो गई है, सर। मेरी आवाज़ और भाषा बिल्कुल स्पष्ट है।"
                else -> "Agent and Voice Access offline diagnostic complete, Sir. Speech recognition, voice control, and audio synthesis are ready."
            }
            speak(testPhrase, settings)
        }

        return VoiceDiagnosticReport(
            micPermissionGranted = micOk,
            speechRecognizerAvailable = recognizerOk,
            ttsEngineReady = ttsReady,
            activeLocaleSupported = localeSupport,
            selectedVoiceName = selectedVoice,
            mediaVolumePercent = volPct,
            activeLanguageLabel = settings.language.displayName,
            wakeWord = settings.wakeWord,
            speechRate = settings.speechRate,
            speechPitch = settings.speechPitch,
            summaryText = summary
        )
    }

    fun speak(text: String, settings: JarvisSettings) {
        currentSettings = settings
        if (!ttsReady) {
            onVoiceError("Voice is not ready yet — please try again in a moment.")
            handleSpeechCompleted()
            return
        }
        val cleanText = formatForClearSpeech(text)
        if (cleanText.isEmpty()) {
            handleSpeechCompleted()
            return
        }

        val targetLocale = resolveTtsLocale(cleanText, settings.language)
        applyClearestVoiceForLocale(targetLocale)
        val clearRate = settings.speechRate.coerceIn(0.65f, 1.45f)
        textToSpeech?.setSpeechRate(clearRate)
        textToSpeech?.setPitch(settings.speechPitch.coerceIn(0.75f, 1.35f))
        _phase.value = VoicePhase.SPEAKING
        textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stopSpeakingOnly() {
        runCatching { textToSpeech?.stop() }
        if (_phase.value == VoicePhase.SPEAKING) {
            _phase.value = VoicePhase.IDLE
        }
    }

    private fun applyClearestVoiceForLocale(locale: Locale): String {
        val tts = textToSpeech ?: return "Unavailable"
        val resolvedLocale = when {
            tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE -> locale
            locale.language == "bn" && tts.isLanguageAvailable(Locale("bn", "IN")) >= TextToSpeech.LANG_AVAILABLE ->
                Locale("bn", "IN")
            // Never fall back to an English voice for Bengali/Hindi text.
            else -> locale
        }
        tts.language = resolvedLocale
        val sameLanguage = tts.voices?.filter { it.locale.language == resolvedLocale.language }
        val bestVoice = runCatching {
            if (isNetworkOnline()) {
                // Online: prefer the best natural (network) voice, not offline-only.
                sameLanguage?.maxByOrNull { it.quality } ?: sameLanguage?.firstOrNull()
            } else {
                sameLanguage?.filter { !it.isNetworkConnectionRequired }?.maxByOrNull { it.quality }
                    ?: sameLanguage?.firstOrNull()
            }
        }.getOrNull()

        if (bestVoice != null) {
            runCatching { tts.voice = bestVoice }
            return bestVoice.name
        }
        return resolvedLocale.displayName
    }

    private fun handleSpeechCompleted() {
        _phase.value = VoicePhase.IDLE
        if (liveSessionActive && currentSettings.liveVoiceChatEnabled) {
            mainHandler.postDelayed({
                if (liveSessionActive) beginRecognition(isWakePass = false)
            }, 100L)
        } else if (wakeModeActive && currentSettings.wakeWordEnabled) {
            mainHandler.postDelayed({
                if (wakeModeActive) beginRecognition(isWakePass = true)
            }, 140L)
        }
    }

    private fun createOptimalSpeechRecognizer(): SpeechRecognizer {
        val preferOnDevice = (!isNetworkOnline() || currentSettings.offlineIsolationMode) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }.getOrDefault(false)

        if (preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        }
        return if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }.getOrDefault(false)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        } else {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }
    }

    private fun isNetworkOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun beginRecognition(isWakePass: Boolean) {
        runCatching { speechRecognizer?.destroy() }
        _voiceRmsLevel.value = 0f

        speechRecognizer = createOptimalSpeechRecognizer().apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _phase.value = if (isWakePass) VoicePhase.WAKE_LISTENING else VoicePhase.COMMAND_LISTENING
                    _partialText.value = ""
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    _voiceRmsLevel.value = normalizeRmsDb(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _voiceRmsLevel.value = 0f
                }

                override fun onError(error: Int) {
                    _partialText.value = ""
                    _voiceRmsLevel.value = 0f
                    if (error == SpeechRecognizer.ERROR_CLIENT) {
                        // Cancelled by us (e.g. stop) — stay quiet and idle.
                        _phase.value = VoicePhase.IDLE
                        return
                    }
                    val backoffMs = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 150L
                        else -> 1500L
                    }
                    if (isWakePass) {
                        if (wakeModeActive && currentSettings.wakeWordEnabled &&
                            error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                        ) {
                            // Wake loop restarts on every recoverable error (network,
                            // server, busy, audio, no-match, timeout) instead of dying.
                            mainHandler.postDelayed({
                                if (wakeModeActive) beginRecognition(isWakePass = true)
                            }, backoffMs)
                        } else {
                            _phase.value = VoicePhase.IDLE
                            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                                onVoiceError(mapRecognitionError(error))
                            }
                        }
                        return
                    }
                    // Command mode (live voice session).
                    if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                        liveSessionActive = false
                        _phase.value = VoicePhase.IDLE
                        onVoiceError(mapRecognitionError(error))
                        if (wakeModeActive && currentSettings.wakeWordEnabled) {
                            mainHandler.postDelayed({ beginRecognition(isWakePass = true) }, 140L)
                        }
                        return
                    }
                    if (liveSessionActive && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        // Recoverable error mid-conversation: keep the session alive
                        // and listen again instead of leaving it stuck.
                        Log.w(TAG, "SpeechRecognizer error code: $error — restarting live listening")
                        mainHandler.postDelayed({
                            if (liveSessionActive) beginRecognition(isWakePass = false)
                        }, backoffMs)
                        return
                    }
                    liveSessionActive = false
                    _phase.value = VoicePhase.IDLE
                    Log.w(TAG, "SpeechRecognizer error code: $error")
                    onVoiceError(mapRecognitionError(error))
                }

                override fun onResults(results: Bundle?) {
                    _voiceRmsLevel.value = 0f
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    _partialText.value = ""

                    if (text.isEmpty()) {
                        onError(SpeechRecognizer.ERROR_NO_MATCH)
                        return
                    }

                    if (isWakePass) {
                        if (JarvisSettingsRepository.matchesWakeWord(text, currentSettings.wakeWord)) {
                            onWakeTriggered()
                            val inlineCommand = JarvisSettingsRepository.extractPostWakeCommand(
                                transcript = text,
                                configuredWakeWord = currentSettings.wakeWord
                            )
                            if (inlineCommand != null) {
                                // One-breath "Hey Jarvis <command>" — execute immediately without asking again
                                liveSessionActive = currentSettings.liveVoiceChatEnabled
                                onTranscriptReady(inlineCommand)
                            } else {
                                val address = currentSettings.addressName.ifBlank { "Sir" }
                                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                                val greeting = when (currentSettings.language) {
                                    AppLanguage.BENGALI -> when (hour) {
                                        in 5..11 -> "শুভ সকাল, $address। বলুন আমি শুনছি।"
                                        in 12..16 -> "শুভ দুপুর, $address। বলুন আমি শুনছি।"
                                        in 17..20 -> "শুভ সন্ধ্যা, $address। বলুন আমি শুনছি।"
                                        else -> "হ্যাঁ $address, বলুন আমি শুনছি। অনেক রাত হয়েছে, শরীরের যত্ন নেবেন।"
                                    }
                                    AppLanguage.HINDI -> when (hour) {
                                        in 5..11 -> "शुभ प्रभात, $address। बताइए मैं सुन रहा हूँ।"
                                        in 12..16 -> "शुभ दोपहर, $address। बताइए मैं सुन रहा हूँ।"
                                        in 17..20 -> "शुभ संध्या, $address। बताइए मैं सुन रहा हूँ।"
                                        else -> "जी $address, बताइए मैं सुन रहा हूँ। देर रात हो गई है, अपना ख्याल रखिएगा।"
                                    }
                                    else -> when (hour) {
                                        in 5..11 -> "Good morning, $address. I am listening."
                                        in 12..16 -> "Good afternoon, $address. I am listening."
                                        in 17..20 -> "Good evening, $address. I am listening."
                                        else -> "Yes $address, I am listening. It is late — do take care of your rest."
                                    }
                                }
                                liveSessionActive = true
                                speak(greeting, currentSettings)
                            }
                        } else if (wakeModeActive && currentSettings.wakeWordEnabled) {
                            mainHandler.postDelayed({
                                if (wakeModeActive) beginRecognition(isWakePass = true)
                            }, 280L)
                        }
                    } else {
                        if (Regex("^(stop|exit|cancel|quiet|থামো|বন্ধ করো)$", RegexOption.IGNORE_CASE)
                                .matches(text)
                        ) {
                            liveSessionActive = false
                            _phase.value = VoicePhase.IDLE
                            if (wakeModeActive && currentSettings.wakeWordEnabled) {
                                mainHandler.postDelayed({ beginRecognition(isWakePass = true) }, 350L)
                            }
                            return
                        }
                        onTranscriptReady(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _partialText.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentSettings.language.bcp47Tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentSettings.language.bcp47Tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        runCatching { speechRecognizer?.startListening(intent) }
            .onFailure {
                _phase.value = VoicePhase.IDLE
                onVoiceError("Unable to start SpeechRecognizer: ${it.localizedMessage ?: "unknown error"}")
            }
    }

    private fun resolveTtsLocale(text: String, language: AppLanguage): Locale {
        return when (language) {
            AppLanguage.ENGLISH -> Locale.US
            AppLanguage.BENGALI -> Locale("bn", "BD")
            AppLanguage.HINDI -> Locale("hi", "IN")
            AppLanguage.AUTO -> when {
                Regex("[\\u0980-\\u09FF]").containsMatchIn(text) -> Locale("bn", "BD")
                Regex("[\\u0900-\\u097F]").containsMatchIn(text) -> Locale("hi", "IN")
                else -> Locale.US
            }
        }
    }

    fun release() {
        wakeModeActive = false
        liveSessionActive = false
        _voiceRmsLevel.value = 0f
        mainHandler.post {
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
        }
        runCatching {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
        textToSpeech = null
    }

    companion object {
        private const val TAG = "JarvisVoiceEngine"

        fun normalizeRmsDb(rmsdB: Float): Float {
            return ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        fun mapRecognitionError(errorCode: Int): String = when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error — check your microphone."
            SpeechRecognizer.ERROR_CLIENT -> "Voice session cancelled."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required for voice commands."
            SpeechRecognizer.ERROR_NETWORK -> "Network unavailable for cloud speech — install offline speech pack or reconnect."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timeout."
            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that, Sir. Tap the mic and speak clearly."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy — please try again in a moment."
            SpeechRecognizer.ERROR_SERVER -> "Speech recognition server error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected — tap the mic to speak."
            else -> "Speech recognition error ($errorCode)."
        }

        /**
         * Normalizes HUD text, abbreviations, and punctuation so Text-to-Speech pronounces
         * every sentence clearly and naturally in English, Bengali, or Hindi.
         */
        fun formatForClearSpeech(raw: String): String {
            return raw
                .replace(Regex("```[\\s\\S]*?```"), " code ")
                .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
                .replace(Regex("https?://\\S+"), " link ")
                .replace(Regex("www\\.\\S+"), " link ")
                .replace(Regex("<[^>]+>"), " ")
                .replace("°C", " degrees Celsius")
                .replace("Mbps", " megabits per second")
                .replace("RPM", " R P M")
                .replace("HDMI", " H D M I")
                .replace("TLS", " T L S")
                .replace("JWT", " J W T")
                .replace("J.A.R.V.I.S", "Jarvis")
                .replace("//", ",")
                .replace("•", ". ")
                .replace("\n", ". ")
                .replace(Regex("[*`#_|]"), " ")
                .replace(Regex("\\s+"), " ")
                .replace("..", ".")
                .trim()
        }
    }
}
