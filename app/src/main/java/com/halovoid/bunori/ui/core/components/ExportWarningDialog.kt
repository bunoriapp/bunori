package com.halovoid.bunori.ui.core.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.SecondaryText

@Composable
fun ExportWarningDialog(
    totalSelected: Int,
    downloadedCount: Int,
    onDownloadFirst: () -> Unit,
    onExportAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chapters Missing", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "You are attempting to export $totalSelected chapters, but only $downloadedCount are currently downloaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "The export process can only include content that is already stored on your device. " +
                    "If you proceed now, the resulting file will be incomplete.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDownloadFirst,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandAccent,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Download Missing First")
                }
                OutlinedButton(
                    onClick = onExportAnyway,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export Anyway")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        },
        dismissButton = null
    )
}
