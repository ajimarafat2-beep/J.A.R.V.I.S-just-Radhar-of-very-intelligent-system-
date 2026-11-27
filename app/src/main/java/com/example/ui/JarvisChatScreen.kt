package com.example.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.agent.AgentActionShortcut
import com.example.agent.AgentMessage
import com.example.agent.AgentRoute
import com.example.agent.JarvisAgentUiState
import com.example.agent.MessageSender
import com.example.ui.theme.JarvisAlertRed
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanDim
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisNavy
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.voice.VoicePhase

import com.example.ai.JarvisMediaComposer
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisChatScreen(
    uiState: JarvisAgentUiState,
    onSendMessage: (String, Bitmap?) -> Unit,
    onSpeakMessage: (String) -> Unit,
    onToggleLiveVoice: () -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onToggleEdgeBorder: () -> Unit,
    onNavigateRoute: (AgentRoute) -> Unit,
    onOpenUrl: (String) -> Unit,
    onRequestWirelessPermissions: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onRequestCameraAndMicPermission: () -> Unit,
    onDismissVoiceNotice: () -> Unit,
    onStopGeneration: () -> Unit,
    modifier: Modifier = Modifier,
    voiceRmsProvider: (() -> Float)? = null
) {
    var inputText by remember { mutableStateOf("") }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showQuickToolsSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val effectiveRmsProvider: () -> Float = voiceRmsProvider ?: { uiState.voiceRmsLevel }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                JarvisMediaComposer.decodeSampledBitmapFromUri(context, uri)?.let {
                    attachedBitmap = it
                }
            }
        }
    }

    val cameraSnapLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) attachedBitmap = bmp
    }

    // Auto-scroll (B14): only follow the conversation while the user is
    // already at the bottom. A new message animates down; a streaming reply
    // follows instantly as its text grows. If the user scrolled up to read
    // history, leave them alone.
    var previousSize by remember { mutableStateOf(0) }
    val lastMessageTextLength = uiState.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(uiState.messages.size, lastMessageTextLength) {
        if (uiState.messages.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            val wasAtBottom = lastVisible == null ||
                lastVisible.index >= (previousSize - 1).coerceAtLeast(0)
            if (wasAtBottom) {
                if (uiState.messages.size == previousSize) {
                    listState.scrollToItem(uiState.messages.lastIndex)
                } else {
                    listState.animateScrollToItem(uiState.messages.lastIndex)
                }
            }
        }
        previousSize = uiState.messages.size
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisNavy),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (uiState.voicePhase != VoicePhase.IDLE ||
            uiState.settings.wakeWordEnabled ||
            uiState.liveTranscript.isNotBlank() ||
            uiState.voiceNotice != null
        ) {
            LiveVoiceHudStrip(
                voicePhase = uiState.voicePhase,
                wakeWord = uiState.settings.wakeWord,
                wakeWordEnabled = uiState.settings.wakeWordEnabled,
                liveTranscript = uiState.liveTranscript,
                rmsProvider = effectiveRmsProvider,
                voiceNotice = uiState.voiceNotice,
                onTapInterrupt = onToggleLiveVoice,
                onDismissNotice = onDismissVoiceNotice
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (uiState.messages.isEmpty()) {
                JarvisReplicaWelcomeCanvas(
                    uiState = uiState,
                    voiceRmsProvider = effectiveRmsProvider,
                    onPromptClick = { onSendMessage(it, null) },
                    onOrbTap = {
                        if (!uiState.micPermissionGranted) onRequestMicPermission() else onToggleLiveVoice()
                    },
                    onToggleWakeWord = {
                        if (!uiState.micPermissionGranted && !uiState.settings.wakeWordEnabled) {
                            onRequestMicPermission()
                        } else {
                            onToggleWakeWord(!uiState.settings.wakeWordEnabled)
                        }
                    },
                    onToggleEdgeBorder = onToggleEdgeBorder,
                    onNavigateRoute = onNavigateRoute,
                    onGrantPermissions = onRequestWirelessPermissions,
                    onGrantCameraAndMic = onRequestCameraAndMicPermission
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { msg ->
                        JarvisChatMessageItem(
                            message = msg,
                            onCopy = { clipboard.setText(AnnotatedString(msg.text)) },
                            onSpeak = { onSpeakMessage(msg.text) },
                            onOpenUrl = onOpenUrl,
                            onShortcut = { shortcut ->
                                if (shortcut.requestPermissions) {
                                    onRequestWirelessPermissions()
                                } else {
                                    shortcut.route?.let(onNavigateRoute)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Google Gemini-style Prompt Suggestion Strip above composer
        if (uiState.messages.isEmpty()) {
            Row(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                JarvisQuickChip("Create Video", Icons.Filled.Videocam) { onNavigateRoute(AgentRoute.STUDIO) }
                JarvisQuickChip("Create Audio", Icons.Filled.AudioFile) { onNavigateRoute(AgentRoute.STUDIO) }
                JarvisQuickChip("Create Image", Icons.Filled.AutoAwesome) { onNavigateRoute(AgentRoute.STUDIO) }
                JarvisQuickChip("Search & Code", Icons.Filled.Public) { onSendMessage("What is today's top tech news and stock highlights?", null) }
                JarvisQuickChip("Build Website", Icons.Filled.Apps) { onNavigateRoute(AgentRoute.STUDIO) }
                JarvisQuickChip("Phone Control", Icons.Filled.PhoneAndroid) { onNavigateRoute(AgentRoute.PHONE_CONTROL) }
                JarvisQuickChip("24/7 Virus Shield", Icons.Filled.Shield) { onNavigateRoute(AgentRoute.SECURITY) }
            }
        }

        // Google Gemini Floating Pill Composer
        Surface(
            color = JarvisSurface,
            border = BorderStroke(
                1.dp,
                when {
                    uiState.voicePhase == VoicePhase.COMMAND_LISTENING -> JarvisGold
                    attachedBitmap != null -> JarvisCyan
                    else -> JarvisBorder
                }
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .widthIn(max = 760.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                attachedBitmap?.let { bmp ->
                    val attachedImage = remember(bmp) { bmp.asImageBitmap() }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp)
                    ) {
                        Image(
                            bitmap = attachedImage,
                            contentDescription = "Attached photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Image attached (J.A.R.V.I.S Vision)",
                            style = MaterialTheme.typography.labelMedium,
                            color = JarvisCyan,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { attachedBitmap = null }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove image", tint = JarvisTextMuted)
                        }
                    }
                }

                // In-card mode chips (NUSU-style): Jarvis mode + Code mode
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = JarvisCyan.copy(alpha = 0.18f),
                        modifier = Modifier.testTag("composer_chip_jarvis")
                    ) {
                        Text(
                            text = "Jarvis",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = JarvisCyan,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = JarvisSurfaceElevated,
                        modifier = Modifier
                            .testTag("composer_chip_code")
                            .clickable {
                                if (inputText.isBlank()) inputText = "Write code for "
                            }
                    ) {
                        Text(
                            text = "<> Code",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = JarvisTextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Exact Google Gemini 2-row Capsule Composer: text input above, action icons below
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "Message Jarvis",
                            color = JarvisTextMuted,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank() || attachedBitmap != null) {
                                onSendMessage(inputText, attachedBitmap)
                                inputText = ""
                                attachedBitmap = null
                            }
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary,
                        cursorColor = JarvisCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .testTag("chat_message_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 10.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showQuickToolsSheet = true },
                        modifier = Modifier.size(44.dp).testTag("chat_tools_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.chat_tools),
                            tint = JarvisTextMuted
                        )
                    }

                    IconButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.size(44.dp).testTag("chat_photo_attach_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = "Attach photo for J.A.R.V.I.S Vision",
                            tint = JarvisTextMuted
                        )
                    }

                    IconButton(
                        onClick = { cameraSnapLauncher.launch(null) },
                        modifier = Modifier.size(44.dp).testTag("chat_camera_snap_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Snap photo via Camera",
                            tint = JarvisTextMuted
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    val isVoiceLive = uiState.voicePhase == VoicePhase.COMMAND_LISTENING ||
                        uiState.voicePhase == VoicePhase.SPEAKING
                    IconButton(
                        onClick = {
                            if (!uiState.micPermissionGranted) onRequestMicPermission() else onToggleLiveVoice()
                        },
                        modifier = Modifier.size(44.dp).testTag("chat_voice_button")
                    ) {
                        Icon(
                            imageVector = if (isVoiceLive) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = stringResource(R.string.chat_voice_input),
                            tint = if (isVoiceLive) JarvisGold else JarvisTextPrimary
                        )
                    }

                    val canSend = inputText.isNotBlank() || attachedBitmap != null
                    val isGenerating = uiState.isProcessing
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                brush = if (!isGenerating && canSend) {
                                    Brush.linearGradient(colors = listOf(JarvisGold, JarvisCyan))
                                } else {
                                    Brush.linearGradient(
                                        colors = listOf(JarvisSurfaceElevated, JarvisSurfaceElevated)
                                    )
                                },
                                shape = CircleShape
                            )
                            .testTag("chat_send_button")
                            .clickable {
                                if (isGenerating) {
                                    onStopGeneration()
                                } else if (canSend) {
                                    onSendMessage(inputText, attachedBitmap)
                                    inputText = ""
                                    attachedBitmap = null
                                } else {
                                    if (!uiState.micPermissionGranted) onRequestMicPermission() else onToggleLiveVoice()
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isGenerating) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = "Stop generating",
                                    tint = JarvisTextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else if (canSend) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(R.string.chat_send),
                                    tint = JarvisNavy,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                JarvisHologramOrb(
                                    size = 28.dp,
                                    activeGold = true,
                                    voiceRmsLevel = if (isVoiceLive) uiState.voiceRmsLevel else 0f
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQuickToolsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuickToolsSheet = false },
            containerColor = JarvisSurface,
            contentColor = JarvisTextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "J.A.R.V.I.S // Full Phone & AI Tools",
                    style = MaterialTheme.typography.titleMedium,
                    color = JarvisGold
                )
                QuickSheetItem(Icons.Filled.PhoneAndroid, "Full Phone Control & Live CameraX") {
                    showQuickToolsSheet = false
                    onNavigateRoute(AgentRoute.PHONE_CONTROL)
                }
                QuickSheetItem(Icons.Filled.AutoAwesome, "AI Studio (Image Gen, Web Builder, Prices)") {
                    showQuickToolsSheet = false
                    onNavigateRoute(AgentRoute.STUDIO)
                }
                QuickSheetItem(Icons.Filled.PhotoLibrary, "Upload Photo for Privacy-Safe Vision") {
                    showQuickToolsSheet = false
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                QuickSheetItem(Icons.Filled.CameraAlt, "Snap Camera Photo for Vision Scan") {
                    showQuickToolsSheet = false
                    cameraSnapLauncher.launch(null)
                }
                QuickSheetItem(Icons.Filled.AccountBalance, "Airbnb & Bank Account Vault") {
                    showQuickToolsSheet = false
                    onNavigateRoute(AgentRoute.CONNECTORS)
                }
                QuickSheetItem(Icons.Filled.Home, "Google Home & Smart Appliances") {
                    showQuickToolsSheet = false
                    onNavigateRoute(AgentRoute.SMART_HOME)
                }
                QuickSheetItem(Icons.Filled.GraphicEq, "Agent & Live Voice Clarity Check") {
                    showQuickToolsSheet = false
                    onSendMessage("Check voice", null)
                }
                QuickSheetItem(Icons.Filled.Shield, "Cybersecurity & Scam Shield") {
                    showQuickToolsSheet = false
                    onNavigateRoute(AgentRoute.SECURITY)
                }
                QuickSheetItem(Icons.Filled.Queue, "Persistent Task Queue (${uiState.pendingTaskCount})") {
                    showQuickToolsSheet = false
                    onNavigateRoute(AgentRoute.QUEUE)
                }
                QuickSheetItem(Icons.Filled.Bluetooth, "Bluetooth & Wi-Fi Scanner") {
                    showQuickToolsSheet = false
                    onNavigateRoute(AgentRoute.BLUETOOTH)
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JarvisReplicaWelcomeCanvas(
    uiState: JarvisAgentUiState,
    voiceRmsProvider: () -> Float,
    onPromptClick: (String) -> Unit,
    onOrbTap: () -> Unit,
    onToggleWakeWord: () -> Unit,
    onToggleEdgeBorder: () -> Unit,
    onNavigateRoute: (AgentRoute) -> Unit,
    onGrantPermissions: () -> Unit,
    onGrantCameraAndMic: () -> Unit
) {
    val orbGold = uiState.isProcessing ||
        uiState.voicePhase == VoicePhase.COMMAND_LISTENING ||
        uiState.voicePhase == VoicePhase.SPEAKING ||
        uiState.edgeBorderMode

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 760.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .clickable(onClick = onOrbTap)
                        .testTag("welcome_jarvis_orb")
                ) {
                    JarvisHologramOrb(
                        size = 86.dp,
                        activeGold = uiState.isProcessing ||
                            uiState.voicePhase == VoicePhase.COMMAND_LISTENING ||
                            uiState.voicePhase == VoicePhase.SPEAKING,
                        voiceRmsProvider = {
                            if (uiState.voicePhase == VoicePhase.COMMAND_LISTENING) voiceRmsProvider() else 0f
                        }
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                val greetingName = uiState.signedInDisplayName
                    ?.trim()
                    ?.split(" ")
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (greetingName != null) "Hello, $greetingName" else stringResource(R.string.chat_welcome_title),
                        style = TextStyle(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.SemiBold,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFFFF59D), JarvisGold, Color(0xFFFFB74D))
                            )
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Your Personal AI Agent & Assistant — Ready to plan & act",
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisTextMuted
                    )
                }
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WelcomeSuggestionChip(
                    text = "Latest news today",
                    onClick = { onPromptClick("What is the latest news today?") }
                )
                WelcomeSuggestionChip(
                    text = "Scan my phone",
                    onClick = { onPromptClick("Scan phone for virus and check battery") }
                )
                WelcomeSuggestionChip(
                    text = "Write a short poem",
                    onClick = { onPromptClick("Write a short poem about the morning") }
                )
            }
        }
    }
}

@Composable
private fun WelcomeSuggestionChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = JarvisSurface,
        border = BorderStroke(1.dp, JarvisBorder),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.testTag("welcome_suggestion_chip")
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = JarvisTextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun JarvisSuggestionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        color = JarvisSurface,
        border = BorderStroke(1.dp, JarvisBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = JarvisTextPrimary)
        }
    }
}

@Composable
private fun JarvisQuickChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        color = JarvisSurface,
        border = BorderStroke(1.dp, JarvisBorder),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = JarvisTextPrimary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JarvisChatMessageItem(
    message: AgentMessage,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onShortcut: (AgentActionShortcut) -> Unit
) {
    val context = LocalContext.current
    if (message.sender == MessageSender.USER) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = JarvisSurfaceElevated,
                shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    message.attachedBitmap?.let { bmp ->
                        val userImage = remember(bmp) { bmp.asImageBitmap() }
                        Image(
                            bitmap = userImage,
                            contentDescription = "User uploaded image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = JarvisTextPrimary
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.padding(top = 2.dp)) {
                JarvisHologramOrb(
                    size = 26.dp,
                    activeGold = message.isStreaming,
                    animate = message.isStreaming,
                    voiceRmsLevel = if (message.isStreaming) 0.45f else 0f
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "J.A.R.V.I.S",
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisGold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = JarvisSurface,
                        border = BorderStroke(1.dp, JarvisBorder),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = if (message.isStreaming) "${message.engineRoute.badgeLabel} • Streaming" else message.engineRoute.badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisTextMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = remember(message.timestampMillis) {
                            SimpleDateFormat("h:mm a", Locale.getDefault())
                                .format(Date(message.timestampMillis))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTextMuted
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                val videoPath = message.attachedVideoPath
                if (!videoPath.isNullOrBlank()) {
                    JarvisVideoPlayerCard(
                        videoPath = videoPath,
                        title = message.mediaTitle ?: "J.A.R.V.I.S AI Video",
                        subtitle = null,
                        thumbnail = message.attachedBitmap,
                        onShare = {
                            JarvisMediaComposer.createShareFileIntent(context, videoPath, "video/mp4", message.mediaTitle ?: "AI Video")
                                ?.let { context.startActivity(Intent.createChooser(it, "Share AI Video")) }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    message.attachedBitmap?.let { bmp ->
                        val generatedImage = remember(bmp) { bmp.asImageBitmap() }
                        Image(
                            bitmap = generatedImage,
                            contentDescription = "Generated image",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                val audioPath = message.attachedAudioPath
                if (!audioPath.isNullOrBlank()) {
                    JarvisAudioPlayerCard(
                        audioPath = audioPath,
                        title = message.mediaTitle ?: "J.A.R.V.I.S AI Audio",
                        subtitle = null,
                        onShare = {
                            JarvisMediaComposer.createShareFileIntent(context, audioPath, "audio/wav", message.mediaTitle ?: "AI Audio")
                                ?.let { context.startActivity(Intent.createChooser(it, "Share AI Audio")) }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (message.agentSteps.isNotEmpty()) {
                    Surface(
                        color = JarvisSurfaceElevated,
                        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("agent_steps_trace_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Agent Actions Executed (${message.agentSteps.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                            message.agentSteps.forEach { step ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = JarvisEmerald,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = step,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JarvisTextMuted
                                    )
                                }
                            }
                        }
                    }
                }

                if (message.isError) {
                    Text(
                        text = message.text.ifBlank { "Thinking…" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = JarvisAlertRed
                    )
                } else {
                    Markdown(
                        content = message.text.ifBlank { "Thinking…" },
                        colors = markdownColor(text = JarvisTextPrimary)
                    )
                }

                if (message.groundingLinks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        message.groundingLinks.forEach { (title, url) ->
                            Surface(
                                color = JarvisSurface,
                                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.clickable { onOpenUrl(url) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = title.take(28),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisCyan
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Filled.OpenInNew,
                                        contentDescription = "Open source link",
                                        tint = JarvisCyan,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                message.actionShortcut?.let { shortcut ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onShortcut(shortcut) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisSurfaceElevated,
                            contentColor = JarvisCyan
                        ),
                        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(text = shortcut.label, style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (!message.isStreaming && message.text.isNotBlank()) {
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        IconButton(onClick = onCopy, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.chat_copy),
                                tint = JarvisTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onSpeak, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = stringResource(R.string.chat_speak),
                                tint = JarvisTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveVoiceHudStrip(
    voicePhase: VoicePhase,
    wakeWord: String,
    wakeWordEnabled: Boolean,
    liveTranscript: String,
    rmsProvider: () -> Float,
    voiceNotice: String?,
    onTapInterrupt: () -> Unit,
    onDismissNotice: () -> Unit
) {
    val label = when {
        voicePhase == VoicePhase.COMMAND_LISTENING && liveTranscript.isNotBlank() ->
            "Listening: \"$liveTranscript\""
        voicePhase == VoicePhase.COMMAND_LISTENING ->
            "Listening via SpeechRecognizer — Speak a command, Sir…"
        voiceNotice != null -> voiceNotice
        voicePhase == VoicePhase.SPEAKING -> "Speaking… Tap mic or orb to interrupt"
        voicePhase == VoicePhase.WAKE_LISTENING || wakeWordEnabled -> "Wake Word Active: Say \"$wakeWord\""
        else -> ""
    }
    if (label.isBlank()) return

    val tint = when (voicePhase) {
        VoicePhase.COMMAND_LISTENING -> JarvisGold
        VoicePhase.SPEAKING -> JarvisCyan
        VoicePhase.WAKE_LISTENING -> JarvisEmerald
        VoicePhase.IDLE -> JarvisCyan
    }

    Surface(
        color = JarvisSurface,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .clickable(onClick = onTapInterrupt)
            .testTag("live_voice_hud_strip")
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label, style = MaterialTheme.typography.bodySmall, color = tint, modifier = Modifier.weight(1f))
                if (voiceNotice != null && voicePhase != VoicePhase.COMMAND_LISTENING) {
                    IconButton(onClick = onDismissNotice, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss notice", tint = JarvisTextMuted)
                    }
                }
            }
            if (voicePhase == VoicePhase.COMMAND_LISTENING) {
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                ) {
                    drawRect(color = JarvisSurfaceElevated)
                    val fraction = (0.15f + rmsProvider() * 0.85f).coerceIn(0.15f, 1f)
                    drawRect(
                        color = JarvisGold,
                        size = size.copy(width = size.width * fraction)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSheetItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        color = JarvisSurfaceElevated,
        border = BorderStroke(1.dp, JarvisBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = JarvisCyan)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = JarvisTextPrimary)
        }
    }
}

@Composable
private fun HudStatusChip(
    text: String,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = JarvisSurface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(999.dp),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
