package com.halovoid.bunori.ui.feature.reader.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.ui.core.theme.*
import kotlinx.coroutines.launch

/**
 * Polished, high-performance center modal dialog for Table of Contents.
 * Features instant search by title or chapter number, quick jump to current chapter,
 * read-state badges, and auto-scrolling to the active reading position.
 */
@Composable
fun TableOfContentsDialog(
    chapters: List<Chapter>,
    currentChapterId: Int?,
    onChapterSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Filter chapters dynamically by keyword or chapter number
    val filteredChapters = remember(chapters, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            chapters
        } else {
            val queryAsNumber = q.removePrefix("#").toIntOrNull()
            chapters.filter { ch ->
                if (queryAsNumber != null && ch.index == queryAsNumber) {
                    true
                } else {
                    ch.title.contains(q, ignoreCase = true) ||
                    ch.index.toString() == q
                }
            }
        }
    }

    // Auto-scroll to active reading position upon dialog open
    LaunchedEffect(currentChapterId, chapters) {
        val activeIndex = chapters.indexOfFirst { it.id == currentChapterId }
        if (activeIndex >= 0) {
            listState.scrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, BorderColor),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BrandAccent.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = BrandAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Table of Contents",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                            Text(
                                text = "${chapters.size} Chapters",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryText
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SecondaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    placeholder = {
                        Text(
                            text = "Search title or chapter #...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText.copy(alpha = 0.7f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = SecondaryText
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = SecondaryText
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            val first = filteredChapters.firstOrNull()
                            if (first != null && searchQuery.isNotBlank()) {
                                onChapterSelected(first.id)
                                onDismiss()
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground,
                        focusedBorderColor = BrandAccent,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Action Bar: Jump to Active Chapter & Results Count
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (searchQuery.isNotBlank()) {
                        Text(
                            text = "Found ${filteredChapters.size} ${if (filteredChapters.size == 1) "chapter" else "chapters"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText
                        )
                    } else {
                        Text(
                            text = "Reading Order",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText
                        )
                    }

                    // Jump to current reading chapter button
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            searchQuery = ""
                            val activeIdx = chapters.indexOfFirst { it.id == currentChapterId }
                            if (activeIdx >= 0) {
                                scope.launch {
                                    listState.animateScrollToItem((activeIdx - 2).coerceAtLeast(0))
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = BrandAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Current",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 4.dp),
                    color = BorderColor.copy(alpha = 0.5f),
                    thickness = 0.75.dp
                )

                // Chapter List or Empty State
                if (filteredChapters.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = SecondaryText.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No chapters found matching \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryText
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { searchQuery = "" }) {
                                Text("Clear Search", color = BrandAccent)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filteredChapters, key = { it.id }) { chapter ->
                            val isCurrent = chapter.id == currentChapterId
                            ChapterRowItem(
                                chapter = chapter,
                                isCurrent = isCurrent,
                                onClick = {
                                    onChapterSelected(chapter.id)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterRowItem(
    chapter: Chapter,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isCurrent -> BrandAccent.copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Chapter Number Badge
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isCurrent) BrandAccent else DarkSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Text(
                text = "#${chapter.index}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) Color.White else SecondaryText,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Chapter Title
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isCurrent -> BrandAccent
                    chapter.read -> SecondaryText.copy(alpha = 0.65f)
                    else -> PrimaryText
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Status Indicator
        if (isCurrent) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = BrandAccent.copy(alpha = 0.2f),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "READING",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandAccent,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        } else if (chapter.read) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Read",
                tint = SecondaryText.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp)
            )
        }
    }
}
