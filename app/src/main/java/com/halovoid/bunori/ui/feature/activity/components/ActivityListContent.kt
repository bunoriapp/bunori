package com.halovoid.bunori.ui.feature.activity.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.ui.core.theme.SecondaryText

@Composable
fun ActivityListContent(
    activeBatches: List<Batch>,
    recentBatches: List<Batch>,
    historyBatches: List<Batch>,
    isCompactMode: Boolean,
    onRequestClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
                    ActivityActiveCarousel(
                        activeBatches = activeBatches,
                        onRequestClick = onRequestClick
                    )
                }
            }
        }

        // 2. Recent Section
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
                CompactJobItem(
                    batch = batch,
                    onClick = { onRequestClick(batch.id) }
                )
            }
        }
    }
}
