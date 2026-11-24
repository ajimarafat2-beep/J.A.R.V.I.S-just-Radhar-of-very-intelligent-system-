package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanDim
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisGoldDim
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Age of Ultron-style 3D Liquid Gold Core Orb blended with Google Gemini palette accents.
 * Reacts dynamically to voice RMS amplitude and active listening/speaking phases.
 */
@Composable
fun JarvisHologramOrb(
    size: Dp,
    activeGold: Boolean,
    modifier: Modifier = Modifier,
    voiceRmsLevel: Float = 0f
) {
    val primaryCore by animateColorAsState(
        targetValue = if (activeGold) Color(0xFFFFEA00) else Color(0xFFFFD600),
        label = "core_primary"
    )
    val rimAccent by animateColorAsState(
        targetValue = if (activeGold) Color(0xFFFFF176) else Color(0xFFFFB300),
        label = "core_rim"
    )
    val rmsSmooth by animateFloatAsState(
        targetValue = voiceRmsLevel.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 90),
        label = "rms_smooth"
    )

    val transition = rememberInfiniteTransition(label = "ultron_gold_core")
    val spinPrimary by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (activeGold) 4200 else 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_primary"
    )
    val spinCounter by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (activeGold) 3200 else 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_counter"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val audioBoost = 1f + rmsSmooth * 0.34f
        val strokeBase = (this.size.minDimension * 0.045f).coerceAtLeast(1.4f)

        // 1. Volumetric outer plasma aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryCore.copy(alpha = 0.45f),
                    rimAccent.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = center,
                radius = radius * (0.95f + rmsSmooth * 0.12f)
            ),
            radius = radius,
            center = center
        )

        // 2. 3D Liquid Gold Orbital Tendrils (multi-layer tilted ellipses)
        val tiltAngles = listOf(0f, 58f, 118f)
        tiltAngles.forEachIndexed { idx, baseTilt ->
            val rot = baseTilt + if (idx % 2 == 0) spinPrimary else spinCounter
            rotate(degrees = rot, pivot = center) {
                val insetX = this.size.width * (0.11f + idx * 0.045f)
                val insetY = this.size.height * (0.24f + idx * 0.03f)
                drawOval(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            primaryCore.copy(alpha = 0.92f),
                            Color(0xFFFFF8E1).copy(alpha = 0.7f),
                            rimAccent.copy(alpha = 0.45f),
                            primaryCore.copy(alpha = 0.92f)
                        ),
                        center = center
                    ),
                    topLeft = Offset(insetX, insetY),
                    size = Size(this.size.width - insetX * 2f, this.size.height - insetY * 2f),
                    style = Stroke(width = strokeBase * (0.85f + rmsSmooth * 0.35f))
                )
            }
        }

        // 3. Outer segmented containment ring
        drawArc(
            color = rimAccent.copy(alpha = 0.85f),
            startAngle = spinPrimary,
            sweepAngle = 150f,
            useCenter = false,
            style = Stroke(width = strokeBase * 0.9f, cap = StrokeCap.Round)
        )
        drawArc(
            color = primaryCore.copy(alpha = 0.9f),
            startAngle = spinPrimary + 185f,
            sweepAngle = 135f,
            useCenter = false,
            style = Stroke(width = strokeBase * 0.9f, cap = StrokeCap.Round)
        )

        // 4. Molten 3D Liquid Gold Nucleus with specular highlight
        val nucleusRadius = (radius * 0.34f * breathe * audioBoost).coerceAtMost(radius * 0.54f)
        val highlightOffset = Offset(
            center.x - nucleusRadius * 0.28f,
            center.y - nucleusRadius * 0.28f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF9C4),
                    primaryCore,
                    JarvisGoldDim,
                    Color(0xFF7A4E00).copy(alpha = 0.85f)
                ),
                center = highlightOffset,
                radius = nucleusRadius * 1.35f
            ),
            radius = nucleusRadius,
            center = center
        )

        // 5. Voice-reactive orbiting energy particles (6 harmonics)
        if (this.size.minDimension >= 40.dp.toPx()) {
            for (i in 0 until 6) {
                val theta = ((spinCounter + i * 60f) * PI / 180.0).toFloat()
                val orbitR = radius * (0.62f + rmsSmooth * 0.14f)
                val px = center.x + cos(theta) * orbitR
                val py = center.y + sin(theta) * orbitR
                drawCircle(
                    color = if (i % 2 == 0) Color(0xFFFFF59D) else rimAccent,
                    radius = (strokeBase * (0.75f + rmsSmooth * 0.55f)),
                    center = Offset(px, py)
                )
            }
        }
    }
}

/**
 * Animated Edge Border Energy Transition (Masterpiece Section 1):
 * Flows along the outer edges of the screen when JARVIS is listening, wake-word active,
 * speaking, or Edge Border Mode is engaged.
 */
@Composable
fun JarvisEdgeBorderOverlay(
    active: Boolean,
    voiceRmsLevel: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val alphaAnim by animateFloatAsState(
        targetValue = if (active) 0.92f else 0f,
        animationSpec = tween(durationMillis = 360),
        label = "edge_alpha"
    )
    val transition = rememberInfiniteTransition(label = "edge_energy_flow")
    val flowPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "edge_flow_phase"
    )

    Box(modifier = modifier.fillMaxSize()) {
        content()

        if (alphaAnim > 0.01f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = (4.dp.toPx() + voiceRmsLevel * 6.dp.toPx())
                val center = Offset(size.width / 2f, size.height / 2f)
                val angleRad = flowPhase * PI.toFloat() / 180f
                val dx = cos(angleRad) * size.width * 0.45f
                val dy = sin(angleRad) * size.height * 0.45f

                val brush = Brush.linearGradient(
                    colors = listOf(
                        JarvisGold.copy(alpha = alphaAnim),
                        JarvisCyan.copy(alpha = alphaAnim * 0.95f),
                        Color(0xFFFF9100).copy(alpha = alphaAnim),
                        JarvisCyanDim.copy(alpha = alphaAnim * 0.9f),
                        JarvisGold.copy(alpha = alphaAnim)
                    ),
                    start = Offset(center.x - dx, center.y - dy),
                    end = Offset(center.x + dx, center.y + dy)
                )

                // Soft outer aura stroke
                drawRoundRect(
                    brush = brush,
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                    style = Stroke(width = strokeWidth * 2.2f),
                    alpha = 0.25f * alphaAnim,
                    blendMode = BlendMode.Plus
                )
                // Crisp inner glowing edge border
                drawRoundRect(
                    brush = brush,
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                    style = Stroke(width = strokeWidth),
                    alpha = alphaAnim
                )
            }
        }
    }
}

/**
 * 4-point Google Gemini sparkle emblem combined with liquid gold core glint.
 */
@Composable
fun GeminiSparkleMark(
    size: Dp = 20.dp,
    goldTint: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val path = Path().apply {
            moveTo(cx, 0f)
            quadraticTo(cx, cy, w, cy)
            quadraticTo(cx, cy, cx, h)
            quadraticTo(cx, cy, 0f, cy)
            quadraticTo(cx, cy, cx, 0f)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = if (goldTint) {
                    listOf(Color(0xFFFFF59D), JarvisGold, JarvisGoldDim)
                } else {
                    listOf(JarvisCyanDim, JarvisCyan, Color(0xFFC58AF9))
                }
            )
        )
    }
}
