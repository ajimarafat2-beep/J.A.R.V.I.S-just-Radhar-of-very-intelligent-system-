package com.example.ui

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.agent.AudioGenUiState
import com.example.agent.MediaGenUiState
import com.example.agent.VideoGenUiState
import com.example.ai.JarvisMediaComposer
import com.example.data.CreationMediaType
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
import java.io.File

enum class StudioMediaMode(val label: String) {
    IMAGE("Image"),
    AUDIO("Audio"),
    VIDEO("Video")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioMediaGeneratorPanel(
    mode: StudioMediaMode,
    onModeChange: (StudioMediaMode) -> Unit,
    mediaGenState: MediaGenUiState,
    audioGenState: AudioGenUiState,
    videoGenState: VideoGenUiState,
    selectedAspectRatio: String,
    onSelectAspectRatio: (String) -> Unit,
    selectedVoiceName: String,
    onSelectVoiceName: (String) -> Unit,
    referenceBitmap: Bitmap?,
    onPickReferenceImage: () -> Unit,
    onClearReferenceImage: () -> Unit,
    onGenerateImage: (String, String, Bitmap?) -> Unit,
    onGenerateAudio: (String, String, String) -> Unit,
    onGenerateVideo: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var imagePrompt by remember { mutableStateOf("") }
    var audioPrompt by remember { mutableStateOf("") }
    var audioMood by remember { mutableStateOf("Calm JARVIS") }
    var videoPrompt by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, JarvisBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JarvisHologramOrb(size = 24.dp, activeGold = true)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "J.A.R.V.I.S Multimodal Media Studio",
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisTextPrimary
                    )
                    Text(
                        text = "Create AI Images, Neural Voice Audio (WAV), and 720p AI Videos (MP4).",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudioMediaMode.entries.forEach { m ->
                    val tag = when (m) {
                        StudioMediaMode.IMAGE -> "studio_mode_image"
                        StudioMediaMode.AUDIO -> "studio_mode_audio"
                        StudioMediaMode.VIDEO -> "studio_mode_video"
                    }
                    FilterChip(
                        selected = mode == m,
                        onClick = { onModeChange(m) },
                        label = { Text(m.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (m) {
                                    StudioMediaMode.IMAGE -> Icons.Filled.Image
                                    StudioMediaMode.AUDIO -> Icons.Filled.AudioFile
                                    StudioMediaMode.VIDEO -> Icons.Filled.Videocam
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = studioModeChipColors(),
                        modifier = Modifier.testTag(tag)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            when (mode) {
                StudioMediaMode.IMAGE -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("1:1", "16:9", "9:16").forEach { ratio ->
                            FilterChip(
                                selected = selectedAspectRatio == ratio,
                                onClick = { onSelectAspectRatio(ratio) },
                                label = { Text("Ratio $ratio") },
                                colors = studioModeChipColors()
                            )
                        }
                        FilterChip(
                            selected = referenceBitmap != null,
                            onClick = onPickReferenceImage,
                            label = {
                                Text(if (referenceBitmap != null) "Change Reference Photo" else "Attach Photo to Edit")
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = studioModeChipColors(),
                            modifier = Modifier.testTag("studio_image_ref_button")
                        )
                    }

                    referenceBitmap?.let { ref ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                bitmap = ref.asImageBitmap(),
                                contentDescription = "Reference photo for editing",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Image-to-Image edit active",
                                style = MaterialTheme.typography.labelMedium,
                                color = JarvisGold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onClearReferenceImage) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear reference photo", tint = JarvisTextMuted)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = imagePrompt,
                        onValueChange = { imagePrompt = it },
                        label = {
                            Text(
                                if (referenceBitmap != null) "Describe how to edit this photo…"
                                else "Describe the image to create…"
                            )
                        },
                        colors = mediaFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_image_prompt_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onGenerateImage(imagePrompt, selectedAspectRatio, referenceBitmap) },
                        enabled = imagePrompt.isNotBlank() && mediaGenState !is MediaGenUiState.Loading,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("studio_generate_image_button")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (referenceBitmap != null) "Edit Image with Nano Banana 2.1" else "Generate Image",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    when (mediaGenState) {
                        is MediaGenUiState.Idle -> Text(
                            text = "Tip: Try \"Futuristic golden holographic reactor in obsidian lab\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextMuted
                        )
                        is MediaGenUiState.Loading -> LoadingStatusRow("Synthesizing image with Nano Banana 2.1…")
                        is MediaGenUiState.Success -> {
                            Image(
                                bitmap = mediaGenState.bitmap.asImageBitmap(),
                                contentDescription = mediaGenState.prompt,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Prompt: ${mediaGenState.prompt}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisTextMuted,
                                    modifier = Modifier.weight(1f)
                                )
                                mediaGenState.filePath?.let { path ->
                                    OutlinedButton(
                                        onClick = {
                                            JarvisMediaComposer.createShareFileIntent(context, path, "image/png", mediaGenState.prompt)
                                                ?.let { context.startActivity(Intent.createChooser(it, "Share AI Image")) }
                                        }
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = "Share PNG", tint = JarvisGold, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Share", color = JarvisGold)
                                    }
                                }
                            }
                        }
                        is MediaGenUiState.Error -> StudioErrorBox(mediaGenState.message)
                    }
                }

                StudioMediaMode.AUDIO -> {
                    Text("Voice Talent", style = MaterialTheme.typography.labelMedium, color = JarvisCyan)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Kore", "Charon", "Puck", "Aoede", "Fenrir").forEach { voice ->
                            FilterChip(
                                selected = selectedVoiceName == voice,
                                onClick = { onSelectVoiceName(voice) },
                                label = { Text(voice) },
                                colors = studioModeChipColors()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Delivery Mood", style = MaterialTheme.typography.labelMedium, color = JarvisGold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Calm JARVIS", "Dramatic Trailer", "Warm Storytelling", "Crisp Broadcast").forEach { mood ->
                            FilterChip(
                                selected = audioMood == mood,
                                onClick = { audioMood = mood },
                                label = { Text(mood) },
                                colors = studioModeChipColors()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = audioPrompt,
                        onValueChange = { audioPrompt = it },
                        label = { Text("Enter text or narration script to synthesize…") },
                        minLines = 2,
                        colors = mediaFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_audio_prompt_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onGenerateAudio(audioPrompt, selectedVoiceName, audioMood) },
                        enabled = audioPrompt.isNotBlank() && audioGenState !is AudioGenUiState.Loading,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("studio_generate_audio_button")
                    ) {
                        Icon(Icons.Filled.AudioFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate AI Audio (WAV)", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    when (audioGenState) {
                        is AudioGenUiState.Idle -> Text(
                            text = "Generates studio-grade L16/WAV speech in English, বাংলা, or Hindi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextMuted
                        )
                        is AudioGenUiState.Loading -> LoadingStatusRow("Synthesizing ${audioGenState.voiceName} neural voice…")
                        is AudioGenUiState.Success -> {
                            JarvisAudioPlayerCard(
                                audioPath = audioGenState.audioFilePath,
                                title = "AI Voice: ${audioGenState.voiceName} (${audioGenState.durationSec}s)",
                                subtitle = audioGenState.prompt,
                                onShare = {
                                    JarvisMediaComposer.createShareFileIntent(
                                        context,
                                        audioGenState.audioFilePath,
                                        "audio/wav",
                                        "J.A.R.V.I.S Audio (${audioGenState.voiceName})"
                                    )?.let { context.startActivity(Intent.createChooser(it, "Share AI Audio")) }
                                }
                            )
                        }
                        is AudioGenUiState.Error -> StudioErrorBox(audioGenState.message)
                    }
                }

                StudioMediaMode.VIDEO -> {
                    Text("Narration Voice", style = MaterialTheme.typography.labelMedium, color = JarvisCyan)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Charon", "Kore", "Fenrir", "Aoede").forEach { voice ->
                            FilterChip(
                                selected = selectedVoiceName == voice,
                                onClick = { onSelectVoiceName(voice) },
                                label = { Text(voice) },
                                colors = studioModeChipColors()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = videoPrompt,
                        onValueChange = { videoPrompt = it },
                        label = { Text("Describe the video scene, story, or topic…") },
                        minLines = 2,
                        colors = mediaFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_video_prompt_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onGenerateVideo(videoPrompt, selectedVoiceName) },
                        enabled = videoPrompt.isNotBlank() && videoGenState !is VideoGenUiState.Loading,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("studio_generate_video_button")
                    ) {
                        Icon(Icons.Filled.Videocam, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate 720p AI Video (MP4)", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    when (videoGenState) {
                        is VideoGenUiState.Idle -> Text(
                            text = "Directs a 3-scene 16:9 storyboard, synthesizes voiceover, and compiles an H.264/AAC .mp4 clip.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextMuted
                        )
                        is VideoGenUiState.Loading -> LoadingStatusRow(videoGenState.progressStage)
                        is VideoGenUiState.Success -> {
                            JarvisVideoPlayerCard(
                                videoPath = videoGenState.videoFilePath,
                                title = "${videoGenState.title} (${videoGenState.durationSec}s)",
                                subtitle = videoGenState.summary,
                                thumbnail = videoGenState.thumbnail,
                                onShare = {
                                    JarvisMediaComposer.createShareFileIntent(
                                        context,
                                        videoGenState.videoFilePath,
                                        "video/mp4",
                                        videoGenState.title
                                    )?.let { context.startActivity(Intent.createChooser(it, "Share AI Video")) }
                                }
                            )
                        }
                        is VideoGenUiState.Error -> StudioErrorBox(videoGenState.message)
                    }
                }
            }
        }
    }
}

@Composable
fun JarvisAudioPlayerCard(
    audioPath: String,
    title: String,
    subtitle: String?,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember(audioPath) { mutableStateOf(false) }
    val playerHolder = remember(audioPath) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(audioPath) {
        onDispose {
            runCatching {
                playerHolder.value?.stop()
                playerHolder.value?.release()
            }
            playerHolder.value = null
        }
    }

    Surface(
        color = JarvisSurfaceElevated,
        border = BorderStroke(1.dp, JarvisGold.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            runCatching {
                                playerHolder.value?.stop()
                                playerHolder.value?.release()
                            }
                            playerHolder.value = null
                            isPlaying = false
                        } else {
                            val file = File(audioPath)
                            if (file.exists()) {
                                runCatching {
                                    playerHolder.value?.release()
                                    val mp = MediaPlayer().apply {
                                        setDataSource(file.absolutePath)
                                        setOnCompletionListener {
                                            isPlaying = false
                                        }
                                        prepare()
                                        start()
                                    }
                                    playerHolder.value = mp
                                    isPlaying = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Stop audio" else "Play audio",
                        tint = JarvisGold,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, color = JarvisTextPrimary)
                    subtitle?.let {
                        Text(
                            text = it.take(120),
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextMuted
                        )
                    }
                }
                IconButton(onClick = onShare, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Share, contentDescription = "Share audio file", tint = JarvisCyan)
                }
            }
        }
    }
}

