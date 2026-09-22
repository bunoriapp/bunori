package com.halovoid.bunori.ui.feature.activity.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.PrimaryText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailTopBar(
    record: Batch?,
    isCancelling: Boolean,
    isActionPending: Boolean,
    onBackClick: () -> Unit,
    onPauseBatch: (String) -> Unit,
    onResumeBatch: (String) -> Unit,
    onReplayBatch: (String) -> Unit,
    onRequestCancelBatch: () -> Unit,
    onResolveSecurityCheck: (Batch) -> Unit,
) {
    AppTopBar(
        title = "Batch",
        onBack = onBackClick,
        actions = {
            if (record != null) {
                if (isCancelling || isActionPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                } else {
                    when (record.status) {
                        JobStatus.RUNNING -> {
                            IconButton(onClick = { onPauseBatch(record.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Pause",
                                    tint = PrimaryText
                                )
                            }
                            IconButton(onClick = onRequestCancelBatch) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = PrimaryText
                                )
                            }
                        }
                        JobStatus.PAUSED -> {
                            IconButton(onClick = { onResumeBatch(record.id) }) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Resume",
                                    tint = PrimaryText
                                )
                            }
                            IconButton(onClick = onRequestCancelBatch) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = PrimaryText
                                )
                            }
                        }
                        JobStatus.PENDING -> {
                            IconButton(onClick = onRequestCancelBatch) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = PrimaryText
                                )
                            }
                        }
                        JobStatus.BLOCKED -> {
                            IconButton(onClick = { onResolveSecurityCheck(record) }) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Resolve Security Check",
                                    tint = BrandAccent
                                )
                            }
                            IconButton(onClick = onRequestCancelBatch) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = PrimaryText
                                )
                            }
                        }
                        JobStatus.SUCCESS, JobStatus.FAILED, JobStatus.CANCELLED -> {
                            IconButton(onClick = { onReplayBatch(record.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Replay",
                                    tint = PrimaryText
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    )
}
