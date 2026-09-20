package com.halovoid.bunori.ui.feature.downloads.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.ui.core.components.ConfirmCancelDialog
import com.halovoid.bunori.ui.core.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactJobItem(
    batch: Batch,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val formattedDate = remember(batch.createdAt, locale) {
        SimpleDateFormat("MMM dd, HH:mm", locale).format(Date(batch.createdAt))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = batch.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (batch.progressTotal > 0) {
                        Text(
                            text = if (batch.progressSuccess > 0) {
                                "${batch.progressSuccess}/${batch.progressTotal} tasks"
                            } else {
                                "${batch.progressTotal} tasks"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText,
                            fontSize = 11.sp
                        )

                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText.copy(alpha = 0.4f)
                        )
                    }

                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }

            StatusIndicator(batch.status)
        }

        HorizontalDivider(
            color = BorderColor.copy(alpha = 0.25f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

@Composable
fun StatusIndicator(
    status: JobStatus,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (status) {
            JobStatus.RUNNING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.8.dp,
                    color = PrimaryText
                )
                Text("Running", style = MaterialTheme.typography.labelSmall, color = SecondaryText, fontSize = 11.sp)
            }
            JobStatus.PAUSED -> {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = "Paused",
                    tint = PrimaryText,
                    modifier = Modifier.size(12.dp)
                )
                Text("Paused", style = MaterialTheme.typography.labelSmall, color = SecondaryText, fontSize = 11.sp)
            }
            JobStatus.SUCCESS -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = PrimaryText,
                    modifier = Modifier.size(12.dp)
                )
                Text("Completed", style = MaterialTheme.typography.labelSmall, color = SecondaryText, fontSize = 11.sp)
            }
            JobStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Failed",
                    tint = PrimaryText,
                    modifier = Modifier.size(12.dp)
                )
                Text("Failed", style = MaterialTheme.typography.labelSmall, color = SecondaryText, fontSize = 11.sp)
            }
            JobStatus.CANCELLED -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancelled",
                    tint = PrimaryText,
                    modifier = Modifier.size(12.dp)
                )
                Text("Cancelled", style = MaterialTheme.typography.labelSmall, color = SecondaryText, fontSize = 11.sp)
            }
            JobStatus.CANCELLING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.8.dp,
                    color = PrimaryText
                )
                Text("Cancelling", style = MaterialTheme.typography.labelSmall, color = SecondaryText, fontSize = 11.sp)
            }
            JobStatus.BLOCKED -> {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Blocked",
                    tint = PrimaryText,
                    modifier = Modifier.size(12.dp)
                )
                Text("Blocked", style = MaterialTheme.typography.labelSmall, color = SecondaryText, fontSize = 11.sp)
            }
            JobStatus.PENDING -> {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Queued",
                    tint = PrimaryText,
                    modifier = Modifier.size(12.dp)
                )
                Text("Queued", style = MaterialTheme.typography.labelSmall, color = SecondaryText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun JobCard(
    batch: Batch,
    onClick: (() -> Unit)? = null,
    onReplay: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onContinue: (() -> Unit)? = null,
    onSecurityClick: (() -> Unit)? = null,
    allowAction: Boolean = false,
    isCancelling: Boolean = false,
    isActionPending: Boolean = false,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val formattedDate = remember(batch.createdAt, locale) {
        SimpleDateFormat("MMM dd, HH:mm", locale).format(Date(batch.createdAt))
    }

    var showCancelDialog by remember { mutableStateOf(false) }

    if (showCancelDialog && onCancel != null) {
        ConfirmCancelDialog(
            title = "Cancel Batch?",
            message = "Are you sure you want to stop \"${batch.name}\"? Any completed progress will be preserved.",
            onConfirm = {
                showCancelDialog = false
                onCancel()
            },
            onDismiss = { showCancelDialog = false }
        )
    }

    val progress = if (batch.progressTotal > 0) {
        batch.progressSuccess.toFloat() / batch.progressTotal
    } else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (onClick != null && !isCancelling) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Row 1: TYPE . DATE (and High Priority) on Left | Status on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = batch.type.name,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = SecondaryText,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (batch.priority > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FlashOn,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = SecondaryText.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "High Priority",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryText.copy(alpha = 0.7f),
                                fontSize = 9.sp
                            )
                        }
                    }

                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText.copy(alpha = 0.4f)
                    )

                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (batch.status == JobStatus.BLOCKED && onSecurityClick != null) {
                        IconButton(
                            onClick = onSecurityClick,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = "Security Check Needed",
                                tint = BrandAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    StatusIndicator(batch.status)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Title
            Text(
                text = batch.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Row 3: x/y tasks on Left | % on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (batch.progressTotal > 0) {
                        "${batch.progressSuccess}/${batch.progressTotal} tasks"
                    } else {
                        "0 tasks"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryText,
                    fontSize = 11.sp
                )

                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 4: Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = BrandAccent,
                trackColor = DarkSurfaceVariant
            )

            val isCompleted = batch.rstatus == JobStatus.SUCCESS ||
                    (batch.progressTotal > 0 && batch.progressSuccess >= batch.progressTotal)

            if (!batch.error.isNullOrBlank() && !isCompleted) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = ErrorRed.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = batch.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRed.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (allowAction) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BorderColor.copy(alpha = 0.3f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = ErrorRed
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Cancelling",
                            style = MaterialTheme.typography.labelSmall,
                            color = ErrorRed,
                            fontSize = 10.sp
                        )
                    } else if (batch.status == JobStatus.RUNNING || batch.status == JobStatus.PENDING || batch.status == JobStatus.BLOCKED) {
                        if (onCancel != null) {
                            TextButton(
                                onClick = { showCancelDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed.copy(alpha = 0.8f))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (batch.status == JobStatus.PAUSED) {
                        if (onContinue != null) {
                            TextButton(
                                onClick = onContinue,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = BrandAccent)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (onCancel != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { showCancelDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed.copy(alpha = 0.8f))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (batch.status == JobStatus.SUCCESS || batch.status == JobStatus.FAILED || batch.status == JobStatus.CANCELLED) {
                        if (onReplay != null) {
                            TextButton(
                                onClick = onReplay,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = SecondaryText)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Replay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}