package com.example.agent

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.accounts.ConnectedAccountsManager
import com.example.ai.AiEngineRoute
import com.example.ai.JarvisAiOrchestrator
import com.example.ai.JarvisMediaComposer
import com.example.ai.LiveDiscoveryEngine
import com.example.ai.StreamChatEvent
import com.example.bluetooth.ConnectionStatus
import com.example.bluetooth.JarvisBluetoothManager
import com.example.connector.UniversalConnector
import com.example.control.JarvisAccessibilityService
import com.example.control.JarvisPhoneControlManager
import com.example.control.PhoneRingerMode
import com.example.data.CreationMediaType
import com.example.data.JarvisCreationsRepository
import com.example.nlp.LocalNlpEngine
import com.example.permissions.PermissionHelper
import com.example.queue.OfflineTaskEntity
import com.example.queue.OfflineTaskManager
import com.example.queue.TaskType
import com.example.security.JarvisSecurityEngine
import com.example.security.JarvisShieldStateHolder
import com.example.settings.AppLanguage
import com.example.settings.JarvisSettingsRepository
import com.example.smarthome.JarvisSmartHomeManager
import com.example.smarthome.SmartDeviceType
import com.example.voice.JarvisVoiceEngine
import com.example.voice.JarvisVoiceService
import com.example.voice.VoicePhase
import com.example.wifi.JarvisWifiManager
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

