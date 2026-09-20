package com.halovoid.bunori.ui.feature.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.DynamicFeed
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
import com.halovoid.bunori.ui.feature.activity.components.CompactJobItem
import com.halovoid.bunori.ui.feature.activity.components.FilterBottomSheet
import com.halovoid.bunori.ui.feature.activity.components.JobActionHandler
import com.halovoid.bunori.ui.feature.activity.components.JobCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onRequestClick: (String) -> Unit
) {
    val requestHistory by viewModel.batchHistory.collectAsStateWithLifecycle()
    val globalStats by viewModel.globalStats.collectAsStateWithLifecycle()
    val isCompactMode by viewModel.isCompactMode.collectAsStateWithLifecycle()
    val cancellingRequestIds by viewModel.cancellingRequestIds.collectAsStateWithLifecycle()
    val activeActionIds by viewModel.activeActionIds.collectAsStateWithLifecycle()

    var filterType by remember { mutableStateOf<JobType?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }

    JobActionHandler(
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
                    MutedEmptyState(
                        title = "No Activity Yet",
                        description = "Monitor and manage all your background tasks here. From fetching metadata to downloading chapters for offline reading, every task's status can be tracked in real-time.",
                        icon = Icons.Outlined.DynamicFeed,
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

                            if (isCompactMode) {
                                items(activeBatches, key = { it.id }) { batch ->
                                    CompactJobItem(
                                        batch = batch,
                                        onClick = { onRequestClick(batch.id) },
                                        showProgressBackground = true
                                    )
                                }
                            } else {
                                item(key = "active_content") {
                                    if (activeBatches.size == 1) {
                                        val batch = activeBatches.first()
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp)
                                        ) {
                                            JobCard(
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
                                                JobCard(
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
                        }

                        // 2. Recent Section (Compact list separated with borders - label omitted)
                        if (recentBatches.isNotEmpty()) {
                            if (activeBatches.isNotEmpty()) {
                                item(key = "spacing_after_active") {
                                    Spacer(modifier = Modifier.height(if (isCompactMode) 8.dp else 16.dp))
                                }
                            }

                            items(recentBatches, key = { it.id }) { batch ->
                                CompactJobItem(
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
                                CompactJobItem(
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

// Backward compatibility alias
@Composable
fun DownloadScreen(
    viewModel: ActivityViewModel,
    onRequestClick: (String) -> Unit
) = ActivityScreen(viewModel = viewModel, onRequestClick = onRequestClick)
