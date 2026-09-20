package com.halovoid.bunori.ui.feature.novel.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.ui.core.components.AsymptoticProgressRing
import com.halovoid.bunori.ui.core.theme.*

@Composable
fun NovelTopBar(
    novel: Novel,
    isOpaque: Boolean,
    showTitle: Boolean,
    isActivityRunning: Boolean = false,
    onActivityClick: () -> Unit = {},
    onBack: () -> Unit,
    onJumpToChapterClick: () -> Unit,
    onFilterClick: () -> Unit,
    isFilterActive: Boolean,
    onSourceFilterClick: () -> Unit,
    isSourceFilterActive: Boolean
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isOpaque) DarkBackground else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "TopBarBackgroundAnimation"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PrimaryText
                )
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showTitle,
                    enter = fadeIn(animationSpec = tween(durationMillis = 250)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 200))
                ) {
                    Text(
                        text = novel.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            if (isActivityRunning) {
                AsymptoticProgressRing()
            }

            IconButton(onClick = onJumpToChapterClick) {
                Icon(
                    imageVector = Icons.Default.Tag,
                    contentDescription = "Jump to Chapter",
                    tint = PrimaryText
                )
            }

            IconButton(
                onClick = onSourceFilterClick,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (isSourceFilterActive) BrandAccent else PrimaryText
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Scanlator Sources"
                )
            }

            IconButton(
                onClick = onFilterClick,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (isFilterActive) BrandAccent else PrimaryText
                )
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filter and Sort"
                )
            }
        }
    }
}
