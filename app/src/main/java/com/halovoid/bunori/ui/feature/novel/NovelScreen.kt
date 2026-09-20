package com.halovoid.bunori.ui.feature.novel

import android.app.Application
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.JobType
import com.halovoid.bunori.data.handlers.utility.parsedMetadata
import com.halovoid.bunori.ui.ViewModelFactory
import com.halovoid.bunori.ui.feature.reader.ReadingPlaylistHolder
import com.halovoid.bunori.ui.core.components.ConfirmDeleteDialog
import com.halovoid.bunori.ui.core.components.ContextualAction
import com.halovoid.bunori.ui.core.components.ContextualBottomBar
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.DarkBackground
import com.halovoid.bunori.ui.core.theme.DarkSurface
import com.halovoid.bunori.ui.core.theme.PrimaryAccent
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText
import com.halovoid.bunori.ui.feature.crawler.webview.WebViewActivity
import com.halovoid.bunori.ui.feature.activity.components.DownloadRangeDialog
import com.halovoid.bunori.ui.feature.novel.components.ChapterFilterSortSheet
import com.halovoid.bunori.ui.feature.novel.components.JumpToChapterBottomSheet
import com.halovoid.bunori.ui.feature.novel.components.NovelActionRow
import com.halovoid.bunori.ui.feature.novel.components.NovelHeroSection
import com.halovoid.bunori.ui.feature.novel.components.NovelMetadataTable
import com.halovoid.bunori.ui.feature.novel.components.NovelSynopsisSection
import com.halovoid.bunori.ui.feature.novel.components.NovelTopBar
import com.halovoid.bunori.ui.feature.novel.components.SourceFilterBottomSheet
import com.halovoid.bunori.ui.feature.novel.components.novelTableOfContents
import kotlinx.coroutines.launch

sealed interface NovelDialogState {
    data object ConfirmDelete : NovelDialogState
    data object DownloadRange : NovelDialogState
    data object FilterSheet : NovelDialogState
    data object SourceFilterSheet : NovelDialogState
    data object JumpToChapter : NovelDialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelScreen(
    novelUrl: String,
    onRequestClick: (String) -> Unit,
    onChapterClick: (String, Int) -> Unit,
    onArtifactsClick: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val factory = remember { ViewModelFactory(context.applicationContext as Application) }
    val viewModel: NovelViewModel = viewModel(factory = factory)
    
    val novel by viewModel.novel.collectAsState()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val downloadFilter by viewModel.downloadFilter.collectAsStateWithLifecycle()
    val sortState by viewModel.sortState.collectAsStateWithLifecycle()
    val chapterRange by viewModel.chapterRange.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedChapterIds by viewModel.selectedChapterIds.collectAsStateWithLifecycle()
    val selectedSources by viewModel.selectedSources.collectAsStateWithLifecycle()
    val availableSources by viewModel.availableSources.collectAsStateWithLifecycle()

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }
    val listState = rememberLazyListState()
    val density = LocalResources.current.displayMetrics.density
    val heroHeightPx = remember { (280 * density).toInt() }

    val isTopBarOpaque by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val showTitleInTopBar by remember {
        derivedStateOf {
            val firstItemIndex = listState.firstVisibleItemIndex
            val firstItemOffset = listState.firstVisibleItemScrollOffset
            firstItemIndex > 0 || (firstItemIndex == 0 && firstItemOffset > heroHeightPx)
        }
    }

    var descriptionExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    val requestHistory by viewModel.rootRequests.collectAsStateWithLifecycle()
    val chapterStatuses by viewModel.chapterStatuses.collectAsStateWithLifecycle()

    val ongoingStatuses = remember {
        setOf(
            JobStatus.PENDING,
            JobStatus.RUNNING,
            JobStatus.CANCELLING,
            JobStatus.PAUSED,
            JobStatus.BLOCKED
        )
    }