@Composable
fun JarvisVideoPlayerCard(
    videoPath: String,
    title: String,
    subtitle: String?,
    thumbnail: Bitmap?,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startedPlayback by remember(videoPath) { mutableStateOf(false) }
    var isPlaying by remember(videoPath) { mutableStateOf(false) }
    var videoViewRef by remember(videoPath) { mutableStateOf<VideoView?>(null) }

    Surface(
        color = JarvisSurfaceElevated,
        border = BorderStroke(1.dp, JarvisGold.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setOnCompletionListener { isPlaying = false }
                            setOnPreparedListener { mp -> mp.isLooping = false }
                            videoViewRef = this
                        }
                    },
                    update = { vv -> videoViewRef = vv },
                    modifier = Modifier.fillMaxSize()
                )
                if (!startedPlayback && thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = JarvisGold)
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = JarvisTextMuted)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val vv = videoViewRef ?: return@Button
                        val file = File(videoPath)
                        if (!file.exists()) return@Button
                        if (!startedPlayback) {
                            vv.setVideoURI(Uri.fromFile(file))
                            vv.start()
                            startedPlayback = true
                            isPlaying = true
                        } else if (isPlaying) {
                            vv.pause()
                            isPlaying = false
                        } else {
                            vv.start()
                            isPlaying = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            !startedPlayback -> "Play MP4"
                            isPlaying -> "Pause"
                            else -> "Resume"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = {
                        val vv = videoViewRef ?: return@OutlinedButton
                        val file = File(videoPath)
                        if (file.exists()) {
                            vv.setVideoURI(Uri.fromFile(file))
                            vv.seekTo(0)
                            vv.start()
                            startedPlayback = true
                            isPlaying = true
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Replay MP4", tint = JarvisCyan)
                }
                OutlinedButton(
                    onClick = onShare,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "Share MP4", tint = JarvisGold)
                }
            }
        }
    }
}

