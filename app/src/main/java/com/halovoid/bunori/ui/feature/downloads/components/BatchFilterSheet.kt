package com.halovoid.bunori.ui.feature.downloads.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.DisabledByDefault
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.ui.core.components.AppBottomSheet
import com.halovoid.bunori.ui.core.components.AppBottomSheetDivider
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.ErrorRed
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText
import com.halovoid.bunori.ui.feature.downloads.FilterState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchFilterSheet(
    allBatches: List<Batch>,
    statusFilters: Map<JobStatus, FilterState>,
    onStatusFilterChange: (JobStatus, FilterState) -> Unit,
    onDismiss: () -> Unit
) {
    val availableStatuses = remember(allBatches) {
        allBatches.map { it.status }.distinct().sortedBy { it.name }
    }

    AppBottomSheet(
        onDismiss = onDismiss,
        title = "Filter Status"
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            availableStatuses.forEachIndexed { index, rstatus ->
                val currentState = statusFilters[rstatus] ?: FilterState.NONE
                ListItem(
                    headlineContent = { Text(rstatus.name.lowercase().replaceFirstChar { it.uppercase() }, color = PrimaryText) },
                    leadingContent = {
                        ThreeStateCheckbox(state = currentState)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        onStatusFilterChange(rstatus, currentState.next())
                    }
                )
                if (index < availableStatuses.lastIndex) {
                    AppBottomSheetDivider()
                }
            }
        }
    }
}

@Composable
fun ThreeStateCheckbox(state: FilterState) {
    val icon = when (state) {
        FilterState.NONE -> Icons.Default.CheckBoxOutlineBlank
        FilterState.INCLUDE -> Icons.Default.CheckBox
        FilterState.EXCLUDE -> Icons.Default.DisabledByDefault
    }
    val tint = when (state) {
        FilterState.NONE -> SecondaryText
        FilterState.INCLUDE -> BrandAccent
        FilterState.EXCLUDE -> ErrorRed
    }

    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
