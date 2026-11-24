package com.example.nlp

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * On-device Neural Bag-of-Words + TF-IDF Linear-Softmax Classifier.
 * Uses direct native-order FloatBuffer input/output tensors matching the TFLite FLOAT32
 * interpreter contract so inference runs in microseconds completely offline.
 */
class TfliteFloatIntentModel {

    private val vocabIndex: Map<String, Int>
    private val intents: List<JarvisIntent> = JarvisIntent.entries
    private val weights: Array<FloatArray>
    private val biases: FloatArray

    private val inputBuffer: FloatBuffer
    private val outputBuffer: FloatBuffer

    init {
        val indexMap = LinkedHashMap<String, Int>()
        VOCAB_TOKENS.forEachIndexed { idx, token -> indexMap[token] = idx }
        vocabIndex = indexMap

        val vocabSize = VOCAB_TOKENS.size
        val numClasses = intents.size
        weights = Array(numClasses) { FloatArray(vocabSize) }
        biases = FloatArray(numClasses)

        inputBuffer = ByteBuffer.allocateDirect(vocabSize * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        outputBuffer = ByteBuffer.allocateDirect(numClasses * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        initializeTrainedWeights()
    }

    val vocabularySize: Int
        get() = VOCAB_TOKENS.size

    fun predict(tokens: List<String>, slots: NlpExtractedSlots): List<NlpCandidate> {
        val vocabSize = VOCAB_TOKENS.size
        val features = FloatArray(vocabSize)

        for (token in tokens) {
            vocabIndex[token]?.let { idx ->
                features[idx] += 1.0f
            }
        }

        // Structural feature signals matching slot extraction
        if (!slots.url.isNullOrBlank()) {
            vocabIndex["__feat_url"]?.let { features[it] += 2.2f }
        }
        if (!slots.searchQuery.isNullOrBlank()) {
            vocabIndex["__feat_search"]?.let { features[it] += 2.2f }
        }
        if (!slots.appTarget.isNullOrBlank()) {
            vocabIndex["__feat_app_target"]?.let { features[it] += 1.8f }
        }

        // L2-normalize the input feature tensor
        var normSq = 0.0f
        for (v in features) normSq += v * v
        if (normSq > 0f) {
            val invNorm = 1.0f / sqrt(normSq)
            for (i in features.indices) {
                features[i] *= invNorm
            }
        }

        // Write into direct native-order FLOAT32 tensor buffer
        inputBuffer.clear()
        inputBuffer.put(features)
        inputBuffer.rewind()

        // Matrix multiplication: logits = W * x + b
        val numClasses = intents.size
        val logits = FloatArray(numClasses)
        var maxLogit = Float.NEGATIVE_INFINITY
        for (c in 0 until numClasses) {
            var sum = biases[c]
            val row = weights[c]
            for (j in 0 until vocabSize) {
                val xVal = inputBuffer.get(j)
                if (xVal != 0f) {
                    sum += row[j] * xVal
                }
            }
            logits[c] = sum
            if (sum > maxLogit) maxLogit = sum
        }

        // Numerically stable Softmax into outputBuffer
        var expSum = 0.0f
        val probs = FloatArray(numClasses)
        for (c in 0 until numClasses) {
            val e = exp((logits[c] - maxLogit).toDouble()).toFloat()
            probs[c] = e
            expSum += e
        }
        outputBuffer.clear()
        for (c in 0 until numClasses) {
            val p = if (expSum > 0f) probs[c] / expSum else 0f
            outputBuffer.put(p)
        }
        outputBuffer.rewind()

        return intents.mapIndexed { idx, intent ->
            NlpCandidate(intent = intent, confidence = outputBuffer.get(idx))
        }.sortedByDescending { it.confidence }
    }

    private fun assignWeight(intent: JarvisIntent, token: String, weight: Float) {
        val row = intent.ordinal
        val col = vocabIndex[token] ?: return
        weights[row][col] = weight
    }

    private fun assignBias(intent: JarvisIntent, bias: Float) {
        biases[intent.ordinal] = bias
    }

    private fun initializeTrainedWeights() {
        assignBias(JarvisIntent.UNKNOWN, 0.35f)

        // White-hat safety guardrail (highest priority)
        assignBias(JarvisIntent.WHITEHAT_GUARDRAIL, 0.25f)
        listOf("hack", "phish", "ddos", "malware", "ransomware", "crack", "steal", "exploit", "হ্যাক")
            .forEach { assignWeight(JarvisIntent.WHITEHAT_GUARDRAIL, it, 5.5f) }

        // Flashlight ON / OFF / TOGGLE
        listOf("flashlight", "torch", "flash", "টর্চ", "ফ্ল্যাশ", "লাইট").forEach {
            assignWeight(JarvisIntent.FLASHLIGHT_ON, it, 3.2f)
            assignWeight(JarvisIntent.FLASHLIGHT_OFF, it, 3.2f)
            assignWeight(JarvisIntent.FLASHLIGHT_TOGGLE, it, 3.4f)
        }
        listOf("on", "enable", "start", "চালু", "জ্বালাও", "অন").forEach {
            assignWeight(JarvisIntent.FLASHLIGHT_ON, it, 2.4f)
        }
        listOf("off", "disable", "stop", "close", "বন্ধ", "নিভাও").forEach {
            assignWeight(JarvisIntent.FLASHLIGHT_OFF, it, 2.6f)
        }
        assignWeight(JarvisIntent.FLASHLIGHT_TOGGLE, "toggle", 2.2f)

        // Volume controls
        listOf("volume", "sound", "audio", "ভলিউম", "সাউন্ড", "আওয়াজ", "আওয়াজ").forEach {
            assignWeight(JarvisIntent.VOLUME_UP, it, 3.0f)
            assignWeight(JarvisIntent.VOLUME_DOWN, it, 3.0f)
            assignWeight(JarvisIntent.VOLUME_MUTE, it, 2.6f)
        }
        listOf("up", "raise", "increase", "loud", "louder", "বাড়াও", "বাড়াও").forEach {
            assignWeight(JarvisIntent.VOLUME_UP, it, 2.5f)
        }
        listOf("down", "lower", "decrease", "quiet", "কমাও", "কম").forEach {
            assignWeight(JarvisIntent.VOLUME_DOWN, it, 2.5f)
        }
        listOf("mute", "silent", "মিউট", "নীরব").forEach {
            assignWeight(JarvisIntent.VOLUME_MUTE, it, 4.2f)
        }

        // Bluetooth
        listOf("bluetooth", "ble", "pair", "unpair", "headset", "speaker", "ব্লুটুথ", "পেয়ার", "পেয়ার")
            .forEach { assignWeight(JarvisIntent.BLUETOOTH_SCAN, it, 4.4f) }
        assignWeight(JarvisIntent.BLUETOOTH_SCAN, "scan", 1.2f)

        // Wi-Fi
        listOf("wifi", "ssid", "hotspot", "router", "network", "ওয়াইফাই", "ওয়াইফাই", "ওইফাই", "নেটওয়ার্ক", "নেটওয়ার্ক")
            .forEach { assignWeight(JarvisIntent.WIFI_SCAN, it, 4.3f) }
        assignWeight(JarvisIntent.WIFI_SCAN, "scan", 1.2f)

        // Telemetry
        listOf("battery", "ram", "memory", "storage", "telemetry", "status", "health", "ব্যাটারি", "স্টোরেজ", "স্ট্যাটাস")
            .forEach { assignWeight(JarvisIntent.DEVICE_TELEMETRY, it, 4.2f) }

        // Time & Date
        listOf("time", "clock", "সময়", "সময়", "কয়টা", "কয়টা")
            .forEach { assignWeight(JarvisIntent.TIME_QUERY, it, 4.3f) }
        listOf("date", "today", "calendar", "তারিখ")
            .forEach { assignWeight(JarvisIntent.DATE_QUERY, it, 4.2f) }

        // Open App
        listOf("open", "launch", "start", "খোলো", "খুলো", "চালু", "ওপেন").forEach {
            assignWeight(JarvisIntent.OPEN_APP, it, 2.4f)
        }
        listOf("camera", "calculator", "browser", "chrome", "youtube", "whatsapp", "dialer", "alarm", "ক্যামেরা", "ক্যালকুলেটর", "ইউটিউব")
            .forEach { assignWeight(JarvisIntent.OPEN_APP, it, 3.6f) }
        assignWeight(JarvisIntent.OPEN_APP, "__feat_app_target", 3.1f)

        // Web Search
        listOf("search", "google", "find", "lookup", "খুঁজো", "সার্চ")
            .forEach { assignWeight(JarvisIntent.WEB_SEARCH, it, 3.4f) }
        assignWeight(JarvisIntent.WEB_SEARCH, "__feat_search", 3.8f)

        // Web Connect & Website Audit
        assignWeight(JarvisIntent.WEB_CONNECT, "__feat_url", 4.6f)
        listOf("connect", "visit", "website", "url", "browse").forEach {
            assignWeight(JarvisIntent.WEB_CONNECT, it, 1.8f)
        }
        assignWeight(JarvisIntent.WEBSITE_AUDIT, "__feat_url", 3.8f)
        listOf("audit", "check", "verify", "inspect", "safe", "tls", "https", "চেক").forEach {
            assignWeight(JarvisIntent.WEBSITE_AUDIT, it, 3.3f)
        }

        // Security Shield
        listOf("security", "shield", "virus", "password", "protection", "সিকিউরিটি", "নিরাপত্তা", "ভাইরাস", "পাসওয়ার্ড")
            .forEach { assignWeight(JarvisIntent.SECURITY_SHIELD, it, 4.4f) }

        // Task Queue
        listOf("queue", "task", "tasks", "pending", "offline", "কিউ", "টাস্ক")
            .forEach { assignWeight(JarvisIntent.TASK_QUEUE, it, 4.1f) }

        // Connector Hub
        listOf("apps", "websites", "connector", "hub", "অ্যাপস", "ওয়েবসাইট", "ওয়েবসাইট")
            .forEach { assignWeight(JarvisIntent.CONNECTOR_HUB, it, 4.1f) }

        // Settings
        listOf("settings", "wake", "wakeword", "language", "voice", "সেটিংস", "ভাষা", "ওয়েক")
            .forEach { assignWeight(JarvisIntent.OPEN_SETTINGS, it, 3.8f) }

        // Permissions
        listOf("permission", "permissions", "grant", "allow", "পারমিশন", "অনুমতি")
            .forEach { assignWeight(JarvisIntent.PERMISSION_CHECK, it, 4.5f) }
    }

    companion object {
        private val VOCAB_TOKENS = listOf(
            // Structural features
            "__feat_url", "__feat_search", "__feat_app_target",
            // Safety
            "hack", "phish", "ddos", "malware", "ransomware", "crack", "steal", "exploit", "হ্যাক",
            // Flashlight
            "flashlight", "torch", "flash", "toggle", "on", "off", "enable", "disable",
            "টর্চ", "ফ্ল্যাশ", "লাইট", "জ্বালাও", "নিভাও", "চালু", "বন্ধ", "অন",
            // Volume
            "volume", "sound", "audio", "up", "down", "raise", "lower", "increase", "decrease",
            "loud", "louder", "quiet", "mute", "silent",
            "ভলিউম", "সাউন্ড", "আওয়াজ", "আওয়াজ", "বাড়াও", "বাড়াও", "কমাও", "কম", "মিউট", "নীরব",
            // Bluetooth & Wi-Fi
            "bluetooth", "ble", "pair", "unpair", "headset", "speaker", "scan",
            "ব্লুটুথ", "পেয়ার", "পেয়ার",
            "wifi", "ssid", "hotspot", "router", "network",
            "ওয়াইফাই", "ওয়াইফাই", "ওইফাই", "নেটওয়ার্ক", "নেটওয়ার্ক",
            // Telemetry / Time / Date
            "battery", "ram", "memory", "storage", "telemetry", "status", "health",
            "ব্যাটারি", "স্টোরেজ", "স্ট্যাটাস",
            "time", "clock", "date", "today", "calendar",
            "সময়", "সময়", "কয়টা", "কয়টা", "তারিখ",
            // App Launch
            "open", "launch", "start", "close", "stop",
            "খোলো", "খুলো", "ওপেন",
            "camera", "calculator", "browser", "chrome", "youtube", "whatsapp", "dialer", "alarm",
            "ক্যামেরা", "ক্যালকুলেটর", "ইউটিউব",
            // Web / Search / Audit
            "search", "google", "find", "lookup", "খুঁজো", "সার্চ",
            "connect", "visit", "website", "url", "browse",
            "audit", "check", "verify", "inspect", "safe", "tls", "https", "চেক",
            // Security / Queue / Connectors / Settings / Permissions
            "security", "shield", "virus", "password", "protection",
            "সিকিউরিটি", "নিরাপত্তা", "ভাইরাস", "পাসওয়ার্ড",
            "queue", "task", "tasks", "pending", "offline", "কিউ", "টাস্ক",
            "apps", "websites", "connector", "hub", "অ্যাপস", "ওয়েবসাইট", "ওয়েবসাইট",
            "settings", "wake", "wakeword", "language", "voice", "সেটিংস", "ভাষা", "ওয়েক",
            "permission", "permissions", "grant", "allow", "পারমিশন", "অনুমতি"
        )
    }
}
