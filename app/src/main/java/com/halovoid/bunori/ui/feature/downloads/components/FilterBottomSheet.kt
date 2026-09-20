package com.halovoid.bunori.ui.feature.downloads.components

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.halovoid.bunori.data.db.entities.JobType
import com.halovoid.bunori.ui.core.components.AppBottomSheet
import com.halovoid.bunori.ui.core.components.AppBottomSheetDivider
import com.halovoid.bunori.ui.core.components.AppBottomSheetGroup
import com.halovoid.bunori.ui.core.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    currentFilter: JobType?,
    onDismiss: () -> Unit,
    onFilterSelected: (JobType?) -> Unit
) {
    AppBottomSheet(
        onDismiss = onDismiss,
        title = "Filter Results"
    ) {
        AppBottomSheetGroup {
            ListItem(
                headlineContent = { Text("All Downloads", color = PrimaryText) },
                trailingContent = {
                    if (currentFilter == null) Icon(Icons.Default.Check, contentDescription = null, tint = BrandAccent)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onFilterSelected(null) }
            )
            AppBottomSheetDivider()

            JobType.entries.forEach { type ->
                val label = when (type) {
                    JobType.NOVEL_METADATA -> "Metadata"
                    JobType.CHAPTER -> "Chapters"
                    JobType.ARTIFACT -> "Exports"
                    JobType.RANGE_DOWNLOAD -> "Downloads"
                    JobType.BACKUP -> "Backups"
                }
                ListItem(
                    headlineContent = { Text(label, color = PrimaryText) },
                    trailingContent = {
                        if (currentFilter == type) Icon(Icons.Default.Check, contentDescription = null, tint = BrandAccent)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onFilterSelected(type) }
                )
                if (type != JobType.entries.last()) {
                    AppBottomSheetDivider()
                }
            }
        }
    }
}
