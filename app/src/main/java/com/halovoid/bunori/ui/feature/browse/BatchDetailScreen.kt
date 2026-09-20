package com.halovoid.bunori.ui.feature.browse

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.JobType
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.ui.ViewModelFactory
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.components.ConfirmCancelDialog
import com.halovoid.bunori.ui.core.components.SecurityCheckDialog
import com.halovoid.bunori.ui.core.platform.rememberFileExportLauncher
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.browse.components.BatchCard
import com.halovoid.bunori.ui.feature.browse.components.StatusIndicator
import com.halovoid.bunori.ui.feature.novel.components.artifact.ArtifactCard
import kotlinx.coroutines.launch

@Composable
fun BatchDetailScreen(
    requestId: String?,
    onBackClick: () -> Unit,
    onGroupClick: (JobType) -> Unit,
    onRequestClick: (String) -> Unit
) {
    if (requestId == null) {
        onBackClick()
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val factory = remember { ViewModelFactory(context.applicationContext as android.app.Application) }
    val viewModel: BatchDetailViewModel = viewModel(factory = factory)

    LaunchedEffect(requestId) {
        viewModel.setRequestId(requestId)
    }

    val record by viewModel.getRequest(requestId).collectAsState(initial = null)
    val linkedRequests by viewModel.linkedRequests.collectAsStateWithLifecycle()
    val cancellingRequestIds by viewModel.cancellingRequestIds.collectAsStateWithLifecycle()
    val activeActionIds by viewModel.activeActionIds.collectAsStateWithLifecycle()
    val chapterMetadata by viewModel.chapterMetadata.collectAsState()
    val artifactMetadata by viewModel.artifactMetadata.collectAsState()

    var securityDialogBatch by remember { mutableStateOf<Batch?>(null) }
    var showCancelDialog by remember { mutableStateOf(false) }

    if (securityDialogBatch != null) {
        SecurityCheckDialog(
            novelName = securityDialogBatch!!.name,
            onConfirm = {
                val req = securityDialogBatch!!
                securityDialogBatch = null
                viewModel.resolveWebView(req.id, req.url ?: req.novelUrl)
            },
            onDismiss = { securityDialogBatch = null }
        )
    }

    if (showCancelDialog && record != null) {
        ConfirmCancelDialog(
            title = "Cancel Batch?",
            message = "Are you sure you want to stop \"${record!!.name}\"? Any progress made will be preserved, but remaining tasks will stop.",
            onConfirm = {
                showCancelDialog = false
                viewModel.cancelRequest(record!!.id)
            },
            onDismiss = { showCancelDialog = false }
        )
    }

    val launchFileExport = rememberFileExportLauncher(mimeType = "*/*") { uri ->
        if (artifactMetadata != null) {
            viewModel.copyArtifactToUri(
                artifact = artifactMetadata!!,
                destinationUri = uri,
                onComplete = { resultUri ->
                    if (resultUri != null) {
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Exported: ${artifactMetadata!!.artifactName}",
                                actionLabel = "OPEN",
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                val mimeType = if (artifactMetadata!!.artifactName.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "application/epub+zip"
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(resultUri, mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Open with"))
                            }
                        }
                    }
                },
                onFileMissing = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Original file not found. It may have been removed or deleted.",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            )
        }
    }

    val currentRecord = record

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = currentRecord?.name ?: "Batch Details",
                onBack = onBackClick
            )
        }
    ) { innerPadding ->
        if (currentRecord == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandAccent)
            }
        } else {
            val progress = if (currentRecord.progressTotal > 0) {
                currentRecord.progressSuccess.toFloat() / currentRecord.progressTotal
            } else 0f

            val isCancelling = cancellingRequestIds.contains(currentRecord.id)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Main Batch Progress Card
                item(key = "main_card") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = DarkSurfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = currentRecord.type.name,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = SecondaryText,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                StatusIndicator(currentRecord.status)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = currentRecord.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${currentRecord.progressSuccess}/${currentRecord.progressTotal} tasks",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SecondaryText
                                )
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PrimaryText,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = BrandAccent,
                                trackColor = DarkSurfaceVariant
                            )

                            if (!currentRecord.error.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = ErrorRed.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = currentRecord.error ?: "",
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ErrorRed
                                    )
                                }
                            }

                            // Control Actions Row
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = BorderColor.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isCancelling) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = ErrorRed
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Cancelling...", style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                                } else if (currentRecord.status == JobStatus.RUNNING || currentRecord.status == JobStatus.PENDING) {
                                    Button(
                                        onClick = { viewModel.pauseRequest(currentRecord.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pause")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = { showCancelDialog = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Cancel")
                                    }
                                } else if (currentRecord.status == JobStatus.PAUSED) {
                                    Button(
                                        onClick = { viewModel.resumeRequest(currentRecord.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Resume")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = { showCancelDialog = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Cancel")
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.replayRequest(currentRecord.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Replay")
                                    }
                                }
                            }
                        }
                    }
                }

                // Linked Artifact if applicable
                if (artifactMetadata != null) {
                    item(key = "artifact_section") {
                        Text(
                            text = "GENERATED ARTIFACT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SecondaryText,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ArtifactCard(
                            artifact = artifactMetadata!!,
                            onOpen = { launchFileExport(artifactMetadata!!.artifactName) },
                            onDownload = { launchFileExport(artifactMetadata!!.artifactName) }
                        )
                    }
                }

                // Sub-tasks / Sub-batches
                if (linkedRequests.isNotEmpty()) {
                    item(key = "subtasks_header") {
                        Text(
                            text = "SUB-TASKS (${linkedRequests.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SecondaryText,
                            letterSpacing = 1.sp
                        )
                    }

                    items(linkedRequests, key = { it.id }) { subBatch ->
                        BatchCard(
                            batch = subBatch,
                            onClick = { onRequestClick(subBatch.id) },
                            onReplay = { viewModel.replayRequest(subBatch.id) },
                            onCancel = { viewModel.cancelRequest(subBatch.id) },
                            onContinue = { viewModel.resumeRequest(subBatch.id) },
                            onSecurityClick = {
                                securityDialogBatch = subBatch
                            },
                            allowAction = true
                        )
                    }
                }
            }
        }
    }
}

// Alias for compatibility
@Composable
fun RequestDetailScreen(
    requestId: String?,
    onBackClick: () -> Unit,
    onGroupClick: (JobType) -> Unit,
    onRequestClick: (String) -> Unit
) = BatchDetailScreen(requestId, onBackClick, onGroupClick, onRequestClick)
