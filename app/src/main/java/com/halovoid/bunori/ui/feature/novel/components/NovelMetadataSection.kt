package com.halovoid.bunori.ui.feature.novel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.ui.core.theme.*

@Composable
fun NovelMetadataTable(novel: Novel) {
    val sources = novel.chapters.map { it.scanlationSource }.filter { it.isNotBlank() && it != "NotProvided" && it != "Not Provided" }.distinct()
    val sourceDisplay = when {
        sources.isEmpty() -> novel.crawlerName
        sources.size <= 2 -> sources.joinToString(", ")
        else -> "${sources.take(2).joinToString(", ")}, ..."
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        MetadataSection(
            data = mapOf(
                "Status" to (novel.status ?: "Unknown"),
                "Chapters" to novel.chapters.size.toString(),
                "Sources" to sourceDisplay
            ),
            singleLine = true
        )
    }
}

@Composable
fun MetadataSection(
    data: Map<String, String>,
    singleLine: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        data.forEach { (key, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = key, 
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryText,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = value, 
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrimaryText, 
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                    overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}