class JarvisAgentViewModel(
    private val application: Application,
    private val currentUserId: String,
    private val creationsRepository: JarvisCreationsRepository,
    private val userDisplayName: String? = null,
    private val userEmail: String? = null,
    private val userPhotoUrl: String? = null
) : ViewModel() {

    private val appContext = application.applicationContext
    private val settingsRepo = JarvisSettingsRepository(appContext)
    private val bluetoothManager = JarvisBluetoothManager(appContext)
    private val wifiManager = JarvisWifiManager(appContext)
    private val connector = UniversalConnector(appContext)
    private val smartHomeManager = JarvisSmartHomeManager(appContext)
    private val nlpEngine = LocalNlpEngine(appContext)
    private val aiOrchestrator = JarvisAiOrchestrator()
    private val accountsManager = ConnectedAccountsManager(appContext)
    private val phoneControlManager = JarvisPhoneControlManager(appContext)

    private lateinit var taskManager: OfflineTaskManager
    private lateinit var offlineEngine: OfflineActionEngine
    // The voice engine lives in JarvisVoiceService (foreground service) so the
    // wake word keeps working in the background / while locked. This ViewModel
    // binds to the service and drives the same engine instance.
    private var voiceService: JarvisVoiceService? = null
    private val voiceEngine: JarvisVoiceEngine?
        get() = voiceService?.getEngine()
    private var voiceStreamsCollected = false
    private var pendingWakeStart = false
    private var voiceServiceBound = false
    private var sendJob: Job? = null

    private val voiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? JarvisVoiceService.VoiceBinder)?.getService() ?: return
            voiceService = service
            voiceServiceBound = true
            service.setCallbacks(object : JarvisVoiceService.VoiceCallbacks {
                override fun onTranscriptReady(text: String) {
                    sendMessage(text)
                }

                override fun onWakeTriggered() {
                    _uiState.update {
                        it.copy(
                            edgeBorderMode = true,
                            voiceNotice = "Wake word detected — Edge Core active, listening clearly, Sir."
                        )
                    }
                }

                override fun onVoiceError(err: String) {
                    _uiState.update { it.copy(voiceNotice = err) }
                }
            })
            voiceEngine?.updateSettings(settingsRepo.settings.value)
            collectVoiceStreams()
            if (settingsRepo.settings.value.wakeWordEnabled &&
                PermissionHelper.hasMicrophonePermission(appContext) &&
                voiceEngine?.phase?.value == VoicePhase.IDLE
            ) {
                service.startWakeListener()
            }
            if (pendingWakeStart) {
                pendingWakeStart = false
                startOrInterruptLiveVoice()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            voiceService = null
            voiceServiceBound = false
        }
    }

    private fun collectVoiceStreams() {
        val engine = voiceEngine ?: return
        if (voiceStreamsCollected) return
        voiceStreamsCollected = true
        viewModelScope.launch {
            engine.phase.collect { p ->
                _uiState.update { it.copy(voicePhase = p) }
                // Edge light follows speaking/working: when the voice goes idle
                // the light fades back to invisible.
                if (p == VoicePhase.IDLE) {
                    _uiState.update { it.copy(edgeBorderMode = false) }
                }
            }
        }
        viewModelScope.launch { engine.partialText.collect { t -> _uiState.update { it.copy(liveTranscript = t) } } }
        viewModelScope.launch { engine.voiceRmsLevel.collect { r -> _liveVoiceRmsLevel.value = r } }
    }

    private fun bindVoiceService() {
        if (voiceServiceBound) return
        val intent = Intent(appContext, JarvisVoiceService::class.java)
        runCatching {
            appContext.bindService(intent, voiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    /** Called when the app was opened by the wake word while nothing was bound. */
    fun onWakeWordTriggeredFromBackground() {
        pendingWakeStart = true
        if (voiceServiceBound) {
            pendingWakeStart = false
            startOrInterruptLiveVoice()
        } else {
            bindVoiceService()
        }
    }

    private val _liveVoiceRmsLevel = MutableStateFlow(0f)
    val liveVoiceRmsLevel: StateFlow<Float> = _liveVoiceRmsLevel.asStateFlow()

    private val _uiState = MutableStateFlow(
        JarvisAgentUiState(
            signedInDisplayName = userDisplayName,
            settings = settingsRepo.settings.value,
            wirelessPermissionsGranted = PermissionHelper.hasAllWirelessPermissions(appContext),
            micPermissionGranted = PermissionHelper.hasMicrophonePermission(appContext),
            cameraPermissionGranted = PermissionHelper.hasCameraPermission(appContext),
            notificationPermissionGranted = PermissionHelper.hasNotificationPermission(appContext),
            missingWirelessPermissions = PermissionHelper.missingWirelessPermissions(appContext),
            phoneControlState = phoneControlManager.state.value,
            shieldState = JarvisShieldStateHolder.state.value,
            nlpStatus = nlpEngine.status.value,
            smartDevices = smartHomeManager.devices.value,
            smartHubConfig = smartHomeManager.hubConfig.value,
            vaultUnlocked = accountsManager.vaultUnlocked.value,
            airbnbIcalUrl = accountsManager.airbnbIcalUrl.value,
            airbnbReservations = accountsManager.airbnbReservations.value,
            bankPortals = accountsManager.bankPortals.value
        )
    )
    val uiState: StateFlow<JarvisAgentUiState> = _uiState.asStateFlow()

    init {
        taskManager = OfflineTaskManager(
            context = appContext,
            connector = connector,
            onExecuteQueuedAiTask = { queued -> executeQueuedOnlineTask(queued) },
            onTaskAutoCompleted = { completedTask ->
                val msg = AgentMessage(
                    sender = MessageSender.ASSISTANT,
                    text = "Queued task completed:\n${completedTask.resultSummary ?: completedTask.prompt}",
                    engineRoute = AiEngineRoute.GEMINI_CONVERSATION
                )
                _uiState.update { it.copy(messages = it.messages + msg) }
                if (shouldSpeakReply()) {
                    voiceEngine?.speak(msg.text, settingsRepo.settings.value)
                }
            }
        )
        offlineEngine = OfflineActionEngine(
            context = appContext,
            bluetoothManager = bluetoothManager,
            wifiManager = wifiManager,
            connector = connector,
            smartHomeManager = smartHomeManager,
            taskManager = taskManager,
            nlpEngine = nlpEngine,
            voiceEngineProvider = { voiceEngine },
            phoneControlManager = phoneControlManager
        )
        // The voice engine now lives in JarvisVoiceService (foreground service).
        // Bind here; onServiceConnected wires the callbacks and starts the
        // wake-word listener when it is enabled.
        bindVoiceService()
        observeAllStreams()
        viewModelScope.launch(Dispatchers.IO) {
            val apps = connector.listInstalledApps()
            val websites = connector.getConnectedWebsites()
            val googleHome = smartHomeManager.isGoogleHomeInstalled()
            val vaultSecured = accountsManager.isDeviceSecured()
            val airbnb = accountsManager.isAirbnbAppInstalled()
            val bankApps = accountsManager.detectInstalledBankApps(apps)
            _uiState.update {
                it.copy(
                    installedApps = apps,
                    connectedWebsites = websites,
                    googleHomeInstalled = googleHome,
                    vaultDeviceSecured = vaultSecured,
                    airbnbInstalled = airbnb,
                    detectedBankApps = bankApps
                )
            }
            JarvisShieldStateHolder.syncPreference(appContext)
            JarvisShieldStateHolder.scanNowIfStaleAsync(appContext)
            if (JarvisShieldStateHolder.state.value.continuousProtectionEnabled) {
                JarvisShieldStateHolder.startShield(appContext)
            }
            if (_uiState.value.wirelessPermissionsGranted) {
                bluetoothManager.loadBondedDevices()
                wifiManager.refreshActiveConnection()
            }
            aiOrchestrator.warmUpModels()
        }
        viewModelScope.launch {
            creationsRepository.upsertUserProfile(
                displayName = userDisplayName ?: "Sir",
                email = userEmail,
                photoUrl = userPhotoUrl,
                preferredLanguage = settingsRepo.settings.value.language.name
            )
        }
    }

    private fun observeAllStreams() {
        viewModelScope.launch {
            creationsRepository.observeCreations(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            cloudCreationsError = firestoreError?.localizedMessage
                                ?: error.localizedMessage
                                ?: "Unable to sync cloud creations."
                        )
                    }
                }
                .collect { creations ->
                    _uiState.update { it.copy(cloudCreations = creations, cloudCreationsError = null) }
                }
        }
        viewModelScope.launch {
            settingsRepo.settings.collect { s ->
                taskManager.offlineIsolationEnabled = s.offlineIsolationMode
                voiceEngine?.updateSettings(s)
                _uiState.update { it.copy(settings = s) }
            }
        }
        // Voice phase / transcript / RMS streams are collected in
        // collectVoiceStreams() once the voice service is bound.
        viewModelScope.launch { taskManager.isOnline.collect { o -> _uiState.update { it.copy(isOnline = o) } } }
        viewModelScope.launch { taskManager.tasks.collect { t -> _uiState.update { it.copy(tasks = t) } } }
        viewModelScope.launch { taskManager.metrics.collect { m -> _uiState.update { it.copy(taskMetrics = m) } } }
        viewModelScope.launch { nlpEngine.status.collect { st -> _uiState.update { it.copy(nlpStatus = st) } } }
        viewModelScope.launch { wifiManager.activeLink.collect { l -> _uiState.update { it.copy(activeWifiSsid = l?.ssid) } } }
        viewModelScope.launch {
            bluetoothManager.devices.collect { list ->
                _uiState.update { it.copy(bluetoothConnectedCount = list.count { d -> d.connectionStatus == ConnectionStatus.CONNECTED }) }
            }
        }
        viewModelScope.launch { smartHomeManager.devices.collect { d -> _uiState.update { it.copy(smartDevices = d) } } }
        viewModelScope.launch { smartHomeManager.lanNodes.collect { n -> _uiState.update { it.copy(lanSmartNodes = n) } } }
        viewModelScope.launch { smartHomeManager.isScanningLan.collect { sc -> _uiState.update { it.copy(isScanningLan = sc) } } }
        viewModelScope.launch { smartHomeManager.hubConfig.collect { c -> _uiState.update { it.copy(smartHubConfig = c) } } }
        viewModelScope.launch { accountsManager.vaultUnlocked.collect { u -> _uiState.update { it.copy(vaultUnlocked = u) } } }
        viewModelScope.launch { accountsManager.airbnbIcalUrl.collect { u -> _uiState.update { it.copy(airbnbIcalUrl = u) } } }
        viewModelScope.launch { accountsManager.airbnbReservations.collect { r -> _uiState.update { it.copy(airbnbReservations = r) } } }
        viewModelScope.launch { accountsManager.bankPortals.collect { p -> _uiState.update { it.copy(bankPortals = p) } } }
        viewModelScope.launch { phoneControlManager.state.collect { pcs -> _uiState.update { it.copy(phoneControlState = pcs) } } }
        viewModelScope.launch {
            JarvisShieldStateHolder.state.collect { shield ->
                _uiState.update { it.copy(shieldState = shield) }
            }
        }
        viewModelScope.launch {
            JarvisAccessibilityService.isConnected.collect {
                phoneControlManager.refreshState()
            }
        }
    }

    fun toggleEdgeBorderMode() {
        _uiState.update { it.copy(edgeBorderMode = !it.edgeBorderMode) }
    }

    fun refreshPermissions() {
        val wirelessOk = PermissionHelper.hasAllWirelessPermissions(appContext)
        val micOk = PermissionHelper.hasMicrophonePermission(appContext)
        val camOk = PermissionHelper.hasCameraPermission(appContext)
        val notifOk = PermissionHelper.hasNotificationPermission(appContext)
        val missingWireless = PermissionHelper.missingWirelessPermissions(appContext)
        _uiState.update {
            it.copy(
                wirelessPermissionsGranted = wirelessOk,
                micPermissionGranted = micOk,
                cameraPermissionGranted = camOk,
                notificationPermissionGranted = notifOk,
                missingWirelessPermissions = missingWireless
            )
        }
        JarvisShieldStateHolder.scanNowIfStaleAsync(appContext)
        viewModelScope.launch(Dispatchers.IO) {
            phoneControlManager.refreshState()
            val googleHome = smartHomeManager.isGoogleHomeInstalled()
            val airbnb = accountsManager.isAirbnbAppInstalled()
            val vaultSecured = accountsManager.isDeviceSecured()
            _uiState.update {
                it.copy(
                    googleHomeInstalled = googleHome,
                    airbnbInstalled = airbnb,
                    vaultDeviceSecured = vaultSecured
                )
            }
            if (wirelessOk) {
                bluetoothManager.loadBondedDevices()
                wifiManager.refreshActiveConnection()
            }
        }
    }

    // 24/7 Virus Shield & Threat Removal Actions
    fun runDeepVirusScan() {
        JarvisShieldStateHolder.scanNowAsync(appContext)
    }

    fun toggleContinuousShield(enabled: Boolean) {
        JarvisShieldStateHolder.setContinuousEnabled(appContext, enabled)
        if (enabled) {
            JarvisShieldStateHolder.scanNowAsync(appContext)
        }
    }

    fun deleteThreatPackage(packageName: String) {
        runCatching {
            appContext.startActivity(JarvisSecurityEngine.createUninstallIntent(packageName))
        }
    }

    fun deleteNextThreat() {
        val nextThreat = _uiState.value.shieldState.report.highRiskApps.firstOrNull()
            ?: _uiState.value.shieldState.report.cautionApps.firstOrNull()
        if (nextThreat != null) {
            deleteThreatPackage(nextThreat.packageName)
        } else {
            _uiState.update { it.copy(voiceNotice = "No threat apps detected on this device, Sir.") }
        }
    }

    fun trustAppInShield(packageName: String) {
        JarvisSecurityEngine.trustPackage(appContext, packageName)
        JarvisShieldStateHolder.scanNowAsync(appContext)
    }

    fun untrustAppInShield(packageName: String) {
        JarvisSecurityEngine.untrustPackage(appContext, packageName)
        JarvisShieldStateHolder.scanNowAsync(appContext)
    }

    // Full Phone Control & Camera actions
    fun togglePhoneTorch() { phoneControlManager.toggleFlashlight() }
    fun setPhoneBrightness(percent: Int) { phoneControlManager.setBrightnessPercent(percent) }
    fun setPhoneAutoBrightness(enabled: Boolean) { phoneControlManager.setAutoBrightness(enabled) }
    fun setPhoneStreamVolume(streamType: Int, percent: Int) { phoneControlManager.setStreamVolumePercent(streamType, percent) }
    fun setPhoneRingerMode(mode: PhoneRingerMode) { phoneControlManager.setRingerMode(mode) }
    fun dispatchPhoneMediaKey(keyCode: Int) { phoneControlManager.dispatchMediaKey(keyCode) }
    fun executeGlobalPhoneAction(actionName: String) { phoneControlManager.performGlobalNavigation(actionName) }
    fun openPhoneSystemPanel(panel: String) { phoneControlManager.openSystemControlPanel(panel) }
    fun openPhoneDialer(number: String) { phoneControlManager.openDialer(number) }
    fun openPhoneCameraApp(videoMode: Boolean) { phoneControlManager.openCameraApp(videoMode) }
    fun setPhoneQuickTimer(minutes: Int) { phoneControlManager.setQuickTimer(minutes) }

    fun runAgentAndVoiceCheck(speakResult: Boolean = true) {
        refreshPermissions()
        val report = voiceEngine?.runVoiceDiagnostics(_uiState.value.settings, speakVerification = speakResult)
        _uiState.update {
            it.copy(
                voiceDiagnostic = report,
                voiceNotice = report?.let { r ->
                    "Voice Check: TTS ${if (r.ttsEngineReady) "Ready" else "Pending"} • Mic ${if (r.micPermissionGranted) "OK" else "Needed"} • Vol ${r.mediaVolumePercent}%"
                }
            )
        }
    }

    fun startOrInterruptLiveVoice() {
        refreshPermissions()
        val engine = voiceEngine
        if (engine == null) {
            // The background voice service has not bound yet — bind it and say so.
            bindVoiceService()
            _uiState.update {
                it.copy(voiceNotice = "Starting the voice service — tap the mic again in a moment, Sir.")
            }
            return
        }
        when (engine.phase.value) {
            VoicePhase.SPEAKING -> engine.interruptAndListen()
            VoicePhase.COMMAND_LISTENING -> engine.stopAllListening()
            VoicePhase.IDLE, VoicePhase.WAKE_LISTENING -> engine.startLiveVoiceChat()
        }
    }

    fun onSystemAssistInvoked() {
        refreshPermissions()
        _uiState.update {
            it.copy(
                edgeBorderMode = true,
                voiceNotice = "J.A.R.V.I.S Assistant Active — Ready for your command, Sir."
            )
        }
        if (PermissionHelper.hasMicrophonePermission(appContext)) {
            voiceEngine?.startLiveVoiceChat()
        }
    }

    fun onMicPermissionResult(granted: Boolean, startListeningIfGranted: Boolean, enableWakeIfGranted: Boolean) {
        refreshPermissions()
        if (!granted) {
            _uiState.update { it.copy(voiceNotice = "Microphone permission denied. Grant audio access in Settings.") }
            return
        }
        if (enableWakeIfGranted) toggleWakeWord(true)
        else if (startListeningIfGranted) startOrInterruptLiveVoice()
    }

    fun toggleWakeWord(enabled: Boolean) {
        settingsRepo.setWakeWordEnabled(enabled)
        if (enabled) {
            if (PermissionHelper.hasMicrophonePermission(appContext)) {
                JarvisVoiceService.startWakeService(appContext)
            }
            bindVoiceService()
        } else {
            voiceEngine?.stopWakeWordListener()
            // Keep the service alive only while a live voice session is running.
            if (voiceEngine?.isLiveVoiceActive() != true) {
                JarvisVoiceService.stopVoiceService(appContext)
            }
        }
    }

    fun setLanguage(language: AppLanguage) = settingsRepo.setLanguage(language)
    fun setWakeWord(word: String) = settingsRepo.setWakeWord(word)
    fun setAddressName(name: String) = settingsRepo.setAddressName(name)
    fun setOpenAiApiKey(key: String) = settingsRepo.setOpenAiApiKey(key)
    fun setAnthropicApiKey(key: String) = settingsRepo.setAnthropicApiKey(key)
    fun setAppLockEnabled(enabled: Boolean) = settingsRepo.setAppLockEnabled(enabled)
    fun setAppLockPin(pin: String) = settingsRepo.setAppLockPin(pin)
    fun setLiveVoiceChatEnabled(enabled: Boolean) = settingsRepo.setLiveVoiceChatEnabled(enabled)
    fun setVoiceReplyEnabled(enabled: Boolean) {
        settingsRepo.setVoiceReplyEnabled(enabled)
        if (!enabled) voiceEngine?.stopSpeakingOnly()
    }
    fun setSpeechRate(rate: Float) = settingsRepo.setSpeechRate(rate)
    fun setSpeechPitch(pitch: Float) = settingsRepo.setSpeechPitch(pitch)
    fun applyCrystalClearVoicePreset() {
        settingsRepo.setSpeechRate(0.95f)
        settingsRepo.setSpeechPitch(1.0f)
        settingsRepo.setVoiceReplyEnabled(true)
        runAgentAndVoiceCheck(speakResult = true)
    }
    fun setOfflineIsolationMode(enabled: Boolean) {
        settingsRepo.setOfflineIsolationMode(enabled)
        taskManager.offlineIsolationEnabled = enabled
        if (!enabled) taskManager.triggerPendingFlush()
    }
    fun resetSettings() = settingsRepo.resetToDefaults()
    fun toggleVoiceReply() = setVoiceReplyEnabled(!_uiState.value.settings.voiceReplyEnabled)

    fun startNewChat() {
        voiceEngine?.stopLiveVoiceSession()
        aiOrchestrator.resetConversationMemory()
        _uiState.update { it.copy(messages = emptyList(), isProcessing = false) }
    }

    fun speak(text: String) {
        voiceEngine?.speak(text, _uiState.value.settings)
    }

    /** Stops a generating answer (used by the stop button). */
    fun stopGeneration() {
        sendJob?.cancel()
        sendJob = null
        voiceEngine?.stopSpeakingOnly()
        _uiState.update { state ->
            state.copy(
                isProcessing = false,
                messages = state.messages.map { if (it.isStreaming) it.copy(isStreaming = false) else it }
            )
        }
    }

    /** Speak a reply out loud when voice replies are on, or during a live voice chat. */
    private fun shouldSpeakReply(): Boolean =
        _uiState.value.settings.voiceReplyEnabled || (voiceEngine?.isLiveVoiceActive() == true)

    fun sendMessage(rawInput: String, attachedImage: Bitmap? = null) {
        val prompt = rawInput.trim()
        if (prompt.isEmpty() && attachedImage == null) return
        // Ignore rapid double-sends while a reply is still streaming.
        if (sendJob?.isActive == true) return

        val displayPrompt = prompt.ifEmpty { "Analyze this image cleanly, Sir." }
        val userMsg = AgentMessage(
            sender = MessageSender.USER,
            text = displayPrompt,
            attachedBitmap = attachedImage
        )
        _uiState.update { it.copy(messages = it.messages + userMsg, isProcessing = true) }

        sendJob = viewModelScope.launch {
            val settings = _uiState.value.settings
            if (attachedImage != null) {
                if (!taskManager.canExecuteOnlineNow()) {
                    appendAssistantMessage(
                        text = "Offline right now — Privacy-Safe Vision requires an internet connection to analyze images via Gemini Vision.",
                        route = AiEngineRoute.GEMINI_VISION,
                        isError = true
                    )
                    return@launch
                }
                _uiState.update { it.copy(activeEngineRoute = AiEngineRoute.GEMINI_VISION) }
                val res = aiOrchestrator.analyzeImage(attachedImage, displayPrompt, settings)
                res.onSuccess { analysis ->
                    appendAssistantMessage(analysis, AiEngineRoute.GEMINI_VISION)
                }.onFailure { err ->
                    appendAssistantMessage(
                        text = err.localizedMessage ?: "Vision analysis failed.",
                        route = AiEngineRoute.GEMINI_VISION,
                        isError = true
                    )
                }
                return@launch
            }

            // 1. Try deterministic multi-step compound offline actions first
            val compoundAction = runCatching { offlineEngine.tryCompoundLocalAction(prompt, settings) }.getOrNull()
            if (compoundAction != null) {
                val (steps, res) = compoundAction
                appendAssistantMessage(
                    text = res.responseText,
                    route = AiEngineRoute.LOCAL_OFFLINE_NLP,
                    shortcut = res.shortcut,
                    agentSteps = steps
                )
                return@launch
            }

            // 2. Try single deterministic offline action (only if not a multi-clause compound goal)
            if (!offlineEngine.isCompoundGoalPrompt(prompt)) {
                val localAction = runCatching { offlineEngine.tryLocalAction(prompt, settings) }.getOrNull()
                if (localAction != null) {
                    appendAssistantMessage(
                        text = localAction.responseText,
                        route = AiEngineRoute.LOCAL_OFFLINE_NLP,
                        shortcut = localAction.shortcut,
                        agentSteps = listOf(localAction.responseText.lineSequence().firstOrNull().orEmpty().take(90))
                    )
                    return@launch
                }
            }

            // 3. Multi-AI Orchestrator & Autonomous Agent Routing
            val route = runCatching { aiOrchestrator.selectRoute(prompt, hasImage = false) }.getOrElse { AiEngineRoute.GEMINI_RESEARCH }
            _uiState.update { it.copy(activeEngineRoute = route) }

            if (!taskManager.canExecuteOnlineNow()) {
                val queuedType = when (route) {
                    AiEngineRoute.WEB_ARCHITECT -> TaskType.WEB_BUILD
                    AiEngineRoute.LIVE_DISCOVERY -> if (prompt.contains("price", true) || prompt.contains("দাম")) TaskType.PRICE_FINDER else TaskType.APP_DISCOVERY
                    else -> TaskType.AI_QUERY
                }
                taskManager.enqueuePendingTask(
                    prompt = prompt,
                    taskType = queuedType,
                    targetValue = prompt,
                    intentName = route.name,
                    confidence = 0.9f
                )
                appendAssistantMessage(
                    text = "Offline right now, Sir. Queued \"$prompt\" (${route.badgeLabel}) in your Persistent Task Queue — it will auto-execute as soon as internet reconnects.",
                    route = AiEngineRoute.LOCAL_OFFLINE_NLP,
                    shortcut = AgentActionShortcut("View Task Queue", AgentRoute.QUEUE)
                )
                return@launch
            }

            when (route) {
                AiEngineRoute.GEMINI_VIDEO_STUDIO -> {
                    val voiceName = _uiState.value.selectedVoiceName
                    _uiState.update { it.copy(videoGenState = VideoGenUiState.Loading(prompt, "1/4 Directing storyboard…")) }
                    val videoRes = aiOrchestrator.generateVideo(
                        context = appContext,
                        prompt = prompt,
                        voiceName = voiceName,
                        settings = settings,
                        onProgress = { stage ->
                            _uiState.update { it.copy(videoGenState = VideoGenUiState.Loading(prompt, stage)) }
                        }
                    )
                    videoRes.onSuccess { vid ->
                        _uiState.update {
                            it.copy(
                                videoGenState = VideoGenUiState.Success(
                                    videoFilePath = vid.videoFile.absolutePath,
                                    thumbnail = vid.thumbnail,
                                    title = vid.title,
                                    summary = vid.summary,
                                    voiceName = vid.voiceName,
                                    prompt = prompt,
                                    durationSec = vid.durationSeconds
                                )
                            )
                        }
                        creationsRepository.saveCreation(
                            mediaType = CreationMediaType.VIDEO,
                            prompt = prompt,
                            title = vid.title,
                            summary = vid.summary,
                            modelUsed = "gemini-3.8-flash + nano-banana-2.1",
                            voiceName = vid.voiceName,
                            durationSeconds = vid.durationSeconds,
                            localFilePath = vid.videoFile.absolutePath
                        )
                        appendAssistantMessage(
                            text = "Generated 720p MP4 AI Video \"${vid.title}\" (${vid.durationSeconds}s) with ${vid.voiceName} voiceover:\n${vid.summary}",
                            route = AiEngineRoute.GEMINI_VIDEO_STUDIO,
                            bitmap = vid.thumbnail,
                            attachedVideoPath = vid.videoFile.absolutePath,
                            mediaTitle = vid.title,
                            shortcut = AgentActionShortcut("Open Video Studio", AgentRoute.STUDIO)
                        )
                    }.onFailure { err ->
                        val msg = err.localizedMessage ?: "AI video synthesis failed."
                        _uiState.update { it.copy(videoGenState = VideoGenUiState.Error(msg)) }
                        appendAssistantMessage(
                            text = "Video generation failed: $msg",
                            route = AiEngineRoute.GEMINI_VIDEO_STUDIO,
                            isError = true
                        )
                    }
                }
                AiEngineRoute.GEMINI_AUDIO_STUDIO -> {
                    val voiceName = _uiState.value.selectedVoiceName
                    _uiState.update { it.copy(audioGenState = AudioGenUiState.Loading(prompt, voiceName)) }
                    val audioRes = aiOrchestrator.generateAudio(
                        context = appContext,
                        promptOrScript = prompt,
                        voiceName = voiceName
                    )
                    audioRes.onSuccess { aud ->
                        _uiState.update {
                            it.copy(
                                audioGenState = AudioGenUiState.Success(
                                    audioFilePath = aud.audioFile.absolutePath,
                                    mimeType = aud.mimeType,
                                    voiceName = aud.voiceName,
                                    prompt = prompt,
                                    durationSec = aud.durationSeconds
                                )
                            )
                        }
                        creationsRepository.saveCreation(
                            mediaType = CreationMediaType.AUDIO,
                            prompt = prompt,
                            title = "Voice: ${aud.voiceName}",
                            summary = prompt.take(180),
                            modelUsed = "gemini-3.1-flash-tts-preview",
                            voiceName = aud.voiceName,
                            durationSeconds = aud.durationSeconds,
                            localFilePath = aud.audioFile.absolutePath
                        )
                        appendAssistantMessage(
                            text = "Synthesized neural AI voice audio (${aud.voiceName} • ${aud.durationSeconds}s) for: \"$prompt\"",
                            route = AiEngineRoute.GEMINI_AUDIO_STUDIO,
                            attachedAudioPath = aud.audioFile.absolutePath,
                            mediaTitle = "Voice ${aud.voiceName} • ${aud.durationSeconds}s",
                            shortcut = AgentActionShortcut("Open Audio Studio", AgentRoute.STUDIO)
                        )
                    }.onFailure { err ->
                        val msg = err.localizedMessage ?: "AI audio generation failed."
                        _uiState.update { it.copy(audioGenState = AudioGenUiState.Error(msg)) }
                        appendAssistantMessage(
                            text = "Audio generation failed: $msg",
                            route = AiEngineRoute.GEMINI_AUDIO_STUDIO,
                            isError = true
                        )
                    }
                }
                AiEngineRoute.NANO_BANANA_IMAGE -> {
                    val ratioLabel = _uiState.value.selectedAspectRatioName
                    val ratio = aspectRatioFromLabel(ratioLabel)
                    val imgRes = aiOrchestrator.generateImage(prompt, aspectRatio = ratio)
                    imgRes.onSuccess { bmp ->
                        val file = runCatching { JarvisMediaComposer.saveBitmapToFile(appContext, bmp) }.getOrNull()
                        _uiState.update { it.copy(mediaGenState = MediaGenUiState.Success(bmp, prompt, file?.absolutePath)) }
                        creationsRepository.saveCreation(
                            mediaType = CreationMediaType.IMAGE,
                            prompt = prompt,
                            title = prompt.take(64),
                            summary = "Generated ($ratioLabel)",
                            modelUsed = "gemini-nano-banana-2.1",
                            localFilePath = file?.absolutePath
                        )
                        appendAssistantMessage(
                            text = "Generated image via Nano Banana 2.1 for: \"$prompt\"",
                            route = AiEngineRoute.NANO_BANANA_IMAGE,
                            bitmap = bmp,
                            shortcut = AgentActionShortcut("Open Media Studio", AgentRoute.STUDIO)
                        )
                    }.onFailure { err ->
                        appendAssistantMessage(
                            text = "Image generation failed: ${err.localizedMessage ?: "Unknown error"}",
                            route = AiEngineRoute.NANO_BANANA_IMAGE,
                            isError = true
                        )
                    }
                }
                AiEngineRoute.WEB_ARCHITECT -> {
                    val buildRes = aiOrchestrator.buildInteractiveHtmlApp(prompt)
                    buildRes.onSuccess { html ->
                        _uiState.update { it.copy(webBuildState = WebBuildUiState.Success(html, prompt)) }
                        creationsRepository.saveCreation(
                            mediaType = CreationMediaType.WEB_APP,
                            prompt = prompt,
                            title = prompt.take(64),
                            summary = html.take(240),
                            modelUsed = "gemini-3.8-flash"
                        )
                        appendAssistantMessage(
                            text = "Built complete responsive HTML5/PWA app for \"$prompt\". Tap below to preview it live in AI Studio.",
                            route = AiEngineRoute.WEB_ARCHITECT,
                            shortcut = AgentActionShortcut("Preview Live Website", AgentRoute.STUDIO)
                        )
                    }.onFailure { err ->
                        appendAssistantMessage(
                            text = "Website build failed: ${err.localizedMessage ?: "Unknown error"}",
                            route = AiEngineRoute.WEB_ARCHITECT,
                            isError = true
                        )
                    }
                }
                else -> {
                    val streamMsgId = UUID.randomUUID().toString()
                    val needsAgentPlan = aiOrchestrator.looksLikeDeviceOrAgentGoal(prompt)
                    if (!needsAgentPlan) {
                        streamGeminiReply(
                            prompt = prompt,
                            settings = settings
                        )
                    } else {
                        val planningBubble = AgentMessage(
                            id = streamMsgId,
                            sender = MessageSender.ASSISTANT,
                            text = "Planning actions…",
                            engineRoute = AiEngineRoute.GEMINI_CONVERSATION,
                            isStreaming = true
                        )
                        _uiState.update { it.copy(messages = it.messages + planningBubble) }

                        val executedSteps = mutableListOf<String>()
                        var primaryShortcut: AgentActionShortcut? = null
                        val plan = aiOrchestrator.planAgentActions(prompt).getOrNull()
                        if (plan != null && plan.isAgentTask && plan.steps.isNotEmpty()) {
                            for (step in plan.steps) {
                                _uiState.update { state ->
                                    state.copy(
                                        messages = state.messages.map {
                                            if (it.id == streamMsgId) {
                                                it.copy(text = "Executing ${step.toolName}…", agentSteps = executedSteps.toList())
                                            } else it
                                        }
                                    )
                                }
                                val (stepResult, stepShortcut) = runCatching {
                                    offlineEngine.executeAgentTool(
                                        toolName = step.toolName,
                                        argument = step.argument,
                                        settings = settings
                                    )
                                }.getOrElse { "Step failed: ${step.toolName}" to null }
                                executedSteps.add("${step.toolName}: ${stepResult.lineSequence().firstOrNull().orEmpty().take(95)}")
                                if (primaryShortcut == null && stepShortcut != null) {
                                    primaryShortcut = stepShortcut
                                }
                            }
                        }
                        streamGeminiReply(
                            prompt = prompt,
                            settings = settings,
                            existingBubbleId = streamMsgId,
                            executedToolContext = executedSteps,
                            agentSteps = executedSteps,
                            shortcut = primaryShortcut
                        )
                    }
                }
            }
        }
    }

    private suspend fun streamGeminiReply(
        prompt: String,
        settings: com.example.settings.JarvisSettings,
        existingBubbleId: String? = null,
        executedToolContext: List<String> = emptyList(),
        agentSteps: List<String> = emptyList(),
        shortcut: AgentActionShortcut? = null
    ) {
        val streamMsgId = existingBubbleId ?: UUID.randomUUID().toString()
        if (existingBubbleId == null) {
            val initialBubble = AgentMessage(
                id = streamMsgId,
                sender = MessageSender.ASSISTANT,
                text = "",
                actionShortcut = shortcut,
                engineRoute = AiEngineRoute.GEMINI_CONVERSATION,
                agentSteps = agentSteps,
                isStreaming = true
            )
            _uiState.update { it.copy(messages = it.messages + initialBubble) }
        } else {
            _uiState.update { state ->
                state.copy(
                    messages = state.messages.map {
                        if (it.id == streamMsgId) {
                            it.copy(
                                text = "",
                                actionShortcut = shortcut ?: it.actionShortcut,
                                agentSteps = agentSteps,
                                isStreaming = true
                            )
                        } else it
                    }
                )
            }
        }

        try {
            aiOrchestrator.streamConversation(prompt, settings, executedToolContext).collect { event ->
            when (event) {
                is StreamChatEvent.Chunk -> {
                    _uiState.update { state ->
                        state.copy(
                            activeEngineRoute = event.route,
                            messages = state.messages.map {
                                if (it.id == streamMsgId) {
                                    it.copy(
                                        text = event.accumulatedText,
                                        engineRoute = event.route,
                                        groundingLinks = event.groundingLinks,
                                        isStreaming = true
                                    )
                                } else it
                            }
                        )
                    }
                }
                is StreamChatEvent.Complete -> {
                    _uiState.update { state ->
                        state.copy(
                            isProcessing = false,
                            activeEngineRoute = event.route,
                            messages = state.messages.map {
                                if (it.id == streamMsgId) {
                                    it.copy(
                                        text = event.fullText,
                                        engineRoute = event.route,
                                        groundingLinks = event.groundingLinks,
                                        isStreaming = false
                                    )
                                } else it
                            }
                        )
                    }
                    if (shouldSpeakReply()) {
                        voiceEngine?.speak(event.fullText, _uiState.value.settings)
                    }
                }
                is StreamChatEvent.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            isProcessing = false,
                            messages = state.messages.map {
                                if (it.id == streamMsgId) {
                                    it.copy(
                                        text = event.message,
                                        engineRoute = event.route,
                                        isStreaming = false,
                                        isError = true
                                    )
                                } else it
                            }
                        )
                    }
                }
            }
        }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { state ->
                state.copy(
                    isProcessing = false,
                    messages = state.messages.map {
                        if (it.id == streamMsgId) {
                            it.copy(
                                text = "Sorry, Sir \u2014 something went wrong while answering. Please try again.",
                                engineRoute = AiEngineRoute.GEMINI_CONVERSATION,
                                isStreaming = false,
                                isError = true
                            )
                        } else it
                    }
                )
            }
        }
    }

    private fun appendAssistantMessage(
        text: String,
        route: AiEngineRoute,
        shortcut: AgentActionShortcut? = null,
        bitmap: Bitmap? = null,
        attachedAudioPath: String? = null,
        attachedVideoPath: String? = null,
        mediaTitle: String? = null,
        groundingLinks: List<Pair<String, String>> = emptyList(),
        agentSteps: List<String> = emptyList(),
        isError: Boolean = false
    ) {
        val msg = AgentMessage(
            sender = MessageSender.ASSISTANT,
            text = text,
            actionShortcut = shortcut,
            engineRoute = route,
            attachedBitmap = bitmap,
            attachedAudioPath = attachedAudioPath,
            attachedVideoPath = attachedVideoPath,
            mediaTitle = mediaTitle,
            groundingLinks = groundingLinks,
            agentSteps = agentSteps,
            isError = isError
        )
        _uiState.update { it.copy(messages = it.messages + msg, isProcessing = false) }
        if (!isError && shouldSpeakReply()) {
            voiceEngine?.speak(text, _uiState.value.settings)
        }
    }

    private suspend fun executeQueuedOnlineTask(task: OfflineTaskEntity): String {
        val settings = _uiState.value.settings
        return when (task.taskType) {
            TaskType.WEB_BUILD -> {
                val html = aiOrchestrator.buildInteractiveHtmlApp(task.targetValue).getOrThrow()
                _uiState.update { it.copy(webBuildState = WebBuildUiState.Success(html, task.targetValue)) }
                "Website generated for \"${task.targetValue}\". Open AI Studio to preview live."
            }
            TaskType.APP_DISCOVERY -> {
                val links = LiveDiscoveryEngine.discoverAndVerifyApp(task.targetValue)
                val up = links.count { it.reachable }
                "Live App Discovery for \"${task.targetValue}\": verified $up/${links.size} sources (${links.firstOrNull { it.reachable }?.url ?: "See Studio"})."
            }
            TaskType.PRICE_FINDER -> {
                val links = LiveDiscoveryEngine.findLivePrices(task.targetValue)
                val snippets = links.mapNotNull { it.extractedSnippet }.joinToString("\n")
                snippets.ifBlank { "Verified ${links.count { it.reachable }} store links for \"${task.targetValue}\"." }
            }
            else -> {
                var finalAnswer = ""
                aiOrchestrator.streamConversation(task.prompt, settings).collect { ev ->
                    if (ev is StreamChatEvent.Complete) finalAnswer = ev.fullText
                    if (ev is StreamChatEvent.Error) throw IllegalStateException(ev.message)
                }
                finalAnswer.ifBlank { "Completed queued AI query." }
            }
        }
    }

    // Studio Actions (Image, Audio, Video, Web/PWA Builder, Cloud Creations)
    fun setSelectedAspectRatio(label: String) {
        _uiState.update { it.copy(selectedAspectRatioName = label) }
    }

    fun setSelectedVoice(voiceName: String) {
        _uiState.update { it.copy(selectedVoiceName = voiceName) }
    }

    private fun aspectRatioFromLabel(label: String): AspectRatio = when (label) {
        "16:9" -> AspectRatio.LANDSCAPE_16x9
        "9:16" -> AspectRatio.PORTRAIT_9x16
        else -> AspectRatio.SQUARE_1x1
    }

    fun generateStudioImage(
        prompt: String,
        aspectRatioLabel: String = _uiState.value.selectedAspectRatioName,
        referenceBitmap: Bitmap? = null
    ) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    mediaGenState = MediaGenUiState.Loading(prompt),
                    activeEngineRoute = AiEngineRoute.NANO_BANANA_IMAGE
                )
            }
            val ratio = aspectRatioFromLabel(aspectRatioLabel)
            val res = aiOrchestrator.generateImage(prompt, aspectRatio = ratio, referenceBitmap = referenceBitmap)
            res.onSuccess { bmp ->
                val savedFile = runCatching { JarvisMediaComposer.saveBitmapToFile(appContext, bmp) }.getOrNull()
                _uiState.update { it.copy(mediaGenState = MediaGenUiState.Success(bmp, prompt, savedFile?.absolutePath)) }
                creationsRepository.saveCreation(
                    mediaType = CreationMediaType.IMAGE,
                    prompt = prompt,
                    title = prompt.take(64),
                    summary = if (referenceBitmap != null) "Edited ($aspectRatioLabel)" else "Generated ($aspectRatioLabel)",
                    modelUsed = "gemini-nano-banana-2.1",
                    localFilePath = savedFile?.absolutePath
                )
            }.onFailure { err ->
                _uiState.update {
                    it.copy(mediaGenState = MediaGenUiState.Error(err.localizedMessage ?: "Image generation failed."))
                }
            }
        }
    }

    fun generateStudioAudio(
        prompt: String,
        voiceName: String = _uiState.value.selectedVoiceName,
        moodStyle: String = ""
    ) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    audioGenState = AudioGenUiState.Loading(prompt, voiceName),
                    activeEngineRoute = AiEngineRoute.GEMINI_AUDIO_STUDIO
                )
            }
            val res = aiOrchestrator.generateAudio(
                context = appContext,
                promptOrScript = prompt,
                voiceName = voiceName,
                moodStyle = moodStyle
            )
            res.onSuccess { aud ->
                _uiState.update {
                    it.copy(
                        audioGenState = AudioGenUiState.Success(
                            audioFilePath = aud.audioFile.absolutePath,
                            mimeType = aud.mimeType,
                            voiceName = aud.voiceName,
                            prompt = prompt,
                            durationSec = aud.durationSeconds
                        )
                    )
                }
                creationsRepository.saveCreation(
                    mediaType = CreationMediaType.AUDIO,
                    prompt = prompt,
                    title = "Voice: ${aud.voiceName}",
                    summary = if (moodStyle.isNotBlank()) "$moodStyle • ${prompt.take(140)}" else prompt.take(160),
                    modelUsed = "gemini-3.1-flash-tts-preview",
                    voiceName = aud.voiceName,
                    durationSeconds = aud.durationSeconds,
                    localFilePath = aud.audioFile.absolutePath
                )
            }.onFailure { err ->
                _uiState.update {
                    it.copy(audioGenState = AudioGenUiState.Error(err.localizedMessage ?: "Audio generation failed."))
                }
            }
        }
    }

    fun generateStudioVideo(
        prompt: String,
        voiceName: String = _uiState.value.selectedVoiceName
    ) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    videoGenState = VideoGenUiState.Loading(prompt, "1/4 Directing storyboard…"),
                    activeEngineRoute = AiEngineRoute.GEMINI_VIDEO_STUDIO
                )
            }
            val res = aiOrchestrator.generateVideo(
                context = appContext,
                prompt = prompt,
                voiceName = voiceName,
                settings = _uiState.value.settings,
                onProgress = { stage ->
                    _uiState.update { it.copy(videoGenState = VideoGenUiState.Loading(prompt, stage)) }
                }
            )
            res.onSuccess { vid ->
                _uiState.update {
                    it.copy(
                        videoGenState = VideoGenUiState.Success(
                            videoFilePath = vid.videoFile.absolutePath,
                            thumbnail = vid.thumbnail,
                            title = vid.title,
                            summary = vid.summary,
                            voiceName = vid.voiceName,
                            prompt = prompt,
                            durationSec = vid.durationSeconds
                        )
                    )
                }
                creationsRepository.saveCreation(
                    mediaType = CreationMediaType.VIDEO,
                    prompt = prompt,
                    title = vid.title,
                    summary = vid.summary,
                    modelUsed = "gemini-3.8-flash + nano-banana-2.1",
                    voiceName = vid.voiceName,
                    durationSeconds = vid.durationSeconds,
                    localFilePath = vid.videoFile.absolutePath
                )
            }.onFailure { err ->
                _uiState.update {
                    it.copy(videoGenState = VideoGenUiState.Error(err.localizedMessage ?: "Video generation failed."))
                }
            }
        }
    }

    fun deleteCloudCreation(creationId: String) {
        viewModelScope.launch {
            creationsRepository.deleteCreation(creationId)
        }
    }

    fun buildStudioWebsite(description: String) {
        if (description.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(webBuildState = WebBuildUiState.Loading(description), activeEngineRoute = AiEngineRoute.WEB_ARCHITECT) }
            val res = aiOrchestrator.buildInteractiveHtmlApp(description)
            res.onSuccess { html ->
                _uiState.update { it.copy(webBuildState = WebBuildUiState.Success(html, description)) }
                creationsRepository.saveCreation(
                    mediaType = CreationMediaType.WEB_APP,
                    prompt = description,
                    title = description.take(64),
                    summary = html.take(240),
                    modelUsed = "gemini-3.8-flash"
                )
            }.onFailure { err ->
                _uiState.update {
                    it.copy(webBuildState = WebBuildUiState.Error(err.localizedMessage ?: "Website build failed."))
                }
            }
        }
    }

    fun analyzeStudioImage(bitmap: Bitmap, prompt: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(visionState = VisionScanUiState.Loading(bitmap, prompt), activeEngineRoute = AiEngineRoute.GEMINI_VISION) }
            val res = aiOrchestrator.analyzeImage(bitmap, prompt, _uiState.value.settings)
            _uiState.update { state ->
                state.copy(
                    visionState = res.fold(
                        onSuccess = { text -> VisionScanUiState.Success(bitmap, text) },
                        onFailure = { err -> VisionScanUiState.Error(err.localizedMessage ?: "Vision analysis failed.") }
                    )
                )
            }
        }
    }

    fun runLiveDiscovery(query: String, isPriceSearch: Boolean) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(discoveryLoading = true, discoverySummary = null, activeEngineRoute = AiEngineRoute.LIVE_DISCOVERY) }
            val links = if (isPriceSearch) LiveDiscoveryEngine.findLivePrices(query) else LiveDiscoveryEngine.discoverAndVerifyApp(query)
            val reachableCount = links.count { it.reachable }
            _uiState.update {
                it.copy(
                    discoveryLoading = false,
                    discoveryLinks = links,
                    discoverySummary = "Live check complete: $reachableCount of ${links.size} sources verified online."
                )
            }
        }
    }

    fun openExternalUrl(url: String) {
        connector.openWebsite(url)
    }

    // Connected Accounts Vault (Airbnb & Banking)
    fun createVaultUnlockIntent(): Intent? = accountsManager.createUnlockIntent()
    fun onVaultUnlockedResult(unlocked: Boolean) {
        accountsManager.markVaultUnlocked(unlocked)
        _uiState.update {
            it.copy(accountStatusMessage = if (unlocked) "Vault unlocked via device credential." else "Vault remained locked.")
        }
    }
    fun openAirbnbSection(url: String) {
        val res = accountsManager.openAirbnb(url)
        _uiState.update { it.copy(airbnbStatusMessage = res.getOrNull() ?: res.exceptionOrNull()?.localizedMessage) }
    }
    fun syncAirbnbIcal(urlInput: String) {
        accountsManager.saveAirbnbIcalUrl(urlInput)
        viewModelScope.launch {
            _uiState.update { it.copy(airbnbSyncLoading = true, airbnbStatusMessage = null) }
            val res = accountsManager.syncAirbnbIcalFeed()
            _uiState.update {
                it.copy(
                    airbnbSyncLoading = false,
                    airbnbStatusMessage = res.fold(
                        onSuccess = { list -> "Synced ${list.size} reservation(s) live from Airbnb iCal." },
                        onFailure = { err -> err.localizedMessage ?: "Unable to sync Airbnb calendar." }
                    )
                )
            }
        }
    }
    fun addAndVerifyBankPortal(name: String, url: String) {
        viewModelScope.launch {
            val res = accountsManager.verifyAndSaveBankPortal(name, url)
            _uiState.update {
                it.copy(
                    accountStatusMessage = res.fold(
                        onSuccess = { entry -> "Verified TLS & saved ${entry.bankName} (${entry.url})." },
                        onFailure = { err -> err.localizedMessage ?: "Bank portal rejected." }
                    )
                )
            }
        }
    }
    fun openBankPortalVerified(url: String) {
        viewModelScope.launch {
            val res = accountsManager.verifyAndOpenPortal(url)
            _uiState.update { it.copy(accountStatusMessage = res.getOrNull() ?: res.exceptionOrNull()?.localizedMessage) }
        }
    }
    fun removeBankPortal(url: String) = accountsManager.removeBankPortal(url)

    // Smart Home & Google Home actions
    fun openGoogleHomeApp() {
        val res = smartHomeManager.openGoogleHomeApp()
        _uiState.update { it.copy(smartHomeStatusMessage = res.getOrNull() ?: res.exceptionOrNull()?.localizedMessage) }
    }
    fun openSystemDeviceControls() {
        val res = smartHomeManager.openSystemDeviceControls()
        _uiState.update { it.copy(smartHomeStatusMessage = res.getOrNull() ?: res.exceptionOrNull()?.localizedMessage) }
    }
    fun relayToGoogleAssistant(commandPhrase: String) {
        val res = smartHomeManager.relayCommandToGoogleAssistant(commandPhrase)
        _uiState.update { it.copy(smartHomeStatusMessage = res.getOrNull() ?: res.exceptionOrNull()?.localizedMessage) }
    }
    fun scanLanSmartDevices() = smartHomeManager.startLanDiscovery()
    fun saveSmartHubConfig(url: String, token: String) {
        smartHomeManager.saveHubConfig(url, token)
        _uiState.update { it.copy(smartHomeStatusMessage = "Saved Home Assistant / LAN Hub settings.") }
    }
    fun addSmartDevice(name: String, room: String, type: SmartDeviceType, entityId: String) =
        smartHomeManager.addCustomDevice(name, room, type, entityId)
    fun removeSmartDevice(id: String) = smartHomeManager.removeDevice(id)
    fun toggleSmartDevicePower(id: String) { viewModelScope.launch { _uiState.update { it.copy(smartHomeStatusMessage = smartHomeManager.togglePower(id).summary) } } }
    fun setWashingCycle(id: String, mode: String, toggleRun: Boolean = false) { viewModelScope.launch { _uiState.update { it.copy(smartHomeStatusMessage = smartHomeManager.setWashingCycle(id, mode, toggleRun).summary) } } }
    fun adjustWashingRpm(id: String, rpm: Int) { viewModelScope.launch { _uiState.update { it.copy(smartHomeStatusMessage = smartHomeManager.adjustWashingSpinRpm(id, rpm).summary) } } }
    fun adjustSmartTemperature(id: String, temp: Int, isFreezer: Boolean = false) { viewModelScope.launch { _uiState.update { it.copy(smartHomeStatusMessage = smartHomeManager.adjustTemperature(id, temp, isFreezer).summary) } } }
    fun setSmartMode(id: String, mode: String) { viewModelScope.launch { _uiState.update { it.copy(smartHomeStatusMessage = smartHomeManager.setDeviceMode(id, mode).summary) } } }
    fun adjustTvVolume(id: String, delta: Int, toggleMute: Boolean = false) { viewModelScope.launch { _uiState.update { it.copy(smartHomeStatusMessage = smartHomeManager.adjustTvVolume(id, delta, toggleMute).summary) } } }
    fun adjustSmartLevel(id: String, level: Int) { viewModelScope.launch { _uiState.update { it.copy(smartHomeStatusMessage = smartHomeManager.adjustLevelOrSpeed(id, level).summary) } } }
    fun toggleSmartDoorLock(id: String) { viewModelScope.launch { _uiState.update { it.copy(smartHomeStatusMessage = smartHomeManager.toggleDoorLock(id).summary) } } }

    // Connector Hub actions
    fun refreshConnectors() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = connector.listInstalledApps()
            val bankApps = accountsManager.detectInstalledBankApps(apps)
            val airbnb = accountsManager.isAirbnbAppInstalled()
            val websites = connector.getConnectedWebsites()
            _uiState.update {
                it.copy(
                    installedApps = apps,
                    detectedBankApps = bankApps,
                    airbnbInstalled = airbnb,
                    connectedWebsites = websites
                )
            }
        }
    }
    fun launchInstalledApp(packageName: String) { connector.launchApp(packageName) }
    fun togglePinApp(packageName: String) {
        connector.togglePinApp(packageName)
        refreshConnectors()
    }
    fun addAndConnectWebsite(title: String, rawUrl: String) {
        if (rawUrl.isBlank()) return
        val entry = connector.saveWebsite(title, rawUrl)
        refreshConnectors()
        if (taskManager.canExecuteOnlineNow()) {
            connector.openWebsite(entry.url)
        } else {
            viewModelScope.launch {
                taskManager.enqueuePendingTask(
                    prompt = "Connect to ${entry.url}",
                    taskType = TaskType.WEB_CONNECT,
                    targetValue = entry.url
                )
            }
        }
    }
    fun removeWebsite(url: String) {
        connector.removeWebsite(url)
        refreshConnectors()
    }

    // Queue actions
    fun flushQueueNow() = taskManager.triggerPendingFlush()
    fun retryQueueTask(id: Long) { viewModelScope.launch { taskManager.retryTask(id) } }
    fun promoteQueueTaskPriority(id: Long) { viewModelScope.launch { taskManager.promotePriority(id) } }
    fun cancelQueueTask(id: Long) { viewModelScope.launch { taskManager.cancelTask(id) } }
    fun deleteQueueTask(id: Long) { viewModelScope.launch { taskManager.deleteTask(id) } }
    fun clearCompletedTasks() { viewModelScope.launch { taskManager.clearCompleted() } }
    fun dismissVoiceNotice() { _uiState.update { it.copy(voiceNotice = null) } }

    override fun onCleared() {
        // The engine belongs to the foreground service now — just unbind.
        // The service keeps listening in the background when wake word is on.
        runCatching {
            if (voiceServiceBound) appContext.unbindService(voiceConnection)
        }
        voiceServiceBound = false
        voiceService = null
        smartHomeManager.release()
        taskManager.release()
        bluetoothManager.release()
        wifiManager.release()
        phoneControlManager.release()
        super.onCleared()
    }

    companion object {
        fun provideFactory(
            application: Application,
            currentUserId: String,
            creationsRepository: JarvisCreationsRepository,
            userDisplayName: String? = null,
            userEmail: String? = null,
            userPhotoUrl: String? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(JarvisAgentViewModel::class.java)) {
                    return JarvisAgentViewModel(
                        application = application,
                        currentUserId = currentUserId,
                        creationsRepository = creationsRepository,
                        userDisplayName = userDisplayName,
                        userEmail = userEmail,
                        userPhotoUrl = userPhotoUrl
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
