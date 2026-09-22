package com.halovoid.bunori.ui.core.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.ui.core.theme.DarkSurface
import com.halovoid.bunori.ui.core.theme.ErrorRed
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText

/**
 * Shared confirmation dialog for cancelling a request.
 *
 * @param title Short title that identifies the action.
 * @param message Explanation of what happens when the request is cancelled.
 * @param onConfirm Callback invoked only after explicit user confirmation.
 * @param onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun ConfirmCancelDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = PrimaryText) },
        text = {
            Column {
                Text(message, color = SecondaryText)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Confirm", color = ErrorRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Back", color = PrimaryText)
            }
        }
    )
}
