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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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

private val OrbTiltAngles = floatArrayOf(0f, 58f, 118f)
private val OrbNucleusHighlight = Color(0xFFFFF9C4)
private val OrbCream = Color(0xFFFFF8E1)
private val OrbParticleLight = Color(0xFFFFF59D)
private val OrbDarkAmber = Color(0xFF7A4E00).copy(alpha = 0.85f)

/**
 * Age of Ultron-style 3D Liquid Gold Core Orb blended with Google Gemini palette accents.
 * Uses drawWithCache and an optional draw-phase voiceRmsProvider so RMS ticks run at 60fps
 * without recomposing or allocating Brushes every frame.
 */
@Composable
fun JarvisHologramOrb(
    size: Dp,
    activeGold: Boolean,
    modifier: Modifier = Modifier,
    voiceRmsLevel: Float = 0f,
    voiceRmsProvider: (() -> Float)? = null
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
        targetValue = if (voiceRmsProvider == null) voiceRmsLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "rms_smooth"
    )

    val transition = rememberInfiniteTransition(label = "ultron_gold_core")
    val spinPrimary = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (activeGold) 4200 else 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_primary"
    )
    val spinCounter = transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (activeGold) 3200 else 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_counter"
    )
    val breathe = transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val radius = this.size.minDimension / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val strokeBase = (this.size.minDimension * 0.045f).coerceAtLeast(1.4f)
                val showParticles = this.size.minDimension >= 40.dp.toPx()

                val auraBrush = Brush.radialGradient(
                    colors = listOf(
                        primaryCore.copy(alpha = 0.45f),
                        rimAccent.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                )
                val tendrilBrush = Brush.sweepGradient(
                    colors = listOf(
                        primaryCore.copy(alpha = 0.92f),
                        OrbCream.copy(alpha = 0.7f),
                        rimAccent.copy(alpha = 0.45f),
                        primaryCore.copy(alpha = 0.92f)
                    ),
                    center = center
                )
                val baseNucleusRadius = radius * 0.34f
                val nucleusHighlightOffset = Offset(
                    center.x - baseNucleusRadius * 0.28f,
                    center.y - baseNucleusRadius * 0.28f
                )
                val nucleusBrush = Brush.radialGradient(
                    colors = listOf(
                        OrbNucleusHighlight,
                        primaryCore,
                        JarvisGoldDim,
                        OrbDarkAmber
                    ),
                    center = nucleusHighlightOffset,
                    radius = baseNucleusRadius * 1.65f
                )
                val tendrilTopLefts = Array(3) { idx ->
                    Offset(
                        this.size.width * (0.11f + idx * 0.045f),
                        this.size.height * (0.24f + idx * 0.03f)
                    )
                }
                val tendrilSizes = Array(3) { idx ->
                    val ix = tendrilTopLefts[idx].x
                    val iy = tendrilTopLefts[idx].y
                    Size(this.size.width - ix * 2f, this.size.height - iy * 2f)
                }
                val arcColor1 = rimAccent.copy(alpha = 0.85f)
                val arcColor2 = primaryCore.copy(alpha = 0.9f)
                val ringStroke = Stroke(width = strokeBase * 0.9f, cap = StrokeCap.Round)
                val baseTendrilStroke = Stroke(width = strokeBase * 0.9f)

                onDrawBehind {
                    val rms = (voiceRmsProvider?.invoke() ?: rmsSmooth).coerceIn(0f, 1f)
                    val curSpinPrimary = spinPrimary.value
                    val curSpinCounter = spinCounter.value
                    val curBreathe = breathe.value
                    val audioBoost = 1f + rms * 0.34f
                    val tendrilStroke = if (rms > 0.01f) {
                        Stroke(width = strokeBase * (0.85f + rms * 0.35f))
                    } else {
                        baseTendrilStroke
                    }

                    // 1. Volumetric outer plasma aura
                    drawCircle(
                        brush = auraBrush,
                        radius = radius,
                        center = center
                    )

                    // 2. 3D Liquid Gold Orbital Tendrils (multi-layer tilted ellipses)
                    for (idx in OrbTiltAngles.indices) {
                        val rot = OrbTiltAngles[idx] + if (idx % 2 == 0) curSpinPrimary else curSpinCounter
                        rotate(degrees = rot, pivot = center) {
                            drawOval(
                                brush = tendrilBrush,
                                topLeft = tendrilTopLefts[idx],
                                size = tendrilSizes[idx],
                                style = tendrilStroke
                            )
                        }
                    }

                    // 3. Outer segmented containment ring
                    drawArc(
                        color = arcColor1,
                        startAngle = curSpinPrimary,
                        sweepAngle = 150f,
                        useCenter = false,
                        style = ringStroke
                    )
                    drawArc(
                        color = arcColor2,
                        startAngle = curSpinPrimary + 185f,
                        sweepAngle = 135f,
                        useCenter = false,
                        style = ringStroke
                    )

                    // 4. Molten 3D Liquid Gold Nucleus with specular highlight
                    val nucleusRadius = (baseNucleusRadius * curBreathe * audioBoost).coerceAtMost(radius * 0.54f)
                    drawCircle(
                        brush = nucleusBrush,
                        radius = nucleusRadius,
                        center = center
                    )

                    // 5. Voice-reactive orbiting energy particles (6 harmonics on larger orbs)
                    if (showParticles) {
                        val orbitR = radius * (0.62f + rms * 0.14f)
                        val pRadius = strokeBase * (0.75f + rms * 0.55f)
                        for (i in 0 until 6) {
                            val theta = ((curSpinCounter + i * 60f) * PI / 180.0).toFloat()
                            val px = center.x + cos(theta) * orbitR
                            val py = center.y + sin(theta) * orbitR
                            drawCircle(
                                color = if (i % 2 == 0) OrbParticleLight else rimAccent,
                                radius = pRadius,
                                center = Offset(px, py)
                            )
                        }
                    }
                }
            }
    )
}

