package com.halovoid.bunori.ui.feature.activity.components

import androidx.compose.runtime.*
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.ui.core.components.SecurityCheckDialog

@Composable
fun JobActionHandler(
    onResolveWebview: (String, String) -> Unit,
    content: @Composable (onSecurityClick: (Batch) -> Unit) -> Unit
) {
    var securityDialogBatch by remember { mutableStateOf<Batch?>(null) }

    if (securityDialogBatch != null) {
        SecurityCheckDialog(
            novelName = securityDialogBatch!!.name,
            onConfirm = {
                val req = securityDialogBatch!!
                securityDialogBatch = null
                onResolveWebview(req.id, req.url ?: req.novelUrl)
            },
            onDismiss = { securityDialogBatch = null }
        )
    }

    content { securityDialogBatch = it }
}
