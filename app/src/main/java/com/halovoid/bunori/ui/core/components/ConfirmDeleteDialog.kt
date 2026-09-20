package com.halovoid.bunori.ui.core.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.ui.core.theme.DarkSurface
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText

/**
 * Shared confirmation dialog for destructive delete actions.
 *
 * @param title Short title that identifies the action.
 * @param message What will be removed and whether the action is irreversible.
 * @param onConfirm Callback invoked only after explicit user confirmation.
 * @param onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = PrimaryText) },
        text = { Text(message, color = SecondaryText) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = Color.Red.copy(alpha = 0.85f))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PrimaryText)
            }
        }
    )
}
