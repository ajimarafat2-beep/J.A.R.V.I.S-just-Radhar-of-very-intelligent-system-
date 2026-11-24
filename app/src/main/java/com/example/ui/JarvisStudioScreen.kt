package com.example.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.agent.AudioGenUiState
import com.example.agent.MediaGenUiState
import com.example.agent.VideoGenUiState
import com.example.agent.VisionScanUiState
import com.example.agent.WebBuildUiState
import com.example.ai.LiveVerifiedLink
import com.example.data.StudioCreation
import com.example.ui.theme.JarvisAlertRed
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisNavy
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JarvisStudioScreen(
    mediaGenState: MediaGenUiState,
    audioGenState: AudioGenUiState,
    videoGenState: VideoGenUiState,
    webBuildState: WebBuildUiState,
    visionState: VisionScanUiState,
    selectedAspectRatio: String,
    selectedVoiceName: String,
    cloudCreations: List<StudioCreation>,
    cloudCreationsError: String?,
    discoveryLinks: List<LiveVerifiedLink>,
    discoveryLoading: Boolean,
    discoverySummary: String?,
    onSelectAspectRatio: (String) -> Unit,
    onSelectVoiceName: (String) -> Unit,
    onGenerateImage: (String, String, Bitmap?) -> Unit,
    onGenerateAudio: (String, String, String) -> Unit,
    onGenerateVideo: (String, String) -> Unit,
    onDeleteCloudCreation: (String) -> Unit,
    onBuildWebsite: (String) -> Unit,
    onAnalyzeImage: (Bitmap, String) -> Unit,
    onRunLiveDiscovery: (String, Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var mediaMode by remember { mutableStateOf(StudioMediaMode.IMAGE) }
    var referenceEditBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var webPrompt by remember { mutableStateOf("") }
    var showHtmlCode by remember { mutableStateOf(false) }
    var visionPrompt by remember { mutableStateOf("Describe everything in this image, read any text, and check safety/posture.") }
    var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var discoveryQuery by remember { mutableStateOf("") }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            }.getOrNull()
            if (bmp != null) pickedBitmap = bmp
        }
    }

    val referenceImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            }.getOrNull()
            if (bmp != null) referenceEditBitmap = bmp
        }
    }

    val cameraSnapLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) pickedBitmap = bmp
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisNavy),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JarvisHologramOrb(size = 24.dp, activeGold = true)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "J.A.R.V.I.S // AI STUDIO & DISCOVERY",
                    style = MaterialTheme.typography.titleLarge,
                    color = JarvisCyan
                )
            }
            Text(
                text = "Media Studio • Web/PWA Architect • Live Camera & Vision • App & Price Finder",
                style = MaterialTheme.typography.bodySmall,
                color = JarvisTextMuted
            )
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = JarvisSurface,
            contentColor = JarvisCyan,
            edgePadding = 12.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = JarvisGold
                )
            },
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Media Studio") },
                icon = { Icon(Icons.Filled.Image, contentDescription = null) },
                modifier = Modifier.testTag("studio_tab_media")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Web/PWA Builder") },
                icon = { Icon(Icons.Filled.Code, contentDescription = null) },
                modifier = Modifier.testTag("studio_tab_web")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Vision & Game") },
                icon = { Icon(Icons.Filled.Visibility, contentDescription = null) },
                modifier = Modifier.testTag("studio_tab_vision")
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("App & Price") },
                icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.testTag("studio_tab_discovery")
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).widthIn(max = 760.dp).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    item {
                        StudioMediaGeneratorPanel(
                            mode = mediaMode,
                            onModeChange = { mediaMode = it },
                            mediaGenState = mediaGenState,
                            audioGenState = audioGenState,
                            videoGenState = videoGenState,
                            selectedAspectRatio = selectedAspectRatio,
                            onSelectAspectRatio = onSelectAspectRatio,
                            selectedVoiceName = selectedVoiceName,
                            onSelectVoiceName = onSelectVoiceName,
                            referenceBitmap = referenceEditBitmap,
                            onPickReferenceImage = {
                                referenceImagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onClearReferenceImage = { referenceEditBitmap = null },
                            onGenerateImage = onGenerateImage,
                            onGenerateAudio = onGenerateAudio,
                            onGenerateVideo = onGenerateVideo
                        )
                    }
                    item {
                        StudioCloudCreationsCard(
                            creations = cloudCreations,
                            errorMessage = cloudCreationsError,
                            onDeleteCreation = onDeleteCloudCreation
                        )
                    }
                }

                1 -> {
                    item {
                        StudioSectionCard(
                            title = "HTML5 / Three.js / PWA Website Builder",
                            subtitle = "Generate complete responsive web apps from one prompt and preview live inside JARVIS."
                        ) {
                            OutlinedTextField(
                                value = webPrompt,
                                onValueChange = { webPrompt = it },
                                label = { Text("Describe the website or web app to build…") },
                                colors = studioFieldColors(),
                                modifier = Modifier.fillMaxWidth().testTag("studio_web_prompt_input")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { onBuildWebsite(webPrompt) },
                                enabled = webPrompt.isNotBlank() && webBuildState !is WebBuildUiState.Loading,
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisNavy),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("studio_build_web_button")
                            ) {
                                Icon(Icons.Filled.Code, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Build Interactive App", fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            when (webBuildState) {
                                is WebBuildUiState.Idle -> Text(
                                    text = "Try: \"A glowing 3D solar system portfolio website in dark obsidian theme\".",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextMuted
                                )
                                is WebBuildUiState.Loading -> Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = JarvisCyan)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("J.A.R.V.I.S Web Architect is writing HTML5/CSS/JS…", color = JarvisCyan)
                                }
                                is WebBuildUiState.Success -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { showHtmlCode = !showHtmlCode },
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text(if (showHtmlCode) "Show Live Preview" else "View HTML Code", color = JarvisCyan)
                                        }
                                        OutlinedButton(
                                            onClick = { clipboard.setText(AnnotatedString(webBuildState.htmlCode)) },
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = JarvisGold)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Copy HTML", color = JarvisGold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    if (showHtmlCode) {
                                        Surface(
                                            color = JarvisSurfaceElevated,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = webBuildState.htmlCode.take(2500),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = JarvisTextPrimary,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(360.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                        ) {
                                            AndroidView(
                                                factory = { ctx ->
                                                    WebView(ctx).apply {
                                                        settings.javaScriptEnabled = true
                                                        settings.domStorageEnabled = true
                                                    }
                                                },
                                                update = { wv ->
                                                    wv.loadDataWithBaseURL(null, webBuildState.htmlCode, "text/html", "UTF-8", null)
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                                is WebBuildUiState.Error -> Text(
                                    text = webBuildState.message,
                                    color = JarvisAlertRed,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        JarvisCameraCaptureCard(
                            onCapturedBitmap = { bmp ->
                                pickedBitmap = bmp
                                onAnalyzeImage(bmp, visionPrompt)
                            },
                            captureButtonLabel = "Snap & Analyze with J.A.R.V.I.S"
                        )
                    }
                    item {
                        StudioSectionCard(
                            title = "Privacy-Safe Vision, Room Scan & Game Walkthrough",
                            subtitle = "Pick a photo, snap via Camera, or stream CameraX above. Describes objects, scenes, OCR text, and public figures only — never identifies private faces."
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisNavy),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("studio_pick_photo_button")
                                ) {
                                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick Photo", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { cameraSnapLauncher.launch(null) },
                                    border = BorderStroke(1.dp, JarvisGold),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("studio_snap_photo_button")
                                ) {
                                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = JarvisGold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Camera Snap", color = JarvisGold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "Scene & Objects" to "Describe objects, scenes, and landmarks in this photo cleanly.",
                                    "Read Text (OCR)" to "Extract and transcribe all readable text shown in this image.",
                                    "Game Walkthrough" to "Analyze this gameplay screenshot (e.g. Minecraft) and suggest the optimal next steps.",
                                    "Wellness / Posture" to "Check posture/visual cues gently (reminder: not medical advice)."
                                ).forEach { (chipLabel, templatePrompt) ->
                                    FilterChip(
                                        selected = visionPrompt == templatePrompt,
                                        onClick = { visionPrompt = templatePrompt },
                                        label = { Text(chipLabel) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = JarvisCyan,
                                            containerColor = JarvisSurfaceElevated,
                                            labelColor = JarvisTextPrimary
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = visionPrompt,
                                onValueChange = { visionPrompt = it },
                                label = { Text("Vision Question") },
                                colors = studioFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            pickedBitmap?.let { bmp ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Selected preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { onAnalyzeImage(bmp, visionPrompt) },
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("studio_analyze_vision_button")
                                ) {
                                    Text("Analyze with J.A.R.V.I.S Vision", fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            when (visionState) {
                                is VisionScanUiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = JarvisCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Analyzing image via J.A.R.V.I.S Vision…", color = JarvisCyan)
                                }
                                is VisionScanUiState.Success -> Surface(
                                    color = JarvisSurfaceElevated,
                                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.45f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = visionState.analysis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = JarvisTextPrimary,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                                is VisionScanUiState.Error -> Text(visionState.message, color = JarvisAlertRed)
                                is VisionScanUiState.Idle -> Unit
                            }
                        }
                    }
                }

                else -> {
                    item {
                        StudioSectionCard(
                            title = "Live App Discovery & Exact Price Finder",
                            subtitle = "Probes live stores, Play Store, F-Droid, GitHub, Star Tech, Daraz & Amazon with real HTTP checks."
                        ) {
                            OutlinedTextField(
                                value = discoveryQuery,
                                onValueChange = { discoveryQuery = it },
                                singleLine = true,
                                label = { Text("App name or Product (e.g. Sony WH-1000XM5 or CapCut)") },
                                colors = studioFieldColors(),
                                modifier = Modifier.fillMaxWidth().testTag("studio_discovery_input")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onRunLiveDiscovery(discoveryQuery, false) },
                                    enabled = discoveryQuery.isNotBlank() && !discoveryLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisNavy),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("studio_discover_app_button")
                                ) {
                                    Icon(Icons.Filled.Search, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Find App", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { onRunLiveDiscovery(discoveryQuery, true) },
                                    enabled = discoveryQuery.isNotBlank() && !discoveryLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("studio_find_price_button")
                                ) {
                                    Icon(Icons.Filled.ShoppingBag, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Live Prices", fontWeight = FontWeight.Bold)
                                }
                            }
                            if (discoveryLoading) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = JarvisGold)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Verifying live URLs & prices…", color = JarvisGold)
                                }
                            }
                            discoverySummary?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(it, style = MaterialTheme.typography.labelMedium, color = JarvisEmerald)
                            }
                        }
                    }

                    items(discoveryLinks, key = { it.url }) { itemLink ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                            border = BorderStroke(
                                1.dp,
                                if (itemLink.reachable) JarvisEmerald.copy(alpha = 0.55f) else JarvisBorder
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUrl(itemLink.url) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = itemLink.sourceName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = JarvisTextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (itemLink.reachable) "LIVE HTTP ${itemLink.httpStatus}" else "UNREACHABLE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (itemLink.reachable) JarvisEmerald else JarvisAlertRed
                                    )
                                }
                                itemLink.pageTitle?.let {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = JarvisTextMuted)
                                }
                                itemLink.extractedSnippet?.let {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(it, style = MaterialTheme.typography.bodyMedium, color = JarvisGold, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = itemLink.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisCyan,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Filled.OpenInNew, contentDescription = "Open link", tint = JarvisCyan, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, JarvisBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = JarvisTextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = JarvisTextMuted)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun studioFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = JarvisCyan,
    unfocusedBorderColor = JarvisBorder,
    focusedTextColor = JarvisTextPrimary,
    unfocusedTextColor = JarvisTextPrimary,
    focusedLabelColor = JarvisCyan,
    unfocusedLabelColor = JarvisTextMuted
)
