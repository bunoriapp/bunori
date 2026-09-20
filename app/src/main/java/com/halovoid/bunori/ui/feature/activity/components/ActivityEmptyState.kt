package com.halovoid.bunori.ui.feature.activity.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.halovoid.bunori.ui.core.components.MutedEmptyState

@Composable
fun ActivityEmptyState(
    modifier: Modifier = Modifier
) {
    MutedEmptyState(
        title = "No Activity Yet",
        description = "Monitor and manage all your background tasks here. From fetching metadata to downloading chapters for offline reading, every task's status can be tracked in real-time.",
        icon = Icons.Outlined.History,
        modifier = modifier
    )
}
