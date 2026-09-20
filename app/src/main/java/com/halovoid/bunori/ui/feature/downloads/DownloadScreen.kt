package com.halovoid.bunori.ui.feature.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.JobType
import com.halovoid.bunori.ui.core.components.DownloadProgressRing
import com.halovoid.bunori.ui.core.components.MutedEmptyState
import com.halovoid.bunori.ui.core.components.ScreenHeader
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.DarkBackground
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText
import com.halovoid.bunori.ui.feature.browse.components.CompactRequestItem
import com.halovoid.bunori.ui.feature.browse.components.FilterBottomSheet
import com.halovoid.bunori.ui.feature.browse.components.RequestActionHandler
import com.halovoid.bunori.ui.feature.browse.components.RequestCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    onRequestClick: (String) -> Unit,
    onGroupClick: (JobType) -> Unit
) {
    val requestHistory by viewModel.batchHistory.collectAsStateWithLifecycle()
    val globalStats by viewModel.globalStats.collectAsStateWithLifecycle()
    val cancellingRequestIds by viewModel.cancellingRequestIds.collectAsStateWithLifecycle()
    val activeActionIds by viewModel.activeActionIds.collectAsStateWithLifecycle()

    var filterType by remember { mutableStateOf<JobType?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }

    RequestActionHandler(
        onResolveWebview = { id, url -> viewModel.resolveWebView(id, url) }
    ) { onSecurityClick ->
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
                    title = "Downloads",
                    subtitle = if (requestHistory.isNotEmpty()) "${filteredHistory.size} items" else null,
                    actions = {
                        if (globalStats.total > 0) {
                            DownloadProgressRing(
                                completed = globalStats.completed,
                                total = globalStats.total,
                                onClick = {
                                    activeBatches.firstOrNull()?.let { onRequestClick(it.id) }
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = PrimaryText
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
                    MutedEmptyState(
                        title = "No Downloads Yet",
                        description = "Monitor and manage all your background tasks here. From fetching metadata to downloading chapters for offline reading, every request's status can be tracked in real-time.",
                        icon = Icons.Outlined.DownloadForOffline,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                    ) {
                        // 1. Active / Running Section
                        if (activeBatches.isNotEmpty()) {
                            item(key = "header_active") {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SecondaryText.copy(alpha = 0.7f),
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(
                                        start = 20.dp,
                                        end = 20.dp,
                                        top = 8.dp,
                                        bottom = 10.dp
                                    )
                                )
                            }

                            item(key = "active_content") {
                                if (activeBatches.size == 1) {
                                    val batch = activeBatches.first()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp)
                                    ) {
                                        RequestCard(
                                            batch = batch,
                                            onClick = { onRequestClick(batch.id) },
                                            allowAction = false
                                        )
                                    }
                                } else {
                                    val pagerState = rememberPagerState(pageCount = { activeBatches.size })
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp)
                                    ) {
                                        HorizontalPager(
                                            state = pagerState,
                                            pageSpacing = 12.dp,
                                            modifier = Modifier.fillMaxWidth()
                                        ) { page ->
                                            val batch = activeBatches[page]
                                            RequestCard(
                                                batch = batch,
                                                onClick = { onRequestClick(batch.id) },
                                                allowAction = false
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            repeat(activeBatches.size) { index ->
                                                val isSelected = pagerState.currentPage == index
                                                Box(
                                                    modifier = Modifier
                                                        .padding(horizontal = 3.dp)
                                                        .size(if (isSelected) 6.dp else 4.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isSelected) BrandAccent else SecondaryText.copy(alpha = 0.3f)
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Recent Section (Compact list separated with borders - label omitted)
                        if (recentBatches.isNotEmpty()) {
                            if (activeBatches.isNotEmpty()) {
                                item(key = "spacing_after_active") {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            items(recentBatches, key = { it.id }) { batch ->
                                CompactRequestItem(
                                    batch = batch,
                                    onClick = { onRequestClick(batch.id) }
                                )
                            }
                        }

                        // 3. History Section (Compact list separated with borders)
                        if (historyBatches.isNotEmpty()) {
                            item(key = "header_history") {
                                Text(
                                    text = "HISTORY",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SecondaryText.copy(alpha = 0.7f),
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(
                                        start = 20.dp,
                                        end = 20.dp,
                                        top = if (recentBatches.isNotEmpty() || activeBatches.isNotEmpty()) 20.dp else 8.dp,
                                        bottom = 8.dp
                                    )
                                )
                            }

                            items(historyBatches, key = { it.id }) { batch ->
                                CompactRequestItem(
                                    batch = batch,
                                    onClick = { onRequestClick(batch.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
