package com.jarvis.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.domain.model.AssistantVisualState
import kotlin.math.PI
import kotlin.math.sin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember

@Composable
fun ArcReactorIndicator(
    visualState: AssistantVisualState,
    rmsLevel: Float,
    showWaveform: Boolean,
    modifier: Modifier = Modifier,
    indicatorSize: Dp = 124.dp,
    onCoreClick: (() -> Unit)? = null
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val glowColor = MaterialTheme.colorScheme.onPrimaryContainer

    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_core_anim")

    val pulseDuration = when (visualState) {
        AssistantVisualState.LISTENING -> 900
        AssistantVisualState.THINKING -> 750
        AssistantVisualState.SPEAKING -> 1100
        AssistantVisualState.STANDBY -> 2200
    }

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDuration),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rotation"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val smoothedRms by animateFloatAsState(
        targetValue = when (visualState) {
            AssistantVisualState.LISTENING -> rmsLevel.coerceIn(0.08f, 1f)
            AssistantVisualState.SPEAKING -> 0.55f
            AssistantVisualState.THINKING -> 0.35f
            AssistantVisualState.STANDBY -> 0.08f
        },
        animationSpec = tween(durationMillis = 120),
        label = "smoothed_rms"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val coreClickModifier = if (onCoreClick != null) {
            Modifier
                .size(indicatorSize)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCoreClick
                )
        } else {
            Modifier.size(indicatorSize)
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = coreClickModifier
        ) {
            Canvas(modifier = Modifier.size(indicatorSize)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f

                // Outer ambient glow reactive to voice energy
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = (0.24f + 0.16f * smoothedRms) * pulseAlpha),
                            secondaryColor.copy(alpha = 0.10f * pulseAlpha),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxRadius
                    ),
                    radius = maxRadius,
                    center = center
                )

                // Outer segmented HUD ring
                val outerArcRadius = maxRadius * 0.80f
                rotate(degrees = outerRotation, pivot = center) {
                    val arcSize = Size(outerArcRadius * 2f, outerArcRadius * 2f)
                    val topLeft = Offset(center.x - outerArcRadius, center.y - outerArcRadius)
                    val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    listOf(0f, 120f, 240f).forEach { startAngle ->
                        drawArc(
                            color = primaryColor.copy(alpha = 0.82f),
                            startAngle = startAngle,
                            sweepAngle = 78f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke
                        )
                    }
                }

                // Counter-rotating inner technical ring
                val midArcRadius = maxRadius * 0.60f
                rotate(degrees = innerRotation, pivot = center) {
                    val arcSize = Size(midArcRadius * 2f, midArcRadius * 2f)
                    val topLeft = Offset(center.x - midArcRadius, center.y - midArcRadius)
                    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    listOf(20f, 110f, 200f, 290f).forEach { startAngle ->
                        drawArc(
                            color = secondaryColor.copy(alpha = 0.85f),
                            startAngle = startAngle,
                            sweepAngle = 52f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke
                        )
                    }
                }

                // Core outline ring
                drawCircle(
                    color = primaryColor.copy(alpha = 0.45f + 0.45f * pulseAlpha),
                    radius = maxRadius * (0.36f + 0.04f * smoothedRms),
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Glowing inner core
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.92f),
                            glowColor.copy(alpha = 0.85f * pulseAlpha),
                            primaryColor.copy(alpha = 0.25f)
                        ),
                        center = center,
                        radius = maxRadius * 0.29f
                    ),
                    radius = maxRadius * (0.24f + 0.04f * smoothedRms),
                    center = center
                )
            }
        }

        if (showWaveform) {
            Spacer(modifier = Modifier.height(6.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(22.dp)
                    .padding(horizontal = 8.dp)
            ) {
                val barCount = 19
                val spacing = size.width / (barCount * 2f - 1f)
                val barWidth = spacing.coerceAtLeast(2.dp.toPx())
                val midY = size.height / 2f

                for (i in 0 until barCount) {
                    val distanceFromCenter = kotlin.math.abs(i - (barCount - 1) / 2f) / ((barCount - 1) / 2f)
                    val envelope = 1f - 0.65f * distanceFromCenter
                    val wave = 0.5f + 0.5f * sin(wavePhase + i * 0.7f)
                    val amplitude = (0.14f + smoothedRms * 0.86f * wave) * envelope
                    val barHeight = (size.height * amplitude).coerceAtLeast(3.dp.toPx())
                    val x = i * (barWidth + spacing) + barWidth / 2f

                    drawLine(
                        color = if (visualState == AssistantVisualState.STANDBY) {
                            primaryColor.copy(alpha = 0.40f)
                        } else {
                            primaryColor.copy(alpha = 0.92f)
                        },
                        start = Offset(x, midY - barHeight / 2f),
                        end = Offset(x, midY + barHeight / 2f),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = visualState.label,
            style = MaterialTheme.typography.labelMedium,
            color = primaryColor
        )
    }
}
