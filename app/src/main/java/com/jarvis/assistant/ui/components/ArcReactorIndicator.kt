package com.jarvis.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.jarvis.assistant.ui.theme.JarvisBlue
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisCyanGlow

@Composable
fun ArcReactorIndicator(
    visualState: AssistantVisualState,
    modifier: Modifier = Modifier,
    indicatorSize: Dp = 132.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_core_anim")

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rotation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(indicatorSize)
        ) {
            Canvas(modifier = Modifier.size(indicatorSize)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f

                // Outer ambient radial cyan glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            JarvisCyan.copy(alpha = 0.28f * pulseAlpha),
                            JarvisBlue.copy(alpha = 0.10f * pulseAlpha),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxRadius
                    ),
                    radius = maxRadius,
                    center = center
                )

                // Outer segmented HUD ring
                val outerArcRadius = maxRadius * 0.78f
                rotate(degrees = outerRotation, pivot = center) {
                    val arcSize = Size(outerArcRadius * 2f, outerArcRadius * 2f)
                    val topLeft = Offset(center.x - outerArcRadius, center.y - outerArcRadius)
                    val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    listOf(0f, 120f, 240f).forEach { startAngle ->
                        drawArc(
                            color = JarvisCyan.copy(alpha = 0.75f),
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
                val midArcRadius = maxRadius * 0.58f
                rotate(degrees = innerRotation, pivot = center) {
                    val arcSize = Size(midArcRadius * 2f, midArcRadius * 2f)
                    val topLeft = Offset(center.x - midArcRadius, center.y - midArcRadius)
                    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    listOf(20f, 110f, 200f, 290f).forEach { startAngle ->
                        drawArc(
                            color = JarvisBlue.copy(alpha = 0.8f),
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
                    color = JarvisCyan.copy(alpha = 0.4f + 0.4f * pulseAlpha),
                    radius = maxRadius * 0.36f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Glowing inner core
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.9f),
                            JarvisCyanGlow.copy(alpha = 0.85f * pulseAlpha),
                            JarvisCyan.copy(alpha = 0.25f)
                        ),
                        center = center,
                        radius = maxRadius * 0.28f
                    ),
                    radius = maxRadius * 0.26f,
                    center = center
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = visualState.label,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
