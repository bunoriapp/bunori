package com.halovoid.bunori.ui.core.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.ui.core.theme.BorderColor
import com.halovoid.bunori.ui.core.theme.BrandAccent
import kotlinx.coroutines.isActive

/**
 * A circular progress ring that progressively advances towards completion,
 * decelerating continuously over time to indicate ongoing background activity.
 */
@Composable
fun AsymptoticProgressRing(
    modifier: Modifier = Modifier,
) {
    var elapsedSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val startNanos = System.nanoTime()
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                elapsedSeconds = (frameTimeNanos - startNanos) / 1_000_000_000f
            }
        }
    }

    // Decelerating asymptotic progress formula: 1 - 1 / (1 + t / tau)
    val progress = remember(elapsedSeconds) {
        (1f - (1f / (1f + (elapsedSeconds / 8f)))).coerceIn(0.06f, 0.98f)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "AsymptoticRingRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RingRotation"
    )

    val trackColor = BorderColor.copy(alpha = 0.5f)
    val arcColor = BrandAccent

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val strokeWidth = 2.2.dp.toPx()

            // Subtle background track
            drawCircle(
                color = trackColor,
                radius = (size.minDimension - strokeWidth) / 2,
                style = Stroke(width = strokeWidth)
            )

            // Dynamic decelerating arc
            drawArc(
                color = arcColor,
                startAngle = -90f + rotation,
                sweepAngle = progress * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}
