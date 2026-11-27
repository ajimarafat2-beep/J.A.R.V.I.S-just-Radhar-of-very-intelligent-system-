package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.agent.JarvisAgentUiState
import com.example.agent.MessageSender
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisNavy
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.voice.VoicePhase
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Agent board (live) screen — the NUSU-style home: a big particle-sphere orb
 * in Jarvis colours, a clean area below it, live conversation captions
 * (what you say on the RIGHT, what Jarvis says on the LEFT) and a timer
 * showing how long the live conversation has lasted.
 */
@Composable
fun JarvisAgentBoardScreen(
    uiState: JarvisAgentUiState,
    onToggleLiveVoice: () -> Unit,
    modifier: Modifier = Modifier,
    voiceRmsProvider: (() -> Float)? = null
) {
    val isLive = uiState.voicePhase != VoicePhase.IDLE
    var sessionStart by remember { mutableStateOf<Long?>(null) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(uiState.voicePhase) {
        if (isLive && sessionStart == null) {
            sessionStart = System.currentTimeMillis()
        }
        if (!isLive) {
            sessionStart = null
            elapsedSeconds = 0L
        }
    }
    LaunchedEffect(sessionStart) {
        val start = sessionStart ?: return@LaunchedEffect
        while (true) {
            elapsedSeconds = (System.currentTimeMillis() - start) / 1000
            delay(1000)
        }
    }
    val statusText = when (uiState.voicePhase) {
        VoicePhase.WAKE_LISTENING -> "Listening for \"Jarvis\"…"
        VoicePhase.COMMAND_LISTENING -> "Listening…"
        VoicePhase.SPEAKING -> "Speaking…"
        VoicePhase.IDLE -> if (uiState.isProcessing) "Working…" else "Tap the orb or mic to talk"
    }
    val recent = uiState.messages.takeLast(4)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .clickable { onToggleLiveVoice() }
                .testTag("agent_board_orb")
        ) {
            JarvisParticleOrb(
                size = 260.dp,
                active = isLive,
                voiceRmsProvider = voiceRmsProvider
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = JarvisTextPrimary
        )
        if (sessionStart != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatElapsed(elapsedSeconds),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = JarvisGold
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Live captions — clean area below the orb.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.liveTranscript.isNotBlank()) {
                LiveCaptionRow(
                    text = uiState.liveTranscript + "…",
                    isUser = true
                )
            }
            recent.forEach { message ->
                LiveCaptionRow(
                    text = message.text,
                    isUser = message.sender == MessageSender.USER
                )
            }
            if (recent.isEmpty() && uiState.liveTranscript.isBlank()) {
                Text(
                    text = "Your conversation captions will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextMuted
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            onClick = onToggleLiveVoice,
            shape = CircleShape,
            color = JarvisGold,
            modifier = Modifier
                .size(64.dp)
                .testTag("agent_board_mic_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Talk to Jarvis",
                    tint = JarvisNavy,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LiveCaptionRow(text: String, isUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isUser) JarvisCyan.copy(alpha = 0.18f) else JarvisSurface
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = JarvisTextPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

private fun formatElapsed(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

/** A sphere of tiny glowing dots — Jarvis gold + cyan — slowly rotating. */
@Composable
fun JarvisParticleOrb(
    size: Dp,
    active: Boolean,
    modifier: Modifier = Modifier,
    voiceRmsProvider: (() -> Float)? = null
) {
    val transition = rememberInfiniteTransition(label = "particle_orb")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_spin"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )
    val points = remember {
        val pts = ArrayList<Triple<Float, Float, Float>>(420)
        val n = 420
        val golden = PI * (3.0 - sqrt(5.0))
        for (i in 0 until n) {
            val y = 1.0 - (i.toDouble() / (n - 1)) * 2.0
            val r = sqrt(1.0 - y * y)
            val theta = golden * i
            pts.add(
                Triple(
                    (cos(theta) * r).toFloat(),
                    y.toFloat(),
                    (sin(theta) * r).toFloat()
                )
            )
        }
        pts
    }
    Canvas(modifier = modifier.size(size)) {
        val radius = size.toPx() / 2f * 0.92f * pulse
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val glowAlpha = if (active) 0.30f else 0.18f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(JarvisGold.copy(alpha = glowAlpha), Color.Transparent),
                center = Offset(cx, cy),
                radius = radius * 1.3f
            ),
            radius = radius * 1.3f,
            center = Offset(cx, cy)
        )
        val rot = angle * PI.toFloat() / 180f
        val cosR = cos(rot)
        val sinR = sin(rot)
        val rms = (voiceRmsProvider?.invoke() ?: 0f).coerceIn(0f, 1f)
        val spread = 1f + rms * 0.12f
        points.forEachIndexed { idx, p ->
            val x = p.first * cosR + p.third * sinR
            val z = -p.first * sinR + p.third * cosR
            val depth = (z + 1f) / 2f
            val px = cx + x * radius * spread
            val py = cy + p.second * radius * spread
            val dot = (1.5f + depth * 2.0f).dp.toPx()
            val col = if (idx % 3 == 0) JarvisCyan else JarvisGold
            drawCircle(
                color = col.copy(alpha = 0.25f + depth * 0.75f),
                radius = dot,
                center = Offset(px, py)
            )
        }
    }
}
