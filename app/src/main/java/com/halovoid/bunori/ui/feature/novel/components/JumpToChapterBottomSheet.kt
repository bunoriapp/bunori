package com.halovoid.bunori.ui.feature.novel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.ui.core.components.AppBottomSheet
import com.halovoid.bunori.ui.core.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpToChapterBottomSheet(
    chapters: List<Chapter>,
    onChapterClick: (Chapter) -> Unit,
    onScrollToChapter: (Chapter) -> Unit,
    onDismiss: () -> Unit
) {
    var chapterNumberText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val trimmedText = chapterNumberText.trim()
    val targetIndex = trimmedText.toIntOrNull()
        ?: trimmedText.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull()

    val matchingChapters = remember(trimmedText, targetIndex, chapters) {
        if (targetIndex != null) {
            chapters.filter { it.index == targetIndex }
        } else if (trimmedText.isNotBlank()) {
            chapters.filter { it.title.contains(trimmedText, ignoreCase = true) }
        } else {
            emptyList()
        }
    }

    val minIndex = remember(chapters) { chapters.minOfOrNull { it.index } ?: 1 }
    val maxIndex = remember(chapters) { chapters.maxOfOrNull { it.index } ?: 1 }

    AppBottomSheet(
        onDismiss = onDismiss,
        title = "Jump to Chapter",
        subtitle = "Available range: $minIndex – $maxIndex (${chapters.size} total)"
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = chapterNumberText,
            onValueChange = { chapterNumberText = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Enter chapter number (e.g. 10)",
                    color = SecondaryText.copy(alpha = 0.7f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Tag,
                    contentDescription = null,
                    tint = BrandAccent
                )
            },
            trailingIcon = {
                if (chapterNumberText.isNotEmpty()) {
                    IconButton(onClick = { chapterNumberText = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = SecondaryText
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { keyboardController?.hide() }
            ),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandAccent,
                unfocusedBorderColor = BorderColor.copy(alpha = 0.5f),
                focusedTextColor = PrimaryText,
                unfocusedTextColor = PrimaryText,
                focusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.4f),
                unfocusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.25f)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (trimmedText.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant.copy(alpha = 0.3f))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Type a chapter number above to jump directly to it.\nIf multiple scanlators have translated the same chapter number, all matching versions will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText,
                    lineHeight = 20.sp
                )
            }
        } else if (matchingChapters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant.copy(alpha = 0.3f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No chapters found matching \"$trimmedText\".\nValid chapter indices are between $minIndex and $maxIndex.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText,
                    lineHeight = 20.sp
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${matchingChapters.size} match${if (matchingChapters.size > 1) "es" else ""} for Chapter $targetIndex",
                    style = MaterialTheme.typography.labelLarge,
                    color = BrandAccent,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Tap to read",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryText
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(matchingChapters, key = { it.id }) { chapter ->
                    JumpChapterItem(
                        chapter = chapter,
                        onReadClick = {
                            onChapterClick(chapter)
                            onDismiss()
                        },
                        onScrollClick = {
                            onScrollToChapter(chapter)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun JumpChapterItem(
    chapter: Chapter,
    onReadClick: () -> Unit,
    onScrollClick: () -> Unit
) {
    val displayTitle = chapter.title.ifBlank { "Chapter ${chapter.index}" }
    val effectiveSource = chapter.scanlationSource.takeIf {
        it.isNotBlank() && it != "NotProvided" && it != "Not Provided"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onReadClick() },
        color = DarkSurfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (effectiveSource != null) {
                        Surface(
                            color = BrandAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = effectiveSource,
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (chapter.isDownloaded) {
                        Surface(
                            color = SuccessGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Downloaded",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuccessGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (chapter.read) {
                        Text(
                            text = "Read",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onScrollClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = "Scroll to in list",
                    tint = SecondaryText,
                    modifier = Modifier.size(18.dp)
                )
            }

            FilledTonalButton(
                onClick = onReadClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = BrandAccent,
                    contentColor = DarkBackground
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Read",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
