package com.halovoid.bunori.ui.feature.novel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.ui.core.components.AppBottomSheet
import com.halovoid.bunori.ui.core.components.AppBottomSheetDivider
import com.halovoid.bunori.ui.core.components.AppBottomSheetGroup
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.novel.ChapterActiveStatuses

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
    onChapterToggleSelect: (Chapter) -> Unit
) {
    items(chapters, key = { it.id }) { chapter ->
        val status = chapterStatuses.getStatus(chapter.id, chapter.url, chapter.sourceUrl)
        val isSelected = selectedChapterIds.contains(chapter.id)
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
        HorizontalDivider(
            color = BorderColor.copy(alpha = 0.4f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
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
    var showMenu by remember { mutableStateOf(false) }

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
            Text(
                text = "Chapter ${chapter.index}",
                color = if (chapter.read) SecondaryText.copy(0.5f) else PrimaryText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
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
                    onClick = { showMenu = true },
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
                                tint = WarningAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    JobStatus.BLOCKED -> {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Blocked - Security Check",
                            tint = WarningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    JobStatus.CANCELLING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = ErrorRed
                        )
                    }
                    JobStatus.FAILED -> {
                        IconButton(
                            onClick = { onFetchChapter(chapter) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Failed - Tap to Retry",
                                tint = ErrorRed,
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

        if (showMenu) {
            ChapterActionsBottomSheet(
                chapter = chapter,
                onDismiss = { showMenu = false },
                onDelete = { onDeleteChapter(chapter) },
                onReplay = { onReplayChapter(chapter) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterActionsBottomSheet(
    chapter: Chapter,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onReplay: () -> Unit
) {
    AppBottomSheet(
        onDismiss = onDismiss,
        title = "Chapter ${chapter.index}",
        subtitle = if (chapter.title.isNotBlank()) chapter.title else null
    ) {
        AppBottomSheetGroup {
            ListItem(
                headlineContent = { Text("Replay Chapter", color = PrimaryText) },
                leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null, tint = PrimaryText) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    onReplay()
                    onDismiss()
                }
            )
            AppBottomSheetDivider()
            ListItem(
                headlineContent = { Text("Delete Chapter", color = ErrorRed) },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    onDelete()
                    onDismiss()
                }
            )
        }
    }
}