    val activeRequest = remember(requestHistory) {
        requestHistory.find { it.rstatus in ongoingStatuses }
    }

    var activeDialog by remember { mutableStateOf<NovelDialogState?>(null) }

    val isFilterActive = downloadFilter != DownloadFilter.ALL
    val isSourceFilterActive = availableSources.isNotEmpty() && selectedSources.size < availableSources.size
    val isSortModified = sortState.type != SortType.CHAPTER_NUMBER || sortState.order != SortOrder.ASCENDING

    LaunchedEffect(novelUrl) {
        viewModel.loadNovel(novelUrl)
    }

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ContextualBottomBar(
                visible = isSelectionMode,
                selectedCount = selectedChapterIds.size,
                actions = listOf(
                    ContextualAction(
                        title = if (selectedChapterIds.size == chapters.size) "Deselect" else "Select All",
                        icon = if (selectedChapterIds.size == chapters.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                        onClick = {
                            if (selectedChapterIds.size == chapters.size) {
                                viewModel.clearSelection()
                            } else {
                                viewModel.selectAllChapters(chapters)
                            }
                        }
                    ),
                    ContextualAction(
                        title = "Download",
                        icon = Icons.Default.Download,
                        onClick = {
                            viewModel.downloadSelectedChapters(novel ?: return@ContextualAction)
                        }
                    ),
                    ContextualAction(
                        title = "Mark Read",
                        icon = Icons.Default.DoneAll,
                        onClick = {
                            viewModel.markSelectedChaptersRead(true)
                        }
                    ),
                    ContextualAction(
                        title = "Mark Unread",
                        icon = Icons.Default.RemoveDone,
                        onClick = {
                            viewModel.markSelectedChaptersRead(false)
                        }
                    ),
                    ContextualAction(
                        title = "Delete",
                        icon = Icons.Default.Delete,
                        isDestructive = true,
                        onClick = {
                            viewModel.deleteSelectedChapters()
                        }
                    )
                )
            )
        }
    ) { innerPadding ->
        if (novel == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryAccent)
            }
        } else {
            val currentNovel = novel!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .background(DarkBackground)
            ) {
                val pullRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        coroutineScope.launch {
                            isRefreshing = true
                            viewModel.fetchNovelMetadata(currentNovel)
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    state = pullRefreshState,
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 48.dp),
                            containerColor = DarkSurface,
                            color = BrandAccent
                        )
                    }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        item {
                            NovelHeroSection(novel = currentNovel)
                        }

                        item {
                            NovelMetadataTable(
                                novel = currentNovel
                            )
                        }

                        item {
                            NovelActionRow(
                                inLibrary = currentNovel.inLibrary,
                                isActivityRunning = requestHistory.any { it.rstatus in ongoingStatuses },
                                artifactsExist = chapters.isNotEmpty(),
                                downloadEnabled = chapters.isNotEmpty(),
                                onFavoriteClick = { viewModel.toggleLibrary(currentNovel) },
                                onDownloadClick = { activeDialog = NovelDialogState.DownloadRange },
                                onArtifactsClick = onArtifactsClick,
                                onWebViewClick = {
                                    val intent = Intent(context, WebViewActivity::class.java).apply {
                                        putExtra("url", currentNovel.url)
                                        putExtra("host", currentNovel.url.toUri().host ?: "")
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }

                        item {
                            NovelSynopsisSection(
                                novel = currentNovel,
                                isExpanded = descriptionExpanded,
                                onExpandClick = { descriptionExpanded = !descriptionExpanded }
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${chapters.size} Chapters",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                            }
                        }

                        novelTableOfContents(
                            chapters = chapters,
                            chapterStatuses = chapterStatuses,
                            isSelectionMode = isSelectionMode,
                            selectedChapterIds = selectedChapterIds,
                            onFetchChapter = { viewModel.fetchChapter(currentNovel, it) },
                            onDeleteChapter = { viewModel.deleteChapter(it) },
                            onReplayChapter = { viewModel.replayChapter(currentNovel, it) },
                            onChapterClick = {
                                ReadingPlaylistHolder.setPlaylist(currentNovel.url, chapters.map { ch -> ch.id })
                                onChapterClick(currentNovel.url, it.id)
                            },
                            onChapterLongClick = { viewModel.selectChapter(it.id) },
                            onChapterToggleSelect = { viewModel.toggleChapterSelection(it.id) }
                        )
                    }
                }

                NovelTopBar(
                    novel = currentNovel,
                    isOpaque = isTopBarOpaque,
                    showTitle = showTitleInTopBar,
                    isActivityRunning = activeRequest != null,
                    onActivityClick = { activeRequest?.let { onRequestClick(it.id) } },
                    onBack = onBack,
                    onJumpToChapterClick = { activeDialog = NovelDialogState.JumpToChapter },
                    onFilterClick = { activeDialog = NovelDialogState.FilterSheet },
                    isFilterActive = isFilterActive || isSortModified,
                    onSourceFilterClick = { activeDialog = NovelDialogState.SourceFilterSheet },
                    isSourceFilterActive = isSourceFilterActive
                )
            }

            when (activeDialog) {
                is NovelDialogState.ConfirmDelete -> {
                    ConfirmDeleteDialog(
                        title = "Delete novel?",
                        message = "This will permanently remove \"${currentNovel.title}\" from your library and storage. This action cannot be undone.",
                        onConfirm = {
                            activeDialog = null
                            viewModel.deleteNovelPermanently(currentNovel)
                            onBack()
                        },
                        onDismiss = { activeDialog = null }
                    )
                }
                is NovelDialogState.DownloadRange -> {
                    val minIndex = chapters.minOfOrNull { it.index.toFloat() } ?: 1f
                    val maxIndex = chapters.maxOfOrNull { it.index.toFloat() } ?: currentNovel.chapters.size.toFloat().coerceAtLeast(1f)
                    val activeSources = if (selectedSources.isNotEmpty()) {
                        selectedSources.toList()
                    } else {
                        availableSources
                    }
                    DownloadRangeDialog(
                        initialRange = chapterRange,
                        minChapterIndex = minIndex,
                        maxChapterIndex = maxIndex,
                        sources = activeSources,
                        onConfirm = { range ->
                            viewModel.updateChapterRange(range)
                            viewModel.fetchRange(currentNovel)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null }
                    )
                }
                is NovelDialogState.FilterSheet -> {
                    ChapterFilterSortSheet(
                        downloadFilter = downloadFilter,
                        sortState = sortState,
                        onSetFilter = { viewModel.setDownloadFilter(it) },
                        onToggleAlphabetical = { viewModel.toggleAlphabeticalSort() },
                        onToggleChapterNumber = { viewModel.toggleChapterNumberSort() },
                        onDismiss = { activeDialog = null }
                    )
                }

                is NovelDialogState.SourceFilterSheet -> {
                    SourceFilterBottomSheet(
                        availableSources = availableSources,
                        selectedSources = selectedSources,
                        onToggleSource = { viewModel.toggleSourceSelection(it) },
                        onDismiss = { activeDialog = null }
                    )
                }
                is NovelDialogState.JumpToChapter -> {
                    JumpToChapterBottomSheet(
                        chapters = chapters,
                        onChapterClick = { chapter ->
                            ReadingPlaylistHolder.setPlaylist(currentNovel.url, chapters.map { ch -> ch.id })
                            onChapterClick(currentNovel.url, chapter.id)
                        },
                        onScrollToChapter = { chapter ->
                            val headerItemCount = 6
                            val chapterIndexInList = chapters.indexOf(chapter)
                            if (chapterIndexInList >= 0) {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(headerItemCount + chapterIndexInList)
                                }
                            }
                        },
                        onDismiss = { activeDialog = null }
                    )
                }
                null -> Unit
            }
        }
    }
}
