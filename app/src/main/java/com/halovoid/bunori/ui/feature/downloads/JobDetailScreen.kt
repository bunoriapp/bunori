package com.halovoid.bunori.ui.feature.downloads

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
import com.halovoid.bunori.ui.feature.downloads.components.JobCard
import com.halovoid.bunori.ui.feature.downloads.components.StatusIndicator
import com.halovoid.bunori.ui.feature.novel.components.artifact.ArtifactCard
import kotlinx.coroutines.launch

@Composable
fun JobDetailScreen(
    batchId: String?,
    onBackClick: () -> Unit,
) {
    if (batchId == null) {
        onBackClick()
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val factory = remember { ViewModelFactory(context.applicationContext as android.app.Application) }
    val viewModel: JobDetailViewModel = viewModel(factory = factory)

    val record by viewModel.getRequest(batchId).collectAsState(initial = null)
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

    LaunchedEffect(batchId) {
        viewModel.setRequestId(batchId)
    }

    val isCancelling = record != null && cancellingRequestIds.contains(record!!.id)
    val isActionPending = record != null && activeActionIds.contains(record!!.id)

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Batch",
                onBack = onBackClick,
                actions = {
                    val current = record
                    if (current != null) {
                        if (isCancelling || isActionPending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryText
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        } else {
                            when (current.status) {
                                JobStatus.RUNNING -> {
                                    IconButton(onClick = { viewModel.pauseRequest(current.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Pause,
                                            contentDescription = "Pause",
                                            tint = PrimaryText
                                        )
                                    }
                                    IconButton(onClick = { showCancelDialog = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel",
                                            tint = PrimaryText
                                        )
                                    }
                                }
                                JobStatus.PAUSED -> {
                                    IconButton(onClick = { viewModel.resumeRequest(current.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Resume",
                                            tint = PrimaryText
                                        )
                                    }
                                    IconButton(onClick = { showCancelDialog = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel",
                                            tint = PrimaryText
                                        )
                                    }
                                }
                                JobStatus.PENDING -> {
                                    IconButton(onClick = { showCancelDialog = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel",
                                            tint = PrimaryText
                                        )
                                    }
                                }
                                JobStatus.BLOCKED -> {
                                    IconButton(onClick = { securityDialogBatch = current }) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = "Resolve Security Check",
                                            tint = BrandAccent
                                        )
                                    }
                                    IconButton(onClick = { showCancelDialog = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel",
                                            tint = PrimaryText
                                        )
                                    }
                                }
                                JobStatus.SUCCESS, JobStatus.FAILED, JobStatus.CANCELLED -> {
                                    IconButton(onClick = { viewModel.replayRequest(current.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Replay",
                                            tint = PrimaryText
                                        )
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (record == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryAccent)
            }
        } else {
            val currentRecord = record!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    JobCard(
                        batch = currentRecord,
                        onClick = null,
                        allowAction = false,
                        isCancelling = isCancelling,
                        isActionPending = isActionPending
                    )
                }

                if (currentRecord.type == JobType.CHAPTER && chapterMetadata != null) {
                    item {
                        Column {
                            Text(
                                "Chapter Metadata",
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryText,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            MetadataTable(
                                mapOf(
                                    "Title" to chapterMetadata!!.title,
                                    "Serial" to chapterMetadata!!.index.toString(),
                                    "URL" to chapterMetadata!!.url,
                                    "Novel URL" to chapterMetadata!!.novelUrl
                                )
                            )
                        }
                    }
                }

                if (currentRecord.type == JobType.ARTIFACT && artifactMetadata != null) {
                    item {
                        ArtifactCard(
                            artifact = artifactMetadata!!,
                            onOpen = {
                                val mimeType = if (it.artifactName.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "application/epub+zip"
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(it.artifactDestination.toUri(), mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(intent, "Open with"))
                                } catch (e: Exception) {
                                    scope.launch {
                                        val docType = if (it.artifactName.endsWith(".pdf", ignoreCase = true)) "PDF" else "EPUB"
                                        snackbarHostState.showSnackbar("No app found to open $docType")
                                    }
                                }
                            },
                            onDownload = {
                                launchFileExport(it.artifactName)
                            }
                        )
                    }
                }

                if (linkedRequests.isNotEmpty()) {
                    item {
                        Text(
                            text = "Tasks (${linkedRequests.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    }

                    items(linkedRequests, key = { it.id }) { task ->
                        TaskDetailItem(task = task)
                    }
                }
            }
        }
    }
}

@Composable
fun TaskDetailItem(
    task: Batch,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!task.error.isNullOrBlank() && task.status != JobStatus.SUCCESS) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.error,
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRed.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            StatusIndicator(status = task.status)
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = BorderColor.copy(alpha = 0.25f)
        )
    }
}

@Composable
fun MetadataTable(data: Map<String, String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .padding(8.dp)
    ) {
        data.forEach { (key, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = key, color = SecondaryText, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(text = value, color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(2f))
            }
            if (key != data.keys.last()) {
                HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
            }
        }
    }
}