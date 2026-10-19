package com.halovoid.bunori.ui.feature.novel.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private data class RenderItem(
    val slotIndex: Int,
    val sourceIndex: Int,
    val name: String
)

@Composable
fun SourceSwipeSelector(
    sources: List<String>,
    currentSource: String?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (sources.isEmpty()) return

    if (sources.size == 1) {
        val single = sources.first()
        Box(
            modifier = modifier,
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = single,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        return
    }

    val displaySource = when {
        currentSource != null && sources.contains(currentSource) -> currentSource
        sources.contains("Bookmarks") && sources.size > 1 -> sources.first { it != "Bookmarks" }
        else -> sources.first()
    }
    val currentIndex = sources.indexOf(displaySource).let { if (it == -1) 0 else it }

    val density = LocalDensity.current
    val spacingDp = 76.dp
    val spacingPx = with(density) { spacingDp.toPx() }
    val animProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var isAnimating by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(200.dp)
            .height(36.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.06f to Color.Black,
                        0.94f to Color.Black,
                        1f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            .pointerInput(sources, displaySource) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (isAnimating) return@detectHorizontalDragGestures
                        coroutineScope.launch {
                            isAnimating = true
                            val p = animProgress.value
                            if (p >= 0.20f) {
                                animProgress.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
                                onNext()
                                animProgress.snapTo(0f)
                            } else if (p <= -0.20f) {
                                animProgress.animateTo(-1f, tween(200, easing = FastOutSlowInEasing))
                                onPrevious()
                                animProgress.snapTo(0f)
                            } else {
                                animProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                            isAnimating = false
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            animProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            isAnimating = false
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        if (isAnimating) return@detectHorizontalDragGestures
                        coroutineScope.launch {
                            val newProgress = (animProgress.value - (dragAmount / spacingPx)).coerceIn(-1f, 1f)
                            animProgress.snapTo(newProgress)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val progress = animProgress.value

        val itemsToRender = remember(currentIndex, sources, progress > 0, progress < 0) {
            val list = mutableListOf<RenderItem>()
            if (sources.size == 2) {
                val otherIndex = 1 - currentIndex
                list.add(RenderItem(slotIndex = 0, sourceIndex = currentIndex, name = sources[currentIndex]))
                if (progress >= 0f) {
                    list.add(RenderItem(slotIndex = 1, sourceIndex = otherIndex, name = sources[otherIndex]))
                } else {
                    list.add(RenderItem(slotIndex = -1, sourceIndex = otherIndex, name = sources[otherIndex]))
                }
            } else {
                val n = sources.size
                list.add(RenderItem(slotIndex = -1, sourceIndex = (currentIndex - 1 + n) % n, name = sources[(currentIndex - 1 + n) % n]))
                list.add(RenderItem(slotIndex = 0, sourceIndex = currentIndex, name = sources[currentIndex]))
                list.add(RenderItem(slotIndex = 1, sourceIndex = (currentIndex + 1) % n, name = sources[(currentIndex + 1) % n]))
                if (progress > 0.05f) {
                    list.add(RenderItem(slotIndex = 2, sourceIndex = (currentIndex + 2) % n, name = sources[(currentIndex + 2) % n]))
                } else if (progress < -0.05f) {
                    list.add(RenderItem(slotIndex = -2, sourceIndex = (currentIndex - 2 + n) % n, name = sources[(currentIndex - 2 + n) % n]))
                }
            }
            list
        }

        for (item in itemsToRender) {
            val itemX = (item.slotIndex - progress) * spacingPx
            val dist = abs(itemX) / spacingPx

            val alpha = when {
                dist <= 1f -> 1.0f - 0.7f * dist
                dist < 1.8f -> (0.3f * (1.8f - dist) / 0.8f).coerceIn(0f, 0.3f)
                else -> 0f
            }

            val scale = when {
                dist <= 1f -> 1.0f - 0.22f * dist
                else -> 0.78f
            }

            if (alpha > 0.01f) {
                val isLeftSlot = itemX < -spacingPx * 0.4f
                val isRightSlot = itemX > spacingPx * 0.4f

                val clickModifier = if (isLeftSlot) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isAnimating) {
                            coroutineScope.launch {
                                isAnimating = true
                                animProgress.animateTo(-1f, tween(220, easing = FastOutSlowInEasing))
                                onPrevious()
                                animProgress.snapTo(0f)
                                isAnimating = false
                            }
                        }
                    }
                } else if (isRightSlot) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isAnimating) {
                            coroutineScope.launch {
                                isAnimating = true
                                animProgress.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
                                onNext()
                                animProgress.snapTo(0f)
                                isAnimating = false
                            }
                        }
                    }
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(itemX.roundToInt(), 0) }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .then(clickModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (dist < 0.35f) FontWeight.SemiBold else FontWeight.Normal,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 74.dp)
                    )
                }
            }
        }
    }
}
