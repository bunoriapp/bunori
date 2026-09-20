package com.halovoid.bunori.ui.feature.layout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.ui.core.components.AppSelectionBottomSheet
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.novel.DownloadFilter
import com.halovoid.bunori.ui.feature.novel.SortOrder
import com.halovoid.bunori.ui.feature.novel.SortType
import com.halovoid.bunori.ui.feature.settings.SectionHeader
import com.halovoid.bunori.ui.feature.settings.SettingsRow
import com.halovoid.bunori.ui.feature.settings.SettingsViewModel

@Composable
fun LayoutSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val searchCompactView by viewModel.searchCompactView.collectAsStateWithLifecycle()
    val libraryCompactView by viewModel.libraryCompactView.collectAsStateWithLifecycle()
    val activityCompactView by viewModel.activityCompactView.collectAsStateWithLifecycle()
    val defaultChapterDownloadFilter by viewModel.defaultChapterDownloadFilter.collectAsStateWithLifecycle()
    val defaultChapterSortType by viewModel.defaultChapterSortType.collectAsStateWithLifecycle()
    val defaultChapterSortOrder by viewModel.defaultChapterSortOrder.collectAsStateWithLifecycle()
    val defaultSourceFilter by viewModel.defaultSourceFilter.collectAsStateWithLifecycle()

    var activeDialog by remember { mutableStateOf<LayoutDialogState?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Layouts",
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader(text = "Layouts")

            SettingsRow(
                title = "Search View Mode",
                subtitle = if (searchCompactView) "Compact View" else "Grid View (Comfortable)",
                icon = Icons.Outlined.GridView,
                onClick = { activeDialog = LayoutDialogState.SearchViewMode }
            )

            SettingsRow(
                title = "Library View Mode",
                subtitle = if (libraryCompactView) "Compact View" else "Grid View",
                icon = Icons.AutoMirrored.Outlined.ViewList,
                onClick = { activeDialog = LayoutDialogState.LibraryViewMode }
            )

            SettingsRow(
                title = "Activity View Mode",
                subtitle = if (activityCompactView) "Compact View" else "Carousel View (Comfortable)",
                icon = Icons.Outlined.DynamicFeed,
                onClick = { activeDialog = LayoutDialogState.ActivityViewMode }
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(text = "Novel Details Defaults")

            SettingsRow(
                title = "Default Chapter Filter",
                subtitle = when (defaultChapterDownloadFilter) {
                    DownloadFilter.ALL -> "All Chapters"
                    DownloadFilter.DOWNLOADED -> "Downloaded Only"
                    DownloadFilter.NOT_DOWNLOADED -> "Not Downloaded Only"
                },
                icon = Icons.Outlined.FilterList,
                onClick = { activeDialog = LayoutDialogState.ChapterFilter }
            )

            SettingsRow(
                title = "Default Chapter Sort By",
                subtitle = when (defaultChapterSortType) {
                    SortType.CHAPTER_NUMBER -> "By Chapter Number"
                    SortType.ALPHABETICAL -> "Alphabetically"
                },
                icon = Icons.AutoMirrored.Outlined.Sort,
                onClick = { activeDialog = LayoutDialogState.ChapterSortType }
            )

            SettingsRow(
                title = "Default Chapter Sort Order",
                subtitle = when (defaultChapterSortOrder) {
                    SortOrder.ASCENDING -> "Ascending (1 to N)"
                    SortOrder.DESCENDING -> "Descending (N to 1)"
                },
                icon = Icons.Outlined.SwapVert,
                onClick = { activeDialog = LayoutDialogState.ChapterSortOrder }
            )

            SettingsRow(
                title = "Default Scanlator Source Filter",
                subtitle = if (defaultSourceFilter == "ALL") "All Sources" else defaultSourceFilter,
                icon = Icons.Outlined.Source,
                onClick = { activeDialog = LayoutDialogState.SourceFilter }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        when (activeDialog) {
            LayoutDialogState.SearchViewMode -> {
                AppSelectionBottomSheet(
                    title = "Search View Mode",
                    options = listOf("Grid View", "Compact View"),
                    selectedIndex = if (searchCompactView) 1 else 0,
                    onSelect = { index ->
                        viewModel.setSearchCompactView(index == 1)
                    },
                    onDismiss = { activeDialog = null }
                )
            }
            LayoutDialogState.LibraryViewMode -> {
                AppSelectionBottomSheet(
                    title = "Library View Mode",
                    options = listOf("Grid View", "Compact View"),
                    selectedIndex = if (libraryCompactView) 1 else 0,
                    onSelect = { index ->
                        viewModel.setLibraryCompactView(index == 1)
                    },
                    onDismiss = { activeDialog = null }
                )
            }
            LayoutDialogState.ActivityViewMode -> {
                AppSelectionBottomSheet(
                    title = "Activity View Mode",
                    options = listOf("Carousel View", "Compact View"),
                    selectedIndex = if (activityCompactView) 1 else 0,
                    onSelect = { index ->
                        viewModel.setActivityCompactView(index == 1)
                    },
                    onDismiss = { activeDialog = null }
                )
            }
            LayoutDialogState.ChapterFilter -> {
                AppSelectionBottomSheet(
                    title = "Default Chapter Filter",
                    options = listOf("All Chapters", "Downloaded Only", "Not Downloaded Only"),
                    selectedIndex = when (defaultChapterDownloadFilter) {
                        DownloadFilter.ALL -> 0
                        DownloadFilter.DOWNLOADED -> 1
                        DownloadFilter.NOT_DOWNLOADED -> 2
                    },
                    onSelect = { index ->
                        val filter = when (index) {
                            1 -> DownloadFilter.DOWNLOADED
                            2 -> DownloadFilter.NOT_DOWNLOADED
                            else -> DownloadFilter.ALL
                        }
                        viewModel.setDefaultChapterDownloadFilter(filter)
                    },
                    onDismiss = { activeDialog = null }
                )
            }
            LayoutDialogState.ChapterSortType -> {
                AppSelectionBottomSheet(
                    title = "Default Chapter Sort By",
                    options = listOf("By Chapter Number", "Alphabetically"),
                    selectedIndex = when (defaultChapterSortType) {
                        SortType.CHAPTER_NUMBER -> 0
                        SortType.ALPHABETICAL -> 1
                    },
                    onSelect = { index ->
                        val type = if (index == 1) SortType.ALPHABETICAL else SortType.CHAPTER_NUMBER
                        viewModel.setDefaultChapterSortType(type)
                    },
                    onDismiss = { activeDialog = null }
                )
            }
            LayoutDialogState.ChapterSortOrder -> {
                AppSelectionBottomSheet(
                    title = "Default Chapter Sort Order",
                    options = listOf("Ascending (1 to N)", "Descending (N to 1)"),
                    selectedIndex = when (defaultChapterSortOrder) {
                        SortOrder.ASCENDING -> 0
                        SortOrder.DESCENDING -> 1
                    },
                    onSelect = { index ->
                        val order = if (index == 1) SortOrder.DESCENDING else SortOrder.ASCENDING
                        viewModel.setDefaultChapterSortOrder(order)
                    },
                    onDismiss = { activeDialog = null }
                )
            }
            LayoutDialogState.SourceFilter -> {
                AppSelectionBottomSheet(
                    title = "Default Source Filter",
                    options = listOf("All Sources"),
                    selectedIndex = 0,
                    onSelect = { _ ->
                        viewModel.setDefaultSourceFilter("ALL")
                    },
                    onDismiss = { activeDialog = null }
                )
            }
            null -> Unit
        }
    }
}

private enum class LayoutDialogState {
    SearchViewMode,
    LibraryViewMode,
    ActivityViewMode,
    ChapterFilter,
    ChapterSortType,
    ChapterSortOrder,
    SourceFilter
}
