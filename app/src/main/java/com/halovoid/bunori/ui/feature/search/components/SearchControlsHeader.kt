package com.halovoid.bunori.ui.feature.search.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.search.SourceSearchStatus

@Composable
fun SearchControlsHeader(
    sourceStates: Map<String, SourceSearchStatus>,
    selectedSource: String?,
    isCompactMode: Boolean,
    onToggleCompactMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val allDone = sourceStates.all { it.value !is SourceSearchStatus.Loading }
    val totalNovels = sourceStates.values.sumOf {
        (it as? SourceSearchStatus.Success)?.items?.size ?: 0
    }
    val totalSources = sourceStates.size
    val completedSources = sourceStates.values.count { it !is SourceSearchStatus.Loading }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (!allDone) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = BrandAccent,
                    strokeWidth = 2.dp
                )
                Text(
                    text = if (selectedSource != null) "Searching $selectedSource..." else "Searching ($completedSources/$totalSources sources)...",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = if (totalNovels > 0) {
                        if (selectedSource != null) "$totalNovels results in $selectedSource" else "$totalNovels results across $totalSources sources"
                    } else {
                        "Search completed"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = { onToggleCompactMode(!isCompactMode) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isCompactMode) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = if (isCompactMode) "Switch to Comfortable View" else "Switch to Compact View",
                tint = SecondaryText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