@Composable
fun StudioCloudCreationsCard(
    creations: List<StudioCreation>,
    errorMessage: String?,
    onDeleteCreation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, JarvisBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JarvisHologramOrb(size = 20.dp, activeGold = true)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Synced Cloud Creations (${creations.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = JarvisGold
                )
            }
            if (errorMessage != null) {
                StudioErrorBox(errorMessage)
            }
            if (creations.isEmpty() && errorMessage == null) {
                Text(
                    text = "Your Google-synced AI Images, Audio files, Videos, and Web Apps will appear here automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisTextMuted
                )
            } else {
                creations.take(15).forEach { item ->
                    val type = CreationMediaType.fromRaw(item.mediaType)
                    val localFileExists = !item.localFilePath.isNullOrBlank() && File(item.localFilePath).exists()
                    Surface(
                        color = JarvisSurfaceElevated,
                        border = BorderStroke(1.dp, JarvisBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = type.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (type) {
                                            CreationMediaType.VIDEO -> JarvisGold
                                            CreationMediaType.AUDIO -> JarvisCyan
                                            CreationMediaType.IMAGE -> JarvisEmerald
                                            else -> JarvisTextMuted
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (item.durationSeconds > 0) {
                                        Text(
                                            text = " • ${item.durationSeconds}s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = JarvisTextMuted
                                        )
                                    }
                                }
                                Text(
                                    text = item.title.ifBlank { item.prompt },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarvisTextPrimary
                                )
                                Text(
                                    text = item.modelUsed,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisTextMuted
                                )
                            }
                            if (localFileExists && item.localFilePath != null) {
                                val mime = when (type) {
                                    CreationMediaType.VIDEO -> "video/mp4"
                                    CreationMediaType.AUDIO -> "audio/wav"
                                    else -> "image/png"
                                }
                                IconButton(
                                    onClick = {
                                        JarvisMediaComposer.createShareFileIntent(context, item.localFilePath, mime, item.title)
                                            ?.let { context.startActivity(Intent.createChooser(it, "Share ${type.name}")) }
                                    }
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = "Share creation", tint = JarvisCyan)
                                }
                            }
                            if (item.id.isNotBlank()) {
                                IconButton(onClick = { onDeleteCreation(item.id) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete creation", tint = JarvisAlertRed)
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
private fun LoadingStatusRow(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = JarvisGold)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = JarvisGold
        )
    }
}

@Composable
private fun StudioErrorBox(message: String) {
    Surface(
        color = JarvisAlertRed.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, JarvisAlertRed),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = JarvisAlertRed,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun studioModeChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = JarvisGold.copy(alpha = 0.22f),
    selectedLabelColor = JarvisGold,
    selectedLeadingIconColor = JarvisGold,
    containerColor = JarvisSurfaceElevated,
    labelColor = JarvisTextPrimary
)

@Composable
private fun mediaFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = JarvisGold,
    unfocusedBorderColor = JarvisBorder,
    focusedTextColor = JarvisTextPrimary,
    unfocusedTextColor = JarvisTextPrimary,
    focusedLabelColor = JarvisGold,
    unfocusedLabelColor = JarvisTextMuted
)
