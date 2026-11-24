package com.example.nlp

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Locale

/**
 * Local on-device NLP Engine for offline command classification, multilingual tokenization,
 * slot extraction, and TFLite (.tflite) FlatBuffer asset inspection.
 */
class LocalNlpEngine(context: Context? = null) {

    private val model = TfliteFloatIntentModel()
    private val externalTflitePresent: Boolean = context?.let { detectExternalTfliteModel(it) } ?: false

    private val _status = MutableStateFlow(
        LocalNlpStatus(
            modelName = if (externalTflitePresent) {
                "jarvis_intent_nlp.tflite + Local Bow-Softmax"
            } else {
                "jarvis-bow-softmax-v1 (Float32 Tensor)"
            },
            vocabSize = model.vocabularySize,
            externalTfliteModelFound = externalTflitePresent
        )
    )
    val status: StateFlow<LocalNlpStatus> = _status.asStateFlow()

    fun classify(rawInput: String): NlpClassificationResult {
        val startNs = System.nanoTime()
        val normalized = normalizeInput(rawInput)
        val tokens = tokenize(normalized)
        val slots = extractSlots(rawInput, normalized)

        val candidates = model.predict(tokens, slots)
        val top = candidates.firstOrNull()
            ?: NlpCandidate(JarvisIntent.UNKNOWN, 0f)

        // Confidence threshold guard for unknown utterances
        val resolvedIntent = if (top.confidence >= MIN_CONFIDENCE_THRESHOLD) {
            top.intent
        } else {
            JarvisIntent.UNKNOWN
        }

        val elapsedMicros = ((System.nanoTime() - startNs) / 1_000L).coerceAtLeast(1L)
        val runtimeLabel = if (externalTflitePresent) "TFLite-Ready Local NLP" else "Local Neural NLP"

        _status.update {
            it.copy(
                lastIntent = resolvedIntent,
                lastConfidence = top.confidence,
                lastLatencyMicros = elapsedMicros
            )
        }

        return NlpClassificationResult(
            intent = resolvedIntent,
            confidence = top.confidence,
            topCandidates = candidates.take(3),
            slots = slots,
            inferenceTimeMicros = elapsedMicros,
            runtimeLabel = runtimeLabel
        )
    }

    internal fun normalizeInput(raw: String): String {
        return raw.trim()
            .lowercase(Locale.getDefault())
            .replace("wi-fi", "wifi")
            .replace("wi fi", "wifi")
            .replace("wake word", "wakeword")
            .replace("ওয়াই-ফাই", "wifi")
            .replace("ওয়াই-ফাই", "wifi")
            .replace("ওয়াইফাই", "wifi")
            .replace("ওয়াইফাই", "wifi")
            .replace("ওইফাই", "wifi")
    }

    internal fun tokenize(normalized: String): List<String> {
        return normalized
            .split(Regex("[\\s,.;!?\"'()\\[\\]{}:/\\\\]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    internal fun extractSlots(rawInput: String, normalized: String): NlpExtractedSlots {
        val urlMatch = URL_REGEX.find(rawInput)?.value
        val searchMatch = SEARCH_REGEX.find(rawInput.trim())?.groupValues?.getOrNull(1)?.trim()
        val isHighPriority = PRIORITY_REGEX.containsMatchIn(normalized)

        val appTarget = extractAppTarget(rawInput, normalized)
            ?.takeIf { urlMatch == null && searchMatch == null }

        return NlpExtractedSlots(
            url = urlMatch,
            searchQuery = searchMatch,
            appTarget = appTarget,
            isHighPriority = isHighPriority
        )
    }

    private fun extractAppTarget(rawInput: String, normalized: String): String? {
        if (!LAUNCH_VERB_REGEX.containsMatchIn(normalized)) {
            // Check direct single-app mentions like "camera" or "youtube"
            val directApps = listOf(
                "camera", "calculator", "browser", "chrome", "youtube", "whatsapp",
                "dialer", "clock", "alarm", "calendar", "contacts", "ক্যামেরা",
                "ক্যালকুলেটর", "ইউটিউব", "হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপ"
            )
            return directApps.firstOrNull { normalized.contains(it) }
        }
        val cleaned = rawInput
            .replace(Regex("(?i)^(please |jarvis |hey jarvis )"), "")
            .replace(Regex("(?i)\\b(open|launch|start|connect to|connect)\\b"), "")
            .replace(Regex("(খোলো|খুলো|চালু করো|ওপেন করুন|ওপেন)"), "")
            .trim()
        return cleaned.takeIf { it.isNotEmpty() && !GENERIC_SYSTEM_WORDS.contains(it.lowercase(Locale.getDefault())) }
    }

    /**
     * Checks if a `.tflite` FlatBuffer model (`TFL3` magic header) is staged in `assets/`
     * or `filesDir/models/`.
     */
    private fun detectExternalTfliteModel(context: Context): Boolean {
        val localFile = File(File(context.filesDir, "models"), MODEL_FILENAME)
        if (localFile.exists() && localFile.length() >= 8L) {
            return runCatching {
                localFile.inputStream().use { stream ->
                    val header = ByteArray(8)
                    stream.read(header) == 8 && hasTfl3Signature(header)
                }
            }.getOrDefault(false)
        }
        return runCatching {
            context.assets.open(MODEL_FILENAME).use { stream ->
                val header = ByteArray(8)
                stream.read(header) == 8 && hasTfl3Signature(header)
            }
        }.getOrDefault(false)
    }

    private fun hasTfl3Signature(header: ByteArray): Boolean {
        return header.size >= 8 &&
            header[4] == 'T'.code.toByte() &&
            header[5] == 'F'.code.toByte() &&
            header[6] == 'L'.code.toByte() &&
            header[7] == '3'.code.toByte()
    }

    companion object {
        private const val MODEL_FILENAME = "jarvis_intent_nlp.tflite"
        private const val MIN_CONFIDENCE_THRESHOLD = 0.14f

        private val URL_REGEX = Regex(
            "(?i)(?:https?://)?([a-z0-9-]+\\.(?:com|org|net|io|dev|ai|edu|gov|bd|in|co|app|info|me)(?:/\\S*)?)"
        )
        private val SEARCH_REGEX = Regex("(?i)^(?:search|google|find)\\s+(?:for\\s+)?(.+)$")
        private val LAUNCH_VERB_REGEX = Regex("(open |launch |start |খোলো|খুলো|চালু করো|ওপেন)")
        private val PRIORITY_REGEX = Regex("(urgent|asap|high priority|immediately|জরুরি)")
        private val GENERIC_SYSTEM_WORDS = setOf(
            "bluetooth", "wifi", "wi-fi", "security", "settings", "queue", "apps"
        )
    }
}
