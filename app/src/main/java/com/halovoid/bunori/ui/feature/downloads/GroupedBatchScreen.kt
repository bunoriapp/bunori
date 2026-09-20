package com.halovoid.bunori.ui.feature.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.JobType
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.browse.components.BatchActionHandler
import com.halovoid.bunori.ui.feature.browse.components.batchHistorySection
import com.halovoid.bunori.ui.feature.downloads.components.BatchFilterSheet

@Composable
fun GroupedBatchScreen(
    type: JobType,
    batches: List<Batch>,
    allBatches: List<Batch>,
    statusFilters: Map<JobStatus, FilterState>,
    onStatusFilterChange: (JobStatus, FilterState) -> Unit,
    onBack: () -> Unit,
    onRequestClick: (String) -> Unit,
    onReplay: (String) -> Unit = {},
    onCancel: (String) -> Unit = {},
    onContinue: (String) -> Unit = {},
    onResolveWebview: (String, String) -> Unit = { _, _ -> },
    cancellingRequestIds: Set<String> = emptySet(),
    activeActionIds: Set<String> = emptySet(),
    allowAction: Boolean = false
) {
    val filteredRequests = batches.filter { it.type == type }
    val unfilteredRequestsForType = remember(allBatches, type) {
        allBatches.filter { it.type == type }
    }
    var showFilterMenu by remember { mutableStateOf(false) }
    val isFilterActive = statusFilters.values.any { it != FilterState.NONE }

    BatchActionHandler(
        onResolveWebview = onResolveWebview
    ) { onSecurityClick ->
        val title = when (type) {
            JobType.NOVEL_METADATA -> "Metadata"
            JobType.CHAPTER -> "Chapter Downloads"
            JobType.ARTIFACT -> "Exports"
            JobType.RANGE_DOWNLOAD -> "Downloads"
            JobType.BACKUP -> "Backups"
        }

        Scaffold(
            containerColor = DarkBackground,
            topBar = {
                AppTopBar(
                    title = title,
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = if (isFilterActive) BrandAccent else PrimaryText
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                batchHistorySection(
                    batchHistory = filteredRequests,
                    onRequestClick = onRequestClick,
                    onGroupClick = {},
                    onReplay = onReplay,
                    onCancel = onCancel,
                    onContinue = onContinue,
                    onSecurityClick = onSecurityClick,
                    cancellingRequestIds = cancellingRequestIds,
                    activeActionIds = activeActionIds,
                    allowAction = allowAction,
                    forceUngrouped = true
                )
            }
        }
    }

    if (showFilterMenu) {
        BatchFilterSheet(
            allBatches = unfilteredRequestsForType,
            statusFilters = statusFilters,
            onStatusFilterChange = onStatusFilterChange,
            onDismiss = { showFilterMenu = false }
        )
    }
}

// Alias for compatibility
@Composable
fun GroupedRequestsScreen(
    type: JobType,
    batches: List<Batch>,
    allBatches: List<Batch>,
    statusFilters: Map<JobStatus, FilterState>,
    onStatusFilterChange: (JobStatus, FilterState) -> Unit,
    onBack: () -> Unit,
    onRequestClick: (String) -> Unit,
    onReplay: (String) -> Unit = {},
    onCancel: (String) -> Unit = {},
    onContinue: (String) -> Unit = {},
    onResolveWebview: (String, String) -> Unit = { _, _ -> },
    cancellingRequestIds: Set<String> = emptySet(),
    activeActionIds: Set<String> = emptySet(),
    allowAction: Boolean = false
) = GroupedBatchScreen(
    type = type,
    batches = batches,
    allBatches = allBatches,
    statusFilters = statusFilters,
    onStatusFilterChange = onStatusFilterChange,
    onBack = onBack,
    onRequestClick = onRequestClick,
    onReplay = onReplay,
    onCancel = onCancel,
    onContinue = onContinue,
    onResolveWebview = onResolveWebview,
    cancellingRequestIds = cancellingRequestIds,
    activeActionIds = activeActionIds,
    allowAction = allowAction
)
