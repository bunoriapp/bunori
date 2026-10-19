package com.halovoid.bunori.ui.feature.novel.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.novel.ChapterActiveStatuses
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

fun LazyListScope.novelTableOfContents(
    chapters: List<Chapter>,
    chapterStatuses: ChapterActiveStatuses = ChapterActiveStatuses(),
    isSelectionMode: Boolean,
    selectedChapterIds: Set<Int>,
    onFetchChapter: (Chapter) -> Unit,
    onDeleteChapter: (Chapter) -> Unit,
    onReplayChapter: (Chapter) -> Unit,
    onChapterClick: (Chapter) -> Unit,
    onChapterLongClick: (Chapter) -> Unit,
    onChapterToggleSelect: (Chapter) -> Unit,
    onToggleBookmark: (Chapter) -> Unit = {}
) {
    items(chapters, key = { it.id }) { chapter ->
        val status = chapterStatuses.getStatus(chapter.id, chapter.url, chapter.sourceUrl)
        val isSelected = selectedChapterIds.contains(chapter.id)
        SwipeableBookmarkChapterRow(
            chapter = chapter,
            isSelectionMode = isSelectionMode,
            onToggleBookmark = onToggleBookmark
        ) {
            ChapterRow(
                chapter = chapter,
                onFetchChapter = { onFetchChapter(it) },
                onDeleteChapter = { onDeleteChapter(it) },
                onReplayChapter = { onReplayChapter(it) },
                onChapterClick = { onChapterClick(it) },
                onChapterLongClick = { onChapterLongClick(it) },
                onChapterToggleSelect = { onChapterToggleSelect(it) },
                chapterStatus = status,
                isSelectionMode = isSelectionMode,
                isSelected = isSelected
            )
        }
        HorizontalDivider(
            color = BorderColor.copy(alpha = 0.4f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
fun SwipeableBookmarkChapterRow(
    chapter: Chapter,
    isSelectionMode: Boolean,
    onToggleBookmark: (Chapter) -> Unit,
    content: @Composable () -> Unit
) {
    if (isSelectionMode) {
        content()
        return
    }

    val density = LocalDensity.current
    val maxDragPx = with(density) { 96.dp.toPx() }
    val thresholdPx = with(density) { 64.dp.toPx() }
    val offsetX = remember(chapter.id) { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val currentChapter by rememberUpdatedState(chapter)
    val currentOnToggleBookmark by rememberUpdatedState(onToggleBookmark)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(chapter.id, chapter.bookmarked) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (offsetX.value <= -thresholdPx) {
                                currentOnToggleBookmark(currentChapter)
                            }
                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val newOffset = (offsetX.value + dragAmount).coerceIn(-maxDragPx, 0f)
                        coroutineScope.launch {
                            offsetX.snapTo(newOffset)
                        }
                    }
                )
            }
    ) {
        val currentOffset = offsetX.value
        if (currentOffset < -2f) {
            val progress = (-currentOffset / thresholdPx).coerceIn(0f, 1f)
            val isPassedThreshold = -currentOffset >= thresholdPx
            val bgTint = if (chapter.bookmarked) {
                Color(0xFFE53935).copy(alpha = if (isPassedThreshold) 0.25f else 0.12f)
            } else {
                BrandAccent.copy(alpha = if (isPassedThreshold) 0.25f else 0.12f)
            }
            val iconTint = if (chapter.bookmarked) {
                Color(0xFFEF5350)
            } else {
                BrandAccent
            }
            val icon = if (chapter.bookmarked) {
                Icons.Default.BookmarkRemove
            } else {
                Icons.Default.BookmarkAdd
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(bgTint),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 24.dp)
                        .graphicsLayer {
                            alpha = progress
                            scaleX = 0.8f + (0.3f * progress)
                            scaleY = 0.8f + (0.3f * progress)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = if (chapter.bookmarked) "Remove bookmark" else "Bookmark",
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        ) {
            content()
        }
    }
}

@Composable
fun ChapterRow(
    chapter: Chapter,
    onFetchChapter: (Chapter) -> Unit,
    onDeleteChapter: (Chapter) -> Unit,
    onReplayChapter: (Chapter) -> Unit,
    onChapterClick: (Chapter) -> Unit,
    onChapterLongClick: (Chapter) -> Unit,
    onChapterToggleSelect: (Chapter) -> Unit,
    chapterStatus: JobStatus? = null,
    isSelectionMode: Boolean,
    isSelected: Boolean
) {
    val backgroundColor = if (isSelected) {
        BrandAccent.copy(alpha = 0.15f)
    } else {
        DarkBackground
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onChapterToggleSelect(chapter)
                    } else {
                        onChapterClick(chapter)
                    }
                },
                onLongClick = {
                    onChapterLongClick(chapter)
                }
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Chapter ${chapter.index}",
                    color = if (chapter.read) SecondaryText.copy(0.5f) else PrimaryText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (chapter.bookmarked) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Bookmarked",
                        tint = BrandAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            val hasTitle = chapter.title.isNotBlank()
            val hasSource = chapter.scanlationSource.isNotBlank() && chapter.scanlationSource != "NotProvided" && chapter.scanlationSource != "Not Provided"

            if (hasTitle || hasSource) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasTitle) {
                        Text(
                            text = chapter.title,
                            color = SecondaryText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (hasSource) {
                        if (hasTitle) {
                            Text(
                                text = " • ",
                                color = SecondaryText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = chapter.scanlationSource,
                            color = SecondaryText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            if (chapter.isDownloaded) {
                IconButton(
                    onClick = { },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Options",
                        tint = PrimaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else if (chapterStatus != null) {
                when (chapterStatus) {
                    JobStatus.RUNNING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = BrandAccent
                        )
                    }
                    JobStatus.PENDING -> {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Queued",
                            tint = SecondaryText.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    JobStatus.PAUSED -> {
                        IconButton(
                            onClick = { onFetchChapter(chapter) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Paused - Tap to Resume",
                                tint = PrimaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    JobStatus.BLOCKED -> {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Blocked - Security Check",
                            tint = PrimaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    JobStatus.CANCELLING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryText
                        )
                    }
                    JobStatus.FAILED, JobStatus.CANCELLED -> {
                        IconButton(
                            onClick = { onFetchChapter(chapter) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry Chapter",
                                tint = PrimaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    else -> {
                        IconButton(
                            onClick = {
                                if (!isSelectionMode) {
                                    onFetchChapter(chapter)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.DownloadForOffline,
                                contentDescription = "Download Chapter",
                                tint = SecondaryText.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else {
                IconButton(
                    onClick = {
                        if (!isSelectionMode) {
                            onFetchChapter(chapter)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.DownloadForOffline,
                        contentDescription = "Download Chapter",
                        tint = SecondaryText.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}