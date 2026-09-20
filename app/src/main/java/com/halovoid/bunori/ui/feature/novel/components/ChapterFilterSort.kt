package com.halovoid.bunori.ui.feature.novel.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.ui.core.components.AppBottomSheet
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.novel.DownloadFilter
import com.halovoid.bunori.ui.feature.novel.SortOrder
import com.halovoid.bunori.ui.feature.novel.SortType
import com.halovoid.bunori.ui.feature.novel.ChapterSortState

enum class ChapterControlTab {
    FILTER, SORT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterFilterSortSheet(
    downloadFilter: DownloadFilter,
    sortState: ChapterSortState,
    onSetFilter: (DownloadFilter) -> Unit,
    onToggleAlphabetical: () -> Unit,
    onToggleChapterNumber: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(ChapterControlTab.FILTER) }

    AppBottomSheet(
        onDismiss = onDismiss
    ) {
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = BrandAccent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                    color = BrandAccent,
                    height = 2.dp
                )
            },
            divider = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .height(40.dp)
        ) {
            Tab(
                selected = selectedTab == ChapterControlTab.FILTER,
                onClick = { selectedTab = ChapterControlTab.FILTER },
                text = {
                    Text(
                        "FILTER",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedTab == ChapterControlTab.FILTER) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == ChapterControlTab.FILTER) PrimaryText else SecondaryText
                    )
                }
            )
            Tab(
                selected = selectedTab == ChapterControlTab.SORT,
                onClick = { selectedTab = ChapterControlTab.SORT },
                text = {
                    Text(
                        "SORT",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedTab == ChapterControlTab.SORT) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == ChapterControlTab.SORT) PrimaryText else SecondaryText
                    )
                }
            )
        }

        Crossfade(targetState = selectedTab, label = "ChapterControlTabContent") { tab ->
            when (tab) {
                ChapterControlTab.FILTER -> {
                    FilterSection(downloadFilter, onSetFilter)
                }
                ChapterControlTab.SORT -> {
                    SortSection(sortState, onToggleAlphabetical, onToggleChapterNumber)
                }
            }
        }
    }
}

@Composable
private fun FilterSection(
    downloadFilter: DownloadFilter,
    onSetFilter: (DownloadFilter) -> Unit
) {
    val isDownloadedChecked = downloadFilter == DownloadFilter.ALL || downloadFilter == DownloadFilter.DOWNLOADED
    val isNotDownloadedChecked = downloadFilter == DownloadFilter.ALL || downloadFilter == DownloadFilter.NOT_DOWNLOADED

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterCheckboxOption(
            label = "Downloaded",
            checked = isDownloadedChecked,
            onToggle = {
                val newDownloaded = !isDownloadedChecked
                val nextFilter = when {
                    newDownloaded && isNotDownloadedChecked -> DownloadFilter.ALL
                    newDownloaded && !isNotDownloadedChecked -> DownloadFilter.DOWNLOADED
                    !newDownloaded && isNotDownloadedChecked -> DownloadFilter.NOT_DOWNLOADED
                    else -> DownloadFilter.NONE
                }
                onSetFilter(nextFilter)
            }
        )

        FilterCheckboxOption(
            label = "Not downloaded",
            checked = isNotDownloadedChecked,
            onToggle = {
                val newNotDownloaded = !isNotDownloadedChecked
                val nextFilter = when {
                    isDownloadedChecked && newNotDownloaded -> DownloadFilter.ALL
                    isDownloadedChecked && !newNotDownloaded -> DownloadFilter.DOWNLOADED
                    !isDownloadedChecked && newNotDownloaded -> DownloadFilter.NOT_DOWNLOADED
                    else -> DownloadFilter.NONE
                }
                onSetFilter(nextFilter)
            }
        )
    }
}

@Composable
private fun FilterCheckboxOption(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = BrandAccent,
                        uncheckedColor = SecondaryText,
                        checkmarkColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = PrimaryText,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SortSection(
    sortState: ChapterSortState,
    onToggleAlphabetical: () -> Unit,
    onToggleChapterNumber: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SortOption(
            label = "By chapter number",
            active = sortState.type == SortType.CHAPTER_NUMBER,
            order = sortState.order,
            onClick = onToggleChapterNumber
        )
        
        SortOption(
            label = "Alphabetically",
            active = sortState.type == SortType.ALPHABETICAL,
            order = sortState.order,
            onClick = onToggleAlphabetical
        )
    }
}

@Composable
private fun SortOption(
    label: String,
    active: Boolean,
    order: SortOrder,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (active) {
                    Icon(
                        imageVector = if (order == SortOrder.ASCENDING) Icons.Default.North else Icons.Default.South,
                        contentDescription = null,
                        tint = BrandAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = PrimaryText,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}
