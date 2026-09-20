package com.halovoid.bunori.ui.feature.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.halovoid.bunori.ui.core.components.AppBottomSheet
import com.halovoid.bunori.ui.core.components.AppBottomSheetDivider
import com.halovoid.bunori.ui.core.components.AppBottomSheetGroup
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.PrimaryText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelPruneFrequencyBottomSheet(
    currentFrequency: String,
    onFrequencySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf("Off", "Daily", "Every 7 Days", "Every 10 Days", "Every 30 Days")

    AppBottomSheet(
        onDismiss = onDismiss,
        title = "Novel Pruning Frequency",
        subtitle = "Choose how often unreferenced non-library novels are pruned."
    ) {
        AppBottomSheetGroup {
            options.forEachIndexed { index, option ->
                val isSelected = currentFrequency == option
                ListItem(
                    headlineContent = {
                        Text(
                            text = option,
                            color = if (isSelected) BrandAccent else PrimaryText,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingContent = {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = BrandAccent)
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        onFrequencySelected(option)
                        onDismiss()
                    }
                )
                if (index < options.lastIndex) {
                    AppBottomSheetDivider()
                }
            }
        }
    }
}
