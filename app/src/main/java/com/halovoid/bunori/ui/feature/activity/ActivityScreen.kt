package com.halovoid.bunori.ui.feature.activity

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.JobType
import com.halovoid.bunori.ui.core.components.DownloadProgressRing
import com.halovoid.bunori.ui.core.components.ScreenHeader
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.DarkBackground
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.feature.activity.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onRequestClick: (String) -> Unit
) {
    val requestHistory by viewModel.batchHistory.collectAsStateWithLifecycle()
    val globalStats by viewModel.globalStats.collectAsStateWithLifecycle()
    val isCompactMode by viewModel.isCompactMode.collectAsStateWithLifecycle()

    var filterType by remember { mutableStateOf<JobType?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }

    JobActionHandler(
        onResolveWebview = { id, url -> viewModel.resolveWebView(id, url) }
    ) { _ ->
        Scaffold(
            containerColor = DarkBackground
        ) { innerPadding ->
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
                        onRequestClick = onRequestClick
                    )
                }
            }
        }
    }
}
