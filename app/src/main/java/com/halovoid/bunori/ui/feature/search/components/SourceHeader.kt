package com.halovoid.bunori.ui.feature.search.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText

@Composable
fun SourceHeader(
    source: String,
    count: Int = 0,
    onDrillDown: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onDrillDown != null) { onDrillDown?.invoke() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = source,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PrimaryText
        )
        if (onDrillDown != null) {
            IconButton(
                onClick = onDrillDown,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Explore $source",
                    tint = SecondaryText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