/**
 * Animated Edge Border Energy Transition:
 * Flows along the outer edges of the screen when JARVIS is listening, wake-word active,
 * speaking, or Edge Border Mode is engaged. Stops its infinite animation when inactive.
 */
@Composable
fun JarvisEdgeBorderOverlay(
    active: Boolean,
    voiceRmsLevel: Float = 0f,
    modifier: Modifier = Modifier,
    voiceRmsProvider: (() -> Float)? = null,
    content: @Composable () -> Unit
) {
    val alphaAnim = animateFloatAsState(
        targetValue = if (active) 0.92f else 0f,
        animationSpec = tween(durationMillis = 360),
        label = "edge_alpha"
    )
    val shouldAnimate = active || alphaAnim.value > 0.01f

    Box(modifier = modifier.fillMaxSize()) {
        content()

        if (shouldAnimate) {
            val transition = rememberInfiniteTransition(label = "edge_energy_flow")
            val flowPhase = transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "edge_flow_phase"
            )
            val edgeColors = remember {
                listOf(
                    JarvisGold,
                    JarvisCyan,
                    Color(0xFFFF9100),
                    JarvisCyanDim,
                    JarvisGold
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val curAlpha = alphaAnim.value
                if (curAlpha <= 0.01f) return@Canvas
                val curRms = (voiceRmsProvider?.invoke() ?: voiceRmsLevel).coerceIn(0f, 1f)
                val strokeWidth = 4.dp.toPx() + curRms * 6.dp.toPx()
                val corner = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                val center = Offset(size.width / 2f, size.height / 2f)
                val angleRad = flowPhase.value * PI.toFloat() / 180f
                val dx = cos(angleRad) * size.width * 0.45f
                val dy = sin(angleRad) * size.height * 0.45f

                val brush = Brush.linearGradient(
                    colors = edgeColors,
                    start = Offset(center.x - dx, center.y - dy),
                    end = Offset(center.x + dx, center.y + dy)
                )

                drawRoundRect(
                    brush = brush,
                    cornerRadius = corner,
                    style = Stroke(width = strokeWidth * 2.2f),
                    alpha = 0.25f * curAlpha,
                    blendMode = BlendMode.Plus
                )
                drawRoundRect(
                    brush = brush,
                    cornerRadius = corner,
                    style = Stroke(width = strokeWidth),
                    alpha = curAlpha
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
    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
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
                val brush = Brush.linearGradient(
                    colors = if (goldTint) {
                        listOf(Color(0xFFFFF59D), JarvisGold, JarvisGoldDim)
                    } else {
                        listOf(JarvisCyanDim, JarvisCyan, Color(0xFFC58AF9))
                    }
                )
                onDrawBehind {
                    drawPath(path = path, brush = brush)
                }
            }
    )
}
