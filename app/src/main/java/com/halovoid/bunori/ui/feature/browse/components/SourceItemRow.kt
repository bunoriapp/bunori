package com.halovoid.bunori.ui.feature.browse.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.ui.core.components.SourceIcon
import com.halovoid.bunori.ui.core.theme.BorderColor
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText
import com.halovoid.bunori.ui.feature.source.ExtensionUiItem
import java.net.URI

@Composable
fun SourceItemRow(
    source: ExtensionUiItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SourceIcon(
                model = source.iconModel,
                fallbackText = source.name,
                size = 42.dp,
                shape = RoundedCornerShape(10.dp),
                contentPadding = 0.dp,
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val langDisplay = if (source.lang.equals("all", ignoreCase = true)) "Multi" else source.lang.uppercase()
                val hostDisplay = try {
                    URI(source.baseUrl).host ?: source.baseUrl
                } catch (_: Exception) {
                    source.baseUrl
                }
                Text(
                    text = "$langDisplay • $hostDisplay",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = SecondaryText.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
        HorizontalDivider(
            color = BorderColor.copy(alpha = 0.25f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
