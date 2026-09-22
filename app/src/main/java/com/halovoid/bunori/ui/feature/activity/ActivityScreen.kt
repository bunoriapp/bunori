package com.halovoid.bunori.ui.feature.activity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.JobType
import com.halovoid.bunori.ui.core.components.ConfirmCancelDialog
import com.halovoid.bunori.ui.core.components.ContextualAction
import com.halovoid.bunori.ui.core.components.ContextualBottomBar
import com.halovoid.bunori.ui.core.components.DownloadProgressRing
import com.halovoid.bunori.ui.core.components.ScreenHeader
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.DarkBackground
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.feature.activity.components.*
import com.halovoid.bunori.ui.navigation.AppNavigationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onRequestClick: (String) -> Unit
) {
    val requestHistory by viewModel.batchHistory.collectAsStateWithLifecycle()
    val globalStats by viewModel.globalStats.collectAsStateWithLifecycle()
    val isCompactMode by viewModel.isCompactMode.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedBatchIds by viewModel.selectedBatchIds.collectAsStateWithLifecycle()

    var filterType by remember { mutableStateOf<JobType?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSelection()
            AppNavigationManager.clearContextualBottomBar()
        }
    }

    var showSourceFilterSheet by remember { mutableStateOf(false) }

    JobActionHandler(
        onResolveWebview = { id, url -> viewModel.resolveWebView(id, url) }
    ) { _ ->
        var showCancelSelectedBatchesDialog by remember { mutableStateOf(false) }

        val filteredHistory = remember(requestHistory, filterType) {
            if (filterType == null) requestHistory
            else requestHistory.filter { it.type == filterType }
        }

        val activeBatches = remember(filteredHistory) {
            filteredHistory.filter {
                it.status == JobStatus.RUNNING ||
                it.status == JobStatus.PAUSED ||
                it.status == JobStatus.PENDING ||
                it.status == JobStatus.BLOCKED ||
                it.status == JobStatus.CANCELLING
            }
        }

        val nonActiveBatches = remember(filteredHistory) {
            filteredHistory.filter {
                it.status == JobStatus.SUCCESS ||
                it.status == JobStatus.FAILED ||
                it.status == JobStatus.CANCELLED
            }
        }

        val recentBatches = remember(nonActiveBatches) {
            nonActiveBatches.take(2)
        }

        val historyBatches = remember(nonActiveBatches) {
            nonActiveBatches.drop(2)
        }

        val selectedBatches = remember(filteredHistory, selectedBatchIds) {
            filteredHistory.filter { it.id in selectedBatchIds }
        }
        val canCancel = selectedBatches.isNotEmpty() && selectedBatches.any {
            it.status == JobStatus.RUNNING ||
            it.status == JobStatus.PAUSED ||
            it.status == JobStatus.PENDING ||
            it.status == JobStatus.BLOCKED
        }
        val canReplay = selectedBatches.isNotEmpty() && selectedBatches.none {
            it.status == JobStatus.RUNNING ||
            it.status == JobStatus.CANCELLING
        }

        val contextualActions = remember(filteredHistory, selectedBatchIds, canCancel, canReplay) {
            listOf(
                ContextualAction(
                    title = if (filteredHistory.isNotEmpty() && selectedBatchIds.size == filteredHistory.size) "Deselect" else "Select All",
                    icon = if (filteredHistory.isNotEmpty() && selectedBatchIds.size == filteredHistory.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                    onClick = {
                        if (filteredHistory.isNotEmpty() && selectedBatchIds.size == filteredHistory.size) {
                            viewModel.clearSelection()
                        } else {
                            viewModel.selectAllBatches(filteredHistory)
                        }
                    }
                ),
                ContextualAction(
                    title = "Filter",
                    icon = Icons.Default.FilterList,
                    onClick = {
                        showSourceFilterSheet = true
                    }
                ),
                ContextualAction(
                    title = "Replay",
                    icon = Icons.Default.Refresh,
                    enabled = canReplay,
                    onClick = {
                        viewModel.replaySelectedBatches()
                    }
                ),
                ContextualAction(
                    title = "Cancel",
                    icon = Icons.Default.Cancel,
                    isDestructive = true,
                    enabled = canCancel,
                    onClick = {
                        showCancelSelectedBatchesDialog = true
                    }
                )
            )
        }

        if (showSourceFilterSheet) {
            SourceSelectionBottomSheet(
                batches = filteredHistory,
                onDismiss = { showSourceFilterSheet = false },
                onSourceSelected = { selectedSource ->
                    viewModel.selectBatchesBySourceDisplayName(selectedSource, filteredHistory)
                    showSourceFilterSheet = false
                }
            )
        }

        if (showCancelSelectedBatchesDialog) {
            val count = selectedBatchIds.size
            ConfirmCancelDialog(
                title = if (count > 1) "Cancel $count Batches?" else "Cancel Batch?",
                message = "Are you sure you want to stop the selected ${if (count > 1) "$count batches" else "batch"}? Any progress made will be preserved, but remaining tasks will stop.",
                onConfirm = {
                    showCancelSelectedBatchesDialog = false
                    viewModel.cancelSelectedBatches()
                },
                onDismiss = { showCancelSelectedBatchesDialog = false }
            )
        }

        LaunchedEffect(isSelectionMode, selectedBatchIds.size, contextualActions) {
            AppNavigationManager.setContextualBottomBar(
                com.halovoid.bunori.ui.navigation.ContextualBottomBarConfig(
                    visible = isSelectionMode,
                    selectedCount = selectedBatchIds.size,
                    actions = contextualActions
                )
            )
        }

        val handleBatchClick: (String) -> Unit = { batchId ->
            if (isSelectionMode) {
                viewModel.toggleBatchSelection(batchId)
            } else {
                onRequestClick(batchId)
            }
        }

        val handleBatchLongClick: (String) -> Unit = { batchId ->
            if (isSelectionMode) {
                viewModel.toggleBatchSelection(batchId)
            } else {
                viewModel.selectBatch(batchId)
            }
        }

        Scaffold(
            containerColor = DarkBackground
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                ScreenHeader(
                    title = "Activity",
                    subtitle = if (requestHistory.isNotEmpty()) "${filteredHistory.size} items" else null,
                    actions = {
                        if (globalStats.total > 0) {
                            DownloadProgressRing(
                                completed = globalStats.completed,
                                total = globalStats.total,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        IconButton(onClick = { viewModel.setCompactMode(!isCompactMode) }) {
                            Icon(
                                imageVector = if (isCompactMode) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = if (isCompactMode) "Carousel View" else "Compact View",
                                tint = PrimaryText
                            )
                        }
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = if (filterType != null) BrandAccent else PrimaryText
                            )
                        }
                    }
                )

                if (showFilterMenu) {
                    FilterBottomSheet(
                        currentFilter = filterType,
                        onDismiss = { showFilterMenu = false },
                        onFilterSelected = { selected ->
                            filterType = selected
                            showFilterMenu = false
                        }
                    )
                }

                if (filteredHistory.isEmpty()) {
                    ActivityEmptyState(
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ActivityListContent(
                        activeBatches = activeBatches,
                        recentBatches = recentBatches,
                        historyBatches = historyBatches,
                        isCompactMode = isCompactMode,
                        onRequestClick = handleBatchClick,
                        onBatchLongClick = handleBatchLongClick,
                        isSelectionMode = isSelectionMode,
                        selectedBatchIds = selectedBatchIds
                    )
                }
            }
        }
    }
}
