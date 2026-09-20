package com.halovoid.bunori.ui.feature.browse.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.data.db.entities.JobType
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.ui.core.components.SecurityCheckDialog
import com.halovoid.bunori.ui.core.theme.*

fun LazyListScope.batchHistorySection(
    batchHistory: List<Batch>,
    onRequestClick: (String) -> Unit,
    onGroupClick: (JobType) -> Unit,
    onReplay: (String) -> Unit = {},
    onCancel: (String) -> Unit = {},
    onContinue: (String) -> Unit = {},
    onSecurityClick: (Batch) -> Unit = {},
    cancellingRequestIds: Set<String> = emptySet(),
    activeActionIds: Set<String> = emptySet(),
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
    allowAction: Boolean = false,
    forceUngrouped: Boolean = false
) {
    if (batchHistory.isEmpty()) return

    if (forceUngrouped) {
        items(batchHistory, key = { it.id }) { request ->
            Box(modifier = Modifier.padding(horizontal = horizontalPadding).padding(bottom = 12.dp)) {
                BatchCard(
                    batch = request,
                    onClick = { onRequestClick(request.id) },
                    onReplay = { onReplay(request.id) },
                    onCancel = { onCancel(request.id) },
                    onContinue = { onContinue(request.id) },
                    onSecurityClick = { onSecurityClick(request) },
                    isCancelling = cancellingRequestIds.contains(request.id),
                    isActionPending = activeActionIds.contains(request.id),
                    allowAction = allowAction
                )
            }
        }
    } else {
        val groupedRequests = batchHistory.groupBy { it.type }

        groupedRequests.forEach { (type, requests) ->
            if (requests.size > 1) {
                item(key = "group_$type") {
                    Box(modifier = Modifier.padding(horizontal = horizontalPadding).padding(bottom = 12.dp)) {
                        BatchGroupCard(
                            type = type,
                            batches = requests,
                            onClick = { onGroupClick(type) }
                        )
                    }
                }
            } else {
                items(requests, key = { it.id }) { request ->
                    Box(modifier = Modifier.padding(horizontal = horizontalPadding).padding(bottom = 12.dp)) {
                        BatchCard(
                            batch = request,
                            onClick = { onRequestClick(request.id) },
                            onReplay = { onReplay(request.id) },
                            onCancel = { onCancel(request.id) },
                            onContinue = { onContinue(request.id) },
                            onSecurityClick = { onSecurityClick(request) },
                            isCancelling = cancellingRequestIds.contains(request.id),
                            isActionPending = activeActionIds.contains(request.id),
                            allowAction = allowAction
                        )
                    }
                }
            }
        }
    }
}

// Aliases for compatibility
fun LazyListScope.requestHistorySection(
    batchHistory: List<Batch>,
    onRequestClick: (String) -> Unit,
    onGroupClick: (JobType) -> Unit,
    onReplay: (String) -> Unit = {},
    onCancel: (String) -> Unit = {},
    onContinue: (String) -> Unit = {},
    onSecurityClick: (Batch) -> Unit = {},
    cancellingRequestIds: Set<String> = emptySet(),
    activeActionIds: Set<String> = emptySet(),
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
    allowAction: Boolean = false,
    forceUngrouped: Boolean = false
) = batchHistorySection(
    batchHistory = batchHistory,
    onRequestClick = onRequestClick,
    onGroupClick = onGroupClick,
    onReplay = onReplay,
    onCancel = onCancel,
    onContinue = onContinue,
    onSecurityClick = onSecurityClick,
    cancellingRequestIds = cancellingRequestIds,
    activeActionIds = activeActionIds,
    horizontalPadding = horizontalPadding,
    allowAction = allowAction,
    forceUngrouped = forceUngrouped
)

@Composable
fun BatchActionHandler(
    onResolveWebview: (String, String) -> Unit,
    content: @Composable (onSecurityClick: (Batch) -> Unit) -> Unit
) {
    var securityDialogBatch by remember { mutableStateOf<Batch?>(null) }

    if (securityDialogBatch != null) {
        SecurityCheckDialog(
            novelName = securityDialogBatch!!.name,
            onConfirm = {
                val req = securityDialogBatch!!
                securityDialogBatch = null
                onResolveWebview(req.id, req.url ?: req.novelUrl)
            },
            onDismiss = { securityDialogBatch = null }
        )
    }

    content { securityDialogBatch = it }
}

// Alias for compatibility
@Composable
fun RequestActionHandler(
    onResolveWebview: (String, String) -> Unit,
    content: @Composable (onSecurityClick: (Batch) -> Unit) -> Unit
) = BatchActionHandler(onResolveWebview, content)

@Composable
fun BatchGroupCard(
    type: JobType,
    batches: List<Batch>,
    onClick: () -> Unit
) {
    val totalSuccess = batches.sumOf { it.progressSuccess }
    val totalFailed = batches.sumOf { it.progressFailed }
    val totalProgress = batches.sumOf { it.progressTotal }
    
    val latestUpdate = batches.maxOfOrNull { it.updatedAt } ?: 0L

    val typeName = when (type) {
        JobType.NOVEL_METADATA -> "Metadata"
        JobType.CHAPTER -> "Chapters"
        JobType.ARTIFACT -> "Exports"
        JobType.RANGE_DOWNLOAD -> "Downloads"
        JobType.BACKUP -> "Backups"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = typeName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = BrandAccent.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = type.name, 
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 9.sp,
                                color = BrandAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Text(
                            text = "${batches.size} jobs",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )

                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText.copy(alpha = 0.4f)
                        )

                        Text(
                            text = "$totalSuccess/$totalProgress",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )

                        if (totalFailed > 0) {
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryText.copy(alpha = 0.4f)
                            )
                            Text(
                                text = "$totalFailed failed",
                                style = MaterialTheme.typography.labelSmall,
                                color = ErrorRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }

                        if (latestUpdate > 0) {
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryText.copy(alpha = 0.4f)
                            )
                            Text(
                                text = android.text.format.DateUtils.getRelativeTimeSpanString(latestUpdate).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryText.copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = SecondaryText.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
