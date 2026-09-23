package com.halovoid.bunori.ui.feature.activity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.JobType
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.ui.ViewModelFactory
import com.halovoid.bunori.ui.core.components.ConfirmCancelDialog
import com.halovoid.bunori.ui.core.components.ContextualAction
import com.halovoid.bunori.ui.core.components.ContextualBottomBar
import com.halovoid.bunori.ui.core.components.SecurityCheckDialog
import com.halovoid.bunori.ui.core.platform.openFile
import com.halovoid.bunori.ui.core.platform.rememberFileExportLauncher
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.activity.components.JobCard
import com.halovoid.bunori.ui.feature.activity.components.JobDetailTopBar
import com.halovoid.bunori.ui.feature.activity.components.MetadataTable
import com.halovoid.bunori.ui.feature.activity.components.StatusFilterBottomSheet
import com.halovoid.bunori.ui.feature.activity.components.TaskDetailItem
import com.halovoid.bunori.ui.feature.novel.components.artifact.ArtifactCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
sealed interface JobDetailDialogState {
    data class SecurityCheck(val batch: Batch) : JobDetailDialogState
    data class CancelBatch(val batch: Batch) : JobDetailDialogState
    data class CancelSelectedTasks(val batchId: String, val count: Int) : JobDetailDialogState
    data object StatusFilterSheet : JobDetailDialogState
}

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

    val record by viewModel.getBatch(batchId).collectAsState(initial = null)
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedTaskIds by viewModel.selectedTaskIds.collectAsStateWithLifecycle()
    val cancellingBatchIds by viewModel.cancellingBatchIds.collectAsStateWithLifecycle()
    val activeActionIds by viewModel.activeActionIds.collectAsStateWithLifecycle()
    val chapterMetadata by viewModel.chapterMetadata.collectAsState()
    val artifactMetadata by viewModel.artifactMetadata.collectAsState()

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    var activeDialog by remember { mutableStateOf<JobDetailDialogState?>(null) }

    val selectedTasks = remember(tasks, selectedTaskIds) {
        tasks.filter { it.id in selectedTaskIds }
    }
    val hasRunningOrCompleted = selectedTasks.any { it.status == JobStatus.RUNNING || it.status == JobStatus.SUCCESS }
    val canCancel = selectedTasks.isNotEmpty() && !hasRunningOrCompleted
    val canReplay = selectedTasks.isNotEmpty() && selectedTasks.none { it.status == JobStatus.RUNNING }
    val distinctStatuses = remember(tasks) {
        tasks.map { it.status }.distinct()
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
                                context.openFile(resultUri, mimeType)
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
        viewModel.setBatchId(batchId)
    }

    val isCancelling = record != null && cancellingBatchIds.contains(record!!.id)
    val isActionPending = record != null && activeActionIds.contains(record!!.id)

    when (val dialog = activeDialog) {
        is JobDetailDialogState.SecurityCheck -> {
            SecurityCheckDialog(
                novelName = dialog.batch.name,
                onConfirm = {
                    val req = dialog.batch
                    activeDialog = null
                    viewModel.resolveWebView(req.id, req.novelUrl)
                },
                onDismiss = { activeDialog = null }
            )
        }
        is JobDetailDialogState.CancelBatch -> {
            ConfirmCancelDialog(
                title = "Cancel Batch?",
                message = "Are you sure you want to stop \"${dialog.batch.name}\"? Any progress made will be preserved, but remaining tasks will stop.",
                onConfirm = {
                    activeDialog = null
                    viewModel.cancelBatch(dialog.batch.id)
                },
                onDismiss = { activeDialog = null }
            )
        }
        is JobDetailDialogState.CancelSelectedTasks -> {
            val count = dialog.count
            ConfirmCancelDialog(
                title = if (count > 1) "Cancel $count Tasks?" else "Cancel Task?",
                message = "Are you sure you want to cancel the selected ${if (count > 1) "$count tasks" else "task"}? Progress made will be preserved, but remaining items will stop.",
                onConfirm = {
                    activeDialog = null
                    viewModel.cancelSelectedTasks(dialog.batchId)
                },
                onDismiss = { activeDialog = null }
            )
        }
        is JobDetailDialogState.StatusFilterSheet -> {
            StatusFilterBottomSheet(
                distinctStatuses = distinctStatuses,
                tasks = tasks,
                onStatusSelected = { status ->
                    viewModel.selectTasksByStatus(status, tasks)
                },
                onDismiss = { activeDialog = null }
            )
        }
        null -> Unit
    }

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            JobDetailTopBar(
                record = record,
                isCancelling = isCancelling,
                isActionPending = isActionPending,
                onBackClick = onBackClick,
                onPauseBatch = { viewModel.pauseBatch(it) },
                onResumeBatch = { viewModel.resumeBatch(it) },
                onReplayBatch = { viewModel.replayBatch(it) },
                onRequestCancelBatch = {
                    record?.let { activeDialog = JobDetailDialogState.CancelBatch(it) }
                },
                onResolveSecurityCheck = {
                    activeDialog = JobDetailDialogState.SecurityCheck(it)
                }
            )
        },
        bottomBar = {
            ContextualBottomBar(
                visible = isSelectionMode,
                selectedCount = selectedTaskIds.size,
                actions = listOf(
                    ContextualAction(
                        title = if (selectedTaskIds.size == tasks.size) "Deselect" else "Select All",
                        icon = if (selectedTaskIds.size == tasks.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                        onClick = {
                            if (selectedTaskIds.size == tasks.size) {
                                viewModel.clearSelection()
                            } else {
                                viewModel.selectAllTasks(tasks)
                            }
                        }
                    ),
                    ContextualAction(
                        title = "Filter",
                        icon = Icons.Default.FilterList,
                        onClick = { activeDialog = JobDetailDialogState.StatusFilterSheet }
                    ),
                    ContextualAction(
                        title = "Replay",
                        icon = Icons.Default.Refresh,
                        enabled = canReplay,
                        onClick = { record?.let { viewModel.replaySelectedTasks(it.id) } }
                    ),
                    ContextualAction(
                        title = "Cancel",
                        icon = Icons.Default.Cancel,
                        isDestructive = true,
                        enabled = canCancel,
                        onClick = {
                            record?.let {
                                activeDialog = JobDetailDialogState.CancelSelectedTasks(it.id, selectedTaskIds.size)
                            }
                        }
                    )
                )
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
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        JobCard(
                            batch = currentRecord,
                            onClick = null,
                            allowAction = false,
                            isCancelling = isCancelling,
                        )
                    }
                }

                if (currentRecord.type == JobType.CHAPTER && chapterMetadata != null) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                "Chapter Metadata",
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryText,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            MetadataTable(
                                data = mapOf(
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
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            ArtifactCard(
                                artifact = artifactMetadata!!,
                                onOpen = {
                                    viewModel.openArtifact(context, it) { docType ->
                                        scope.launch {
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
                }

                if (tasks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Tasks (${tasks.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }

                    items(tasks, key = { it.id }) { task ->
                        val isSelected = selectedTaskIds.contains(task.id)
                        TaskDetailItem(
                            task = task,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleTaskSelection(task.id)
                                }
                            },
                            onLongClick = {
                                viewModel.selectTask(task.id)
                            }
                        )
                        HorizontalDivider(
                            color = BorderColor.copy(alpha = 0.4f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        }
    }
}
