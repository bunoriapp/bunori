package com.halovoid.bunori.ui.feature.activity.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.domain.models.Task
import com.halovoid.bunori.ui.core.components.AppBottomSheet
import com.halovoid.bunori.ui.core.components.AppBottomSheetDivider
import com.halovoid.bunori.ui.core.components.AppBottomSheetGroup
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText

fun formatStatusName(status: JobStatus): String {
    return when (status) {
        JobStatus.BLOCKED -> "Blocked"
        JobStatus.RUNNING -> "Running"
        JobStatus.PENDING -> "Queued"
        JobStatus.PAUSED -> "Paused"
        JobStatus.CANCELLING -> "Cancelling"
        JobStatus.FAILED -> "Failed"
        JobStatus.CANCELLED -> "Cancelled"
        JobStatus.SUCCESS -> "Completed"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusFilterBottomSheet(
    distinctStatuses: List<JobStatus>,
    tasks: List<Task>,
    onStatusSelected: (JobStatus) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(
        onDismiss = onDismiss,
        title = "Select by Status",
        subtitle = "Choose a status to select all matching tasks",
        modifier = modifier
    ) {
        AppBottomSheetGroup {
            distinctStatuses.forEachIndexed { index, status ->
                val count = tasks.count { it.status == status }
                ListItem(
                    headlineContent = {
                        Text(
                            text = formatStatusName(status),
                            color = PrimaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingContent = {
                        StatusIndicator(status = status)
                    },
                    trailingContent = {
                        Text(
                            text = count.toString(),
                            color = SecondaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        onStatusSelected(status)
                        onDismiss()
                    }
                )
                if (index < distinctStatuses.lastIndex) {
                    AppBottomSheetDivider()
                }
            }
        }
    }
}
