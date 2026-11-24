package com.example.nlp

enum class JarvisIntent(val requiresNetwork: Boolean, val categoryLabel: String) {
    WHITEHAT_GUARDRAIL(false, "Safety Guardrail"),
    FLASHLIGHT_ON(false, "Hardware Torch"),
    FLASHLIGHT_OFF(false, "Hardware Torch"),
    FLASHLIGHT_TOGGLE(false, "Hardware Torch"),
    VOLUME_UP(false, "Audio Control"),
    VOLUME_DOWN(false, "Audio Control"),
    VOLUME_MUTE(false, "Audio Control"),
    BLUETOOTH_SCAN(false, "Bluetooth"),
    WIFI_SCAN(false, "Wi-Fi"),
    DEVICE_TELEMETRY(false, "System Telemetry"),
    TIME_QUERY(false, "Clock"),
    DATE_QUERY(false, "Calendar"),
    OPEN_APP(false, "App Launcher"),
    WEB_SEARCH(true, "Web Search"),
    WEB_CONNECT(true, "Web Connect"),
    WEBSITE_AUDIT(true, "Security Audit"),
    SECURITY_SHIELD(false, "Security Shield"),
    TASK_QUEUE(false, "Task Queue"),
    CONNECTOR_HUB(false, "Universal Hub"),
    OPEN_SETTINGS(false, "Settings"),
    PERMISSION_CHECK(false, "Permissions"),
    UNKNOWN(false, "General")
}

data class NlpExtractedSlots(
    val url: String? = null,
    val searchQuery: String? = null,
    val appTarget: String? = null,
    val isHighPriority: Boolean = false
)

data class NlpCandidate(
    val intent: JarvisIntent,
    val confidence: Float
)

data class NlpClassificationResult(
    val intent: JarvisIntent,
    val confidence: Float,
    val topCandidates: List<NlpCandidate>,
    val slots: NlpExtractedSlots,
    val inferenceTimeMicros: Long,
    val runtimeLabel: String
)

data class LocalNlpStatus(
    val modelName: String = "jarvis-bow-softmax-v1",
    val vocabSize: Int = 0,
    val intentCount: Int = JarvisIntent.entries.size,
    val externalTfliteModelFound: Boolean = false,
    val lastIntent: JarvisIntent? = null,
    val lastConfidence: Float = 0f,
    val lastLatencyMicros: Long = 0L
)
