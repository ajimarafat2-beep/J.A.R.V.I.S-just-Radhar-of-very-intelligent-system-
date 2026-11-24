package com.example.agent

import android.graphics.Bitmap
import com.example.accounts.AirbnbReservation
import com.example.accounts.BankPortalEntry
import com.example.ai.AiEngineRoute
import com.example.ai.LiveVerifiedLink
import com.example.connector.ConnectedWebsiteEntry
import com.example.connector.InstalledAppEntry
import com.example.control.PhoneControlSnapshot
import com.example.data.StudioCreation
import com.example.nlp.LocalNlpStatus
import com.example.queue.OfflineTaskEntity
import com.example.queue.OfflineTaskMetrics
import com.example.queue.TaskStatus
import com.example.security.ShieldLiveState
import com.example.settings.JarvisSettings
import com.example.smarthome.LanSmartNode
import com.example.smarthome.SmartHomeDevice
import com.example.smarthome.SmartHubConfig
import com.example.voice.VoiceDiagnosticReport
import com.example.voice.VoicePhase
import java.util.UUID

enum class AgentRoute {
    CHAT,
    STUDIO,
    PHONE_CONTROL,
    SMART_HOME,
    QUEUE,
    CONNECTORS,
    BLUETOOTH,
    WIFI,
    SECURITY,
    SETTINGS
}

enum class MessageSender {
    USER,
    ASSISTANT
}

data class AgentActionShortcut(
    val label: String,
    val route: AgentRoute? = null,
    val requestPermissions: Boolean = false
)

data class AgentMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val actionShortcut: AgentActionShortcut? = null,
    val engineRoute: AiEngineRoute = AiEngineRoute.LOCAL_OFFLINE_NLP,
    val attachedBitmap: Bitmap? = null,
    val attachedAudioPath: String? = null,
    val attachedVideoPath: String? = null,
    val mediaTitle: String? = null,
    val groundingLinks: List<Pair<String, String>> = emptyList(),
    val agentSteps: List<String> = emptyList(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)

sealed interface MediaGenUiState {
    data object Idle : MediaGenUiState
    data class Loading(val prompt: String) : MediaGenUiState
    data class Success(
        val bitmap: Bitmap,
        val prompt: String,
        val filePath: String? = null
    ) : MediaGenUiState
    data class Error(val message: String) : MediaGenUiState
}

sealed interface AudioGenUiState {
    data object Idle : AudioGenUiState
    data class Loading(val prompt: String, val voiceName: String) : AudioGenUiState
    data class Success(
        val audioFilePath: String,
        val mimeType: String,
        val voiceName: String,
        val prompt: String,
        val durationSec: Int
    ) : AudioGenUiState
    data class Error(val message: String) : AudioGenUiState
}

sealed interface VideoGenUiState {
    data object Idle : VideoGenUiState
    data class Loading(val prompt: String, val progressStage: String) : VideoGenUiState
    data class Success(
        val videoFilePath: String,
        val thumbnail: Bitmap,
        val title: String,
        val summary: String,
        val voiceName: String,
        val prompt: String,
        val durationSec: Int
    ) : VideoGenUiState
    data class Error(val message: String) : VideoGenUiState
}

sealed interface WebBuildUiState {
    data object Idle : WebBuildUiState
    data class Loading(val prompt: String) : WebBuildUiState
    data class Success(val htmlCode: String, val description: String) : WebBuildUiState
    data class Error(val message: String) : WebBuildUiState
}

sealed interface VisionScanUiState {
    data object Idle : VisionScanUiState
    data class Loading(val bitmap: Bitmap, val prompt: String) : VisionScanUiState
    data class Success(val bitmap: Bitmap, val analysis: String) : VisionScanUiState
    data class Error(val message: String) : VisionScanUiState
}

data class JarvisAgentUiState(
    val signedInDisplayName: String? = null,
    val messages: List<AgentMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val activeEngineRoute: AiEngineRoute = AiEngineRoute.GEMINI_CONVERSATION,
    val edgeBorderMode: Boolean = false,
    val settings: JarvisSettings = JarvisSettings(),
    val voicePhase: VoicePhase = VoicePhase.IDLE,
    val liveTranscript: String = "",
    val voiceRmsLevel: Float = 0f,
    val voiceDiagnostic: VoiceDiagnosticReport? = null,
    val wirelessPermissionsGranted: Boolean = false,
    val micPermissionGranted: Boolean = false,
    val cameraPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = true,
    val missingWirelessPermissions: List<String> = emptyList(),
    val phoneControlState: PhoneControlSnapshot = PhoneControlSnapshot(),
    val shieldState: ShieldLiveState = ShieldLiveState(),
    val isOnline: Boolean = false,
    val activeWifiSsid: String? = null,
    val bluetoothConnectedCount: Int = 0,
    val tasks: List<OfflineTaskEntity> = emptyList(),
    val taskMetrics: OfflineTaskMetrics = OfflineTaskMetrics(),
    val nlpStatus: LocalNlpStatus = LocalNlpStatus(),
    val installedApps: List<InstalledAppEntry> = emptyList(),
    val connectedWebsites: List<ConnectedWebsiteEntry> = emptyList(),
    val smartDevices: List<SmartHomeDevice> = emptyList(),
    val lanSmartNodes: List<LanSmartNode> = emptyList(),
    val isScanningLan: Boolean = false,
    val smartHubConfig: SmartHubConfig = SmartHubConfig(),
    val googleHomeInstalled: Boolean = false,
    val smartHomeStatusMessage: String? = null,
    val voiceNotice: String? = null,
    // Studio Hub state (Image/Audio/Video Gen, Web/PWA Builder, Vision Scan, Live App & Price Discovery)
    val mediaGenState: MediaGenUiState = MediaGenUiState.Idle,
    val audioGenState: AudioGenUiState = AudioGenUiState.Idle,
    val videoGenState: VideoGenUiState = VideoGenUiState.Idle,
    val webBuildState: WebBuildUiState = WebBuildUiState.Idle,
    val visionState: VisionScanUiState = VisionScanUiState.Idle,
    val selectedAspectRatioName: String = "1:1",
    val selectedVoiceName: String = "Kore",
    val cloudCreations: List<StudioCreation> = emptyList(),
    val cloudCreationsError: String? = null,
    val discoveryLinks: List<LiveVerifiedLink> = emptyList(),
    val discoveryLoading: Boolean = false,
    val discoverySummary: String? = null,
    // Connected Accounts Vault (Airbnb & Banking)
    val vaultUnlocked: Boolean = true,
    val vaultDeviceSecured: Boolean = false,
    val airbnbInstalled: Boolean = false,
    val airbnbIcalUrl: String = "",
    val airbnbReservations: List<AirbnbReservation> = emptyList(),
    val airbnbSyncLoading: Boolean = false,
    val airbnbStatusMessage: String? = null,
    val detectedBankApps: List<InstalledAppEntry> = emptyList(),
    val bankPortals: List<BankPortalEntry> = emptyList(),
    val accountStatusMessage: String? = null
) {
    val pendingTaskCount: Int
        get() = tasks.count { it.status == TaskStatus.PENDING_NETWORK || it.status == TaskStatus.RUNNING }

    val edgeBorderActive: Boolean
        get() = edgeBorderMode || voicePhase != VoicePhase.IDLE || isProcessing
}
