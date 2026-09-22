package com.halovoid.bunori.ui.feature.novel

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.net.toUri
import com.halovoid.bunori.domain.models.Artifact
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.ui.ViewModelFactory
import com.halovoid.bunori.ui.core.components.ExportWarningDialog
import com.halovoid.bunori.ui.core.platform.openFile
import com.halovoid.bunori.ui.core.platform.rememberFileExportLauncher
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.novel.components.artifact.ArtifactCard
import com.halovoid.bunori.ui.feature.novel.components.artifact.ArtifactExportDialog
import com.halovoid.bunori.ui.feature.novel.components.artifact.ExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ArtifactsDialogState {
    data object SelectFormat : ArtifactsDialogState
    data class ExportWarning(
        val format: ExportFormat,
        val totalSelected: Int,
        val downloadedCount: Int,
        val selectedSources: Set<String>,
        val missingChapters: List<Chapter>
    ) : ArtifactsDialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelArtifactsScreen(
    novel: Novel?,
    artifacts: List<Artifact>,
    onBack: () -> Unit,
    onDownload: (Artifact) -> Unit,
    viewModel: NovelViewModel? = null
) {
    val context = LocalContext.current
    val actualViewModel: NovelViewModel = viewModel ?: run {
        val factory = remember { ViewModelFactory(context.applicationContext as Application) }
        viewModel(factory = factory)
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(novel?.url) {
        novel?.url?.let { actualViewModel.loadNovel(it) }
    }

    val availableSources by actualViewModel.availableSources.collectAsStateWithLifecycle()
    val selectedSources by actualViewModel.selectedSources.collectAsStateWithLifecycle()
    val allChapters by actualViewModel.allNovelChapters.collectAsStateWithLifecycle()
    val chapters by actualViewModel.chapters.collectAsStateWithLifecycle()
    val chapterRange by actualViewModel.chapterRange.collectAsStateWithLifecycle()

    var selectedArtifact by remember { mutableStateOf<Artifact?>(null) }
    var activeDialog by remember { mutableStateOf<ArtifactsDialogState?>(null) }

    val launchFileExport = rememberFileExportLauncher(mimeType = "*/*") { destUri ->
        selectedArtifact?.let { artifact ->
            actualViewModel.copyArtifactToUri(
                artifact = artifact,
                destinationUri = destUri,
                onComplete = { resultUri ->
                    if (resultUri != null) {
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Exported: ${artifact.artifactName}",
                                actionLabel = "OPEN",
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                val mimeType = if (artifact.artifactName.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "application/epub+zip"
                                context.openFile(resultUri, mimeType)
                            }
                        }
                    }
                },
                onFileMissing = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Original file not found.",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artifacts", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { activeDialog = ArtifactsDialogState.SelectFormat }) {
                        Icon(Icons.Default.Add, contentDescription = "Create Artifact")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = PrimaryText,
                    navigationIconContentColor = PrimaryText,
                    actionIconContentColor = PrimaryText
                )
            )
        },
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (artifacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = SecondaryText.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No artifacts yet", style = MaterialTheme.typography.titleMedium, color = PrimaryText)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Generated EPUB files will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { activeDialog = ArtifactsDialogState.SelectFormat },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                    ) {
                        Text("Create Artifact")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(artifacts, key = { it.id }) { artifact ->
                    ArtifactCard(
                        artifact = artifact,
                        onOpen = {
                            actualViewModel.openArtifact(context, artifact) { docType ->
                                scope.launch {
                                    snackbarHostState.showSnackbar("No app found to open $docType")
                                }
                            }
                        },
                        onDownload = {
                            selectedArtifact = artifact
                            launchFileExport(artifact.artifactName)
                            onDownload(artifact)
                        }
                    )
                }
            }
        }

        when (val dialog = activeDialog) {
            is ArtifactsDialogState.SelectFormat -> {
                ArtifactExportDialog(
                    availableSources = availableSources,
                    initialSelectedSources = selectedSources,
                    allChapters = allChapters,
                    crawlerName = novel?.crawlerName ?: "",
                    onDismiss = { activeDialog = null },
                    onExport = { format, chosenSources ->
                        val matchingChapters = if (availableSources.isEmpty()) {
                            allChapters
                        } else {
                            allChapters.filter { ch ->
                                val eff = if (ch.scanlationSource.isBlank() || ch.scanlationSource == "NotProvided" || ch.scanlationSource == "Not Provided") {
                                    novel?.crawlerName ?: ""
                                } else {
                                    ch.scanlationSource
                                }
                                chosenSources.contains(ch.scanlationSource) || chosenSources.contains(eff)
                            }
                        }
                        val downloaded = matchingChapters.filter { it.isDownloaded }
                        val missing = matchingChapters.filter { !it.isDownloaded }

                        if (downloaded.isEmpty()) {
                            activeDialog = null
                            scope.launch {
                                snackbarHostState.showSnackbar("No downloaded chapters found for the selected sources. Please download them first.")
                            }
                        } else if (missing.isNotEmpty()) {
                            activeDialog = ArtifactsDialogState.ExportWarning(
                                format = format,
                                totalSelected = matchingChapters.size,
                                downloadedCount = downloaded.size,
                                selectedSources = chosenSources,
                                missingChapters = missing
                            )
                        } else {
                            activeDialog = null
                            novel?.let { actualViewModel.startBackgroundExport(it, format, chosenSources) }
                            onBack()
                        }
                    }
                )
            }
            is ArtifactsDialogState.ExportWarning -> {
                ExportWarningDialog(
                    totalSelected = dialog.totalSelected,
                    downloadedCount = dialog.downloadedCount,
                    onDownloadFirst = {
                        activeDialog = null
                        novel?.let { nov ->
                            actualViewModel.downloadChapters(nov, dialog.missingChapters)
                        }
                    },
                    onExportAnyway = {
                        activeDialog = null
                        novel?.let { actualViewModel.startBackgroundExport(it, dialog.format, dialog.selectedSources) }
                        onBack()
                    },
                    onDismiss = {
                        activeDialog = null
                    }
                )
            }
            null -> Unit
        }
    }
}
