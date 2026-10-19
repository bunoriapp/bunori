package com.halovoid.bunori.ui.feature.novel.components.artifact

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.ui.core.theme.*

private enum class ExportDialogView {
    MAIN,
    SELECT_FORMAT
}

@Composable
fun ArtifactExportDialog(
    allChapters: List<Chapter> = emptyList(),
    onDismiss: () -> Unit,
    onExport: (ExportFormat) -> Unit
) {
    var activeView by remember {
        mutableStateOf(ExportDialogView.MAIN)
    }

    var selectedFormat by remember {
        mutableStateOf(ExportFormat.EPUB)
    }

    var formatSearchQuery by remember { mutableStateOf("") }

    val totalCount = allChapters.size
    val downloadedCount = remember(allChapters) {
        allChapters.count { it.isDownloaded }
    }
    val canExport = totalCount > 0

    when (activeView) {
        ExportDialogView.SELECT_FORMAT -> {
            val filteredFormats = remember(formatSearchQuery) {
                if (formatSearchQuery.isBlank()) ExportFormat.entries
                else ExportFormat.entries.filter { format ->
                    format.name.contains(formatSearchQuery, ignoreCase = true) ||
                    format.extension.contains(formatSearchQuery, ignoreCase = true) ||
                    when (format) {
                        ExportFormat.EPUB -> "reflowable ebook epub".contains(formatSearchQuery, ignoreCase = true)
                        ExportFormat.PDF -> "fixed document pdf".contains(formatSearchQuery, ignoreCase = true)
                    }
                }
            }

            AlertDialog(
                onDismissRequest = {
                    activeView = ExportDialogView.MAIN
                    formatSearchQuery = ""
                },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                activeView = ExportDialogView.MAIN
                                formatSearchQuery = ""
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PrimaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Select Format",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = formatSearchQuery,
                            onValueChange = { formatSearchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Search format...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryText
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = SecondaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (formatSearchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { formatSearchQuery = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = SecondaryText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandAccent,
                                unfocusedBorderColor = BorderColor.copy(alpha = 0.3f),
                                focusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.2f),
                                cursorColor = BrandAccent,
                                focusedTextColor = PrimaryText,
                                unfocusedTextColor = PrimaryText
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.5.dp, BorderColor.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                        ) {
                            if (filteredFormats.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No formats found",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SecondaryText
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    filteredFormats.forEachIndexed { index, format ->
                                        val isSelected = selectedFormat == format
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedFormat = format
                                                    activeView = ExportDialogView.MAIN
                                                    formatSearchQuery = ""
                                                }
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = format.extension.uppercase(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) BrandAccent else PrimaryText
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = when (format) {
                                                        ExportFormat.EPUB -> "Reflowable eBook"
                                                        ExportFormat.PDF -> "Fixed Document"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SecondaryText
                                                )
                                            }

                                            RadioButton(
                                                selected = isSelected,
                                                onClick = {
                                                    selectedFormat = format
                                                    activeView = ExportDialogView.MAIN
                                                    formatSearchQuery = ""
                                                },
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = BrandAccent,
                                                    unselectedColor = SecondaryText.copy(alpha = 0.5f)
                                                ),
                                                modifier = Modifier.scale(0.9f)
                                            )
                                        }
                                        if (index < filteredFormats.lastIndex) {
                                            HorizontalDivider(
                                                color = BorderColor.copy(alpha = 0.15f),
                                                thickness = 0.5.dp,
                                                modifier = Modifier.padding(horizontal = 14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            activeView = ExportDialogView.MAIN
                            formatSearchQuery = ""
                        }
                    ) {
                        Text(
                            text = "Done",
                            color = BrandAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }

        ExportDialogView.MAIN -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkSurface,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileUpload,
                            contentDescription = null,
                            tint = BrandAccent,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "Export Artifact",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = SecondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Section 1: Format Selection Card
                        Column {
                            Text(
                                text = "Format",
                                color = PrimaryText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { activeView = ExportDialogView.SELECT_FORMAT },
                                shape = RoundedCornerShape(10.dp),
                                color = DarkSurfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(0.5.dp, BorderColor.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedFormat.extension.uppercase(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandAccent
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = when (selectedFormat) {
                                                ExportFormat.EPUB -> "Reflowable eBook"
                                                ExportFormat.PDF -> "Fixed Document"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SecondaryText
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Choose format",
                                        tint = SecondaryText.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Section 2: Summary Chapter Status Row
                        HorizontalDivider(
                            color = BorderColor.copy(alpha = 0.15f),
                            thickness = 0.5.dp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    totalCount == 0 -> "No chapters available"
                                    totalCount == 1 -> "1 chapter total"
                                    else -> "$totalCount chapters total"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (totalCount == 0) SecondaryText else PrimaryText,
                                fontWeight = FontWeight.Medium
                            )

                            if (totalCount > 0) {
                                val isComplete = downloadedCount == totalCount
                                Text(
                                    text = if (isComplete) "All downloaded" else "$downloadedCount/$totalCount downloaded",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isComplete) SuccessGreen else WarningAmber,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { onExport(selectedFormat) },
                        enabled = canExport
                    ) {
                        Text(
                            text = "Export",
                            color = if (canExport) BrandAccent else SecondaryText.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Cancel",
                            color = SecondaryText,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            )
        }
    }
}