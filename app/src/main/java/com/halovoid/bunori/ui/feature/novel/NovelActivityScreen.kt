package com.halovoid.bunori.ui.feature.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.components.MutedEmptyState
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.DarkBackground
import com.halovoid.bunori.ui.core.theme.SecondaryText
import com.halovoid.bunori.ui.feature.request.components.CompactRequestItem
import com.halovoid.bunori.ui.feature.request.components.RequestActionHandler
import com.halovoid.bunori.ui.feature.request.components.RequestCard

@Composable
fun NovelActivityScreen(
    batches: List<Batch>,
    onBack: () -> Unit,
    onRequestClick: (String) -> Unit,
    onReplay: (String) -> Unit,
    onCancel: (String) -> Unit,
    onContinue: (String) -> Unit,
    onResolveWebview: (String, String) -> Unit,
    cancellingRequestIds: Set<String>,
    activeActionIds: Set<String>
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Activity",
                onBack = onBack
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        RequestActionHandler(
            onResolveWebview = onResolveWebview
        ) { onSecurityClick ->
            val activeBatches = remember(batches) {
                batches.filter {
                    it.status == JobStatus.RUNNING ||
                    it.status == JobStatus.PAUSED ||
                    it.status == JobStatus.PENDING ||
                    it.status == JobStatus.BLOCKED ||
                    it.status == JobStatus.CANCELLING
                }
            }

            val nonActiveBatches = remember(batches) {
                batches.filter {
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
                if (batches.isEmpty()) {
                    MutedEmptyState(
                        title = "No Activity Yet",
                        description = "Monitor and manage background tasks for this novel here. Real-time progress and history will appear when novel crawl jobs run.",
                        icon = Icons.Outlined.DownloadForOffline,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                    ) {
                        // 1. ACTIVE Section
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
                                            onReplay = { onReplay(batch.id) },
                                            onCancel = { onCancel(batch.id) },
                                            onContinue = { onContinue(batch.id) },
                                            onSecurityClick = { onSecurityClick(batch) },
                                            isCancelling = cancellingRequestIds.contains(batch.id),
                                            isActionPending = activeActionIds.contains(batch.id),
                                            allowAction = true
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
                                                onReplay = { onReplay(batch.id) },
                                                onCancel = { onCancel(batch.id) },
                                                onContinue = { onContinue(batch.id) },
                                                onSecurityClick = { onSecurityClick(batch) },
                                                isCancelling = cancellingRequestIds.contains(batch.id),
                                                isActionPending = activeActionIds.contains(batch.id),
                                                allowAction = true
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

                        // 2. Recent Section
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

                        // 3. History Section
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
