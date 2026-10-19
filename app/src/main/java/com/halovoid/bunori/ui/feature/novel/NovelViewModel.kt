package com.halovoid.bunori.ui.feature.novel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.handlers.utility.parsedMetadata
import com.halovoid.bunori.data.factory.JobFactory
import com.halovoid.bunori.data.repository.ArtifactRepository
import com.halovoid.bunori.data.repository.BatchRepository
import com.halovoid.bunori.data.repository.ChapterRepository
import com.halovoid.bunori.data.repository.DownloadRepositoryImpl
import com.halovoid.bunori.data.repository.NovelRepository
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.repository.StorageRepositoryImpl
import com.halovoid.bunori.data.scheduler.services.SchedulerService
import com.halovoid.bunori.domain.models.Artifact
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.ui.core.platform.openFile
import com.halovoid.bunori.ui.feature.novel.components.artifact.ExportFormat
import com.halovoid.bunori.utils.SimhashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DownloadFilter {
    ALL, DOWNLOADED, NOT_DOWNLOADED, NONE
}

enum class SortType {
    CHAPTER_NUMBER, ALPHABETICAL
}

enum class SortOrder {
    ASCENDING, DESCENDING
}

data class ChapterSortState(
    val type: SortType = SortType.CHAPTER_NUMBER,
    val order: SortOrder = SortOrder.ASCENDING
)

data class ChapterActiveStatuses(
    val byId: Map<Int, JobStatus> = emptyMap(),
    val byUrl: Map<String, JobStatus> = emptyMap()
) {
    fun getStatus(chapterId: Int, url: String, sourceUrl: String?): JobStatus? {
        return byId[chapterId]
            ?: byUrl[url]
            ?: (sourceUrl?.takeIf { it.isNotBlank() }?.let { byUrl[it] })
    }
}

class NovelViewModel(
    application: Application,
    private val batchRepository: BatchRepository,
    private val jobFactory: JobFactory = JobFactory(),
    private val preferenceRepository: PreferenceRepository = PreferenceRepository.getInstance(application)
) : AndroidViewModel(application)
{
    private val novelRepository = NovelRepository.getInstance(application)
    private val artifactRepository = ArtifactRepository.getInstance(application)
    private val chapterRepository = ChapterRepository.getInstance(application)
    private val downloadRepository = DownloadRepositoryImpl.getInstance(application)
    private val storageRepository = StorageRepositoryImpl.getInstance(application)

    private val _novelUrl = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val novel: StateFlow<Novel?> = _novelUrl
        .filterNotNull()
        .flatMapLatest { url ->
            combine(
                novelRepository.getNovelByUrlFlow(url),
                chapterRepository.getChaptersFlow(url)
            ) { details, chapters ->
                if (details != null) {
                    details.copy(chapters = chapters)
                } else {
                    val downloads = downloadRepository.getDownloadsForNovel(url)
                    if (downloads.isNotEmpty()) {
                        val first = downloads.first()
                        Novel(
                            url = url,
                            title = first.novelTitle.ifBlank { "Saved Novel" },
                            author = "Saved",
                            crawlerName = first.scanlationSource.takeIf { it.isNotBlank() && it != "Not Provided" } ?: "Saved",
                            inLibrary = false,
                            chapters = emptyList()
                        )
                    } else {
                        null
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    companion object {
        const val SOURCE_BOOKMARKS = "Bookmarks"
    }

    private val _chapterRange = MutableStateFlow(1f..1f)
    val chapterRange: StateFlow<ClosedFloatingPointRange<Float>> = _chapterRange.asStateFlow()

    private val _downloadFilter = MutableStateFlow(DownloadFilter.ALL)
    val downloadFilter: StateFlow<DownloadFilter> = _downloadFilter.asStateFlow()

    private val _selectedSource = MutableStateFlow<String?>(null)
    val selectedSource: StateFlow<String?> = _selectedSource.asStateFlow()

    private val _sortState = MutableStateFlow(ChapterSortState())
    val sortState: StateFlow<ChapterSortState> = _sortState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val batches: StateFlow<List<Batch>> = novel
        .filterNotNull()
        .flatMapLatest { nov ->
            batchRepository.getBatchesByNovelFlow(nov.url)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val allNovelChapters: StateFlow<List<Chapter>> = novel
        .filterNotNull()
        .flatMapLatest { nov ->
            combine(
                chapterRepository.getChaptersFlow(nov.url),
                downloadRepository.getDownloadedChapterUrlsFlow(nov.url),
                downloadRepository.getDownloadsForNovelFlow(nov.url)
            ) { rawChapters, downloadedUrls, downloads ->
                val downloadedSet = downloadedUrls.toSet()
                if (rawChapters.isNotEmpty()) {
                    rawChapters.map { chapter ->
                        val effSource = if (chapter.scanlationSource.isBlank() || chapter.scanlationSource == "NotProvided" || chapter.scanlationSource == "Not Provided") {
                            nov.crawlerName
                        } else {
                            chapter.scanlationSource
                        }
                        chapter.copy(isDownloaded = downloadedSet.contains(chapter.url)).apply {
                            sourceUrl = chapter.sourceUrl
                            scanlationSource = effSource
                            read = chapter.read
                            bookmarked = chapter.bookmarked
                        }
                    }
                } else {
                    downloads.mapIndexed { idx, dl ->
                        Chapter(
                            id = if (dl.id > 0) dl.id.toInt() else (idx + 1),
                            url = dl.chapterUrl,
                            title = dl.chapterTitle.ifBlank { "Chapter ${dl.chapterIndex}" },
                            index = dl.chapterIndex,
                            novelUrl = dl.novelUrl,
                            isDownloaded = true,
                            read = false,
                            scanlationSource = dl.scanlationSource
                        )
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val availableSources: StateFlow<List<String>> = allNovelChapters
        .map { list ->
            val sources = list.map { it.scanlationSource }.filter { it.isNotBlank() }.distinct()
            val hasBookmark = list.any { it.bookmarked }

            if (hasBookmark) {
                listOf(SOURCE_BOOKMARKS) + sources
            } else {
                sources
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val chapters: StateFlow<List<Chapter>> = combine(
        allNovelChapters,
        _downloadFilter,
        _selectedSource,
        _sortState
    ) { rawChapters, filter, activeSource, sort ->
        val sources = rawChapters.map { it.scanlationSource }.filter { it.isNotBlank() }.distinct()
        val hasBookmark = rawChapters.any { it.bookmarked }

        val effSource = when {
            activeSource == SOURCE_BOOKMARKS && !hasBookmark -> sources.firstOrNull()
            activeSource != null && (activeSource == SOURCE_BOOKMARKS || sources.contains(activeSource)) -> activeSource
            else -> sources.firstOrNull()
        }

        val bySource = if (effSource == SOURCE_BOOKMARKS) {
            rawChapters.filter { it.bookmarked }
        } else if (effSource != null) {
            rawChapters.filter { it.scanlationSource == effSource }
        } else {
            rawChapters
        }

        val byDownload = when (filter) {
            DownloadFilter.ALL -> bySource
            DownloadFilter.DOWNLOADED -> bySource.filter { it.isDownloaded }
            DownloadFilter.NOT_DOWNLOADED -> bySource.filter { !it.isDownloaded }
            DownloadFilter.NONE -> emptyList()
        }

        when (sort.type) {
            SortType.CHAPTER_NUMBER -> {
                if (sort.order == SortOrder.ASCENDING) {
                    byDownload.sortedWith(compareBy({ it.index }, { it.id }))
                } else {
                    byDownload.sortedWith(compareByDescending<Chapter> { it.index }.thenByDescending { it.id })
                }
            }
            SortType.ALPHABETICAL -> {
                if (sort.order == SortOrder.ASCENDING) byDownload.sortedBy { it.title }
                else byDownload.sortedByDescending { it.title }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val artifacts: StateFlow<List<Artifact>> = novel
        .filterNotNull()
        .flatMapLatest { nov ->
            artifactRepository.getArtifactsByNovelFlow(nov.url)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val chapterStatuses: StateFlow<ChapterActiveStatuses> = novel
        .filterNotNull()
        .flatMapLatest { nov ->
            batchRepository.getActiveTasksByNovelFlow(nov.url).map { tasks ->
                val idMap = mutableMapOf<Int, JobStatus>()
                val urlMap = mutableMapOf<String, JobStatus>()
                tasks.forEach { task ->
                    val meta = task.parsedMetadata
                    meta.chapterId?.let { id ->
                        idMap[id] = task.status
                    }
                    task.url?.let { url ->
                        if (url.isNotBlank()) urlMap[url] = task.status
                    }
                }
                ChapterActiveStatuses(idMap, urlMap)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ChapterActiveStatuses()
        )

    init {
        viewModelScope.launch {
            val defaultFilterStr = preferenceRepository.defaultChapterDownloadFilter.firstOrNull()
            if (defaultFilterStr != null) {
                _downloadFilter.value = runCatching { DownloadFilter.valueOf(defaultFilterStr) }.getOrDefault(DownloadFilter.ALL)
            }

            val defaultSortTypeStr = preferenceRepository.defaultChapterSortType.firstOrNull()
            val defaultSortOrderStr = preferenceRepository.defaultChapterSortOrder.firstOrNull()
            if (defaultSortTypeStr != null || defaultSortOrderStr != null) {
                val sortType = runCatching { SortType.valueOf(defaultSortTypeStr ?: "") }.getOrDefault(SortType.CHAPTER_NUMBER)
                val sortOrder = runCatching { SortOrder.valueOf(defaultSortOrderStr ?: "") }.getOrDefault(SortOrder.ASCENDING)
                _sortState.value = ChapterSortState(type = sortType, order = sortOrder)
            }
        }

        viewModelScope.launch {
            novel.collect { currentNovel ->
                if (currentNovel != null) {
                    if (currentNovel.chapters.isNotEmpty()) {
                        val currentRange = _chapterRange.value
                        val maxIdx = currentNovel.chapters.maxOfOrNull { it.index.toFloat() } ?: currentNovel.chapters.size.toFloat()
                        if (currentRange.start == 1f && currentRange.endInclusive == 1f) {
                            _chapterRange.value = 1f..maxIdx
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            _selectedSource.collect {
                val currentChapters = chapters.value
                if (currentChapters.isNotEmpty()) {
                    val minIndex = currentChapters.minOf { it.index }.toFloat()
                    val maxIndex = currentChapters.maxOf { it.index }.toFloat()
                    _chapterRange.value = minIndex..maxIndex
                } else {
                    _chapterRange.value = 1f..1f
                }
            }
        }

        viewModelScope.launch {
            chapters.collect { filteredChapters ->
                if (filteredChapters.isNotEmpty()) {
                    val minIndex = filteredChapters.minOf { it.index }.toFloat()
                    val maxIndex = filteredChapters.maxOf { it.index }.toFloat()
                    val currentRange = _chapterRange.value
                    if (currentRange.start < minIndex || currentRange.endInclusive > maxIndex || (currentRange.start == 1f && currentRange.endInclusive == 1f)) {
                        _chapterRange.value = minIndex..maxIndex
                    }
                }
            }
        }
    }
    fun toggleBookmark(chapter: Chapter) {
        viewModelScope.launch(Dispatchers.IO) {
            chapterRepository.toggleChapterBookmark(chapter.id)
        }
    }
    fun bookmarkSelectedChapters(bookmarked: Boolean) {
        val ids = _selectedChapterIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            chapterRepository.updateChaptersBookmarkStatus(ids, bookmarked)
            clearSelection()
        }
    }

    fun nextSource() {
        val sources = availableSources.value
        if (sources.size <= 1) return
        val current = _selectedSource.value ?: sources.firstOrNull { it != SOURCE_BOOKMARKS } ?: sources.first()
        val currIdx = sources.indexOf(current).let { if (it == -1) 0 else it }
        val nextIdx = (currIdx + 1) % sources.size
        _selectedSource.value = sources[nextIdx]
        _chapterRange.value = 1f..1f
    }

    fun prevSource() {
        val sources = availableSources.value
        if (sources.size <= 1) return
        val current = _selectedSource.value ?: sources.firstOrNull { it != SOURCE_BOOKMARKS } ?: sources.first()
        val currIdx = sources.indexOf(current).let { if (it == -1) 0 else it }
        val prevIdx = (currIdx - 1 + sources.size) % sources.size
        _selectedSource.value = sources[prevIdx]
        _chapterRange.value = 1f..1f
    }

    fun loadNovel(novelUrl: String) {
        clearSelection()
        _selectedSource.value = null
        _chapterRange.value = 1f..1f
        _novelUrl.value = novelUrl
    }

    fun toggleLibrary(novel: Novel, onSimilarFound: ((List<Novel>) -> Unit)? = null) {
        if (novel.inLibrary) {
            viewModelScope.launch(Dispatchers.IO) {
                novelRepository.toggleLibrary(novel.url, false)
            }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val hash = novel.titleHash ?: SimhashUtils.generateSimhash(novel.title)
                val similar = novelRepository.getSimilarNovels(hash, 3)
                if (similar.isNotEmpty() && onSimilarFound != null) {
                    withContext(Dispatchers.Main) {
                        onSimilarFound(similar)
                    }
                } else {
                    novelRepository.toggleLibrary(novel.url, true)
                }
            }
        }
    }

    fun forceAddToLibrary(novel: Novel) {
        viewModelScope.launch(Dispatchers.IO) {
            novelRepository.toggleLibrary(novel.url, true)
        }
    }

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedChapterIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedChapterIds: StateFlow<Set<Int>> = _selectedChapterIds.asStateFlow()

    fun toggleChapterSelection(chapterId: Int) {
        val current = _selectedChapterIds.value
        val updated = if (current.contains(chapterId)) current - chapterId else current + chapterId
        _selectedChapterIds.value = updated
        _isSelectionMode.value = updated.isNotEmpty()
    }

    fun selectChapter(chapterId: Int) {
        _isSelectionMode.value = true
        _selectedChapterIds.value += chapterId
    }

    fun clearSelection() {
        _selectedChapterIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun selectAllChapters(chapters: List<Chapter>) {
        _selectedChapterIds.value = chapters.map { it.id }.toSet()
        _isSelectionMode.value = true
    }

    fun markSelectedChaptersRead(isRead: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = _selectedChapterIds.value.toList()
            if (ids.isNotEmpty()) {
                chapterRepository.updateChaptersReadStatus(ids, isRead)
            }
            clearSelection()
        }
    }

    fun downloadSelectedChapters(novel: Novel) {
        val selectedIds = _selectedChapterIds.value
        val toDownload = chapters.value.filter { selectedIds.contains(it.id) }
        if (toDownload.isNotEmpty()) {
            downloadChapters(novel, toDownload)
        }
        clearSelection()
    }

    fun deleteSelectedChapters() {
        viewModelScope.launch(Dispatchers.IO) {
            val selectedIds = _selectedChapterIds.value
            val toDelete = chapters.value.filter { selectedIds.contains(it.id) }
            toDelete.forEach { chapter ->
                val download = downloadRepository.getDownload(chapter.novelUrl, chapter.url)
                if (download != null) {
                    downloadRepository.deleteDownload(chapter.novelUrl, chapter.url)
                }
            }
            clearSelection()
        }
    }

    fun updateChapterRange(range: ClosedFloatingPointRange<Float>) {
        _chapterRange.value = range
    }

    fun startBackgroundExport(
        novel: Novel,
        format: ExportFormat,
        selectedSources: Set<String>,
        start: Int,
        end: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val batch = jobFactory.createExportBatch(novel, format, start, end, selectedSources)
            val task = jobFactory.createExportTask(batch.id, novel, format, start, end, selectedSources)

            batchRepository.insertBatch(batch)
            batchRepository.insertTask(task)
            SchedulerService.startService(getApplication())
        }
    }

    fun downloadChapters(novel: Novel, chaptersToDownload: List<Chapter>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (chaptersToDownload.isEmpty()) return@launch
            val start = chaptersToDownload.minOf { it.index }
            val end = chaptersToDownload.maxOf { it.index }
            val batch = jobFactory.createRangeDownloadBatch(novel, start, end)
            val tasks = jobFactory.createChapterTasks(batch.id, novel.crawlerName, chaptersToDownload, batch.priority)
            batchRepository.insertBatch(batch)
            batchRepository.insertTasks(tasks)
            SchedulerService.startService(getApplication())
        }
    }

    fun fetchNovelMetadata(novel: Novel) {
        viewModelScope.launch(Dispatchers.IO) {
            val batch = jobFactory.createMetadataBatch(novel)
            val task = jobFactory.createMetadataTask(batch.id, novel)

            batchRepository.insertBatch(batch)
            batchRepository.insertTask(task)
            SchedulerService.startService(getApplication())
        }
    }

    fun fetchRange(novel: Novel) {
        val start = _chapterRange.value.start.toInt()
        val end = _chapterRange.value.endInclusive.toInt()
        val currentChapters = chapters.value
        val rangeChapters = currentChapters.filter { it.index in start..end }
        downloadChapters(novel, rangeChapters)
    }

    fun fetchChapter(novel: Novel, chapter: Chapter) {
        viewModelScope.launch(Dispatchers.IO) {
            val batch = jobFactory.createChapterBatch(novel, chapter)
            val tasks = jobFactory.createChapterTasks(batch.id, novel.crawlerName, listOf(chapter), batch.priority)
            batchRepository.insertBatch(batch)
            batchRepository.insertTasks(tasks)
            SchedulerService.startService(getApplication())
        }
    }

    fun deleteChapter(chapter: Chapter) {
        viewModelScope.launch(Dispatchers.IO) {
            val download = downloadRepository.getDownload(chapter.novelUrl, chapter.url)
            if (download != null) {
                downloadRepository.deleteDownload(chapter.novelUrl, chapter.url)
            }
        }
    }

    fun replayChapter(novel: Novel, chapter: Chapter) {
        viewModelScope.launch(Dispatchers.IO) {
            val download = downloadRepository.getDownload(chapter.novelUrl, chapter.url)
            if (download != null) {
                downloadRepository.deleteDownload(chapter.novelUrl, chapter.url)
            }
            val batch = jobFactory.createChapterBatch(novel, chapter)
            val tasks = jobFactory.createChapterTasks(batch.id, novel.crawlerName, listOf(chapter), batch.priority)
            batchRepository.insertBatch(batch)
            batchRepository.insertTasks(tasks)
        }
    }

    // copied from JobDetailViewModel
    fun copyArtifactToUri(
        artifact: Artifact,
        destinationUri: Uri,
        onComplete: (Uri?) -> Unit,
        onFileMissing: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val sourceUri = storageRepository.relativeToUri(artifact.artifactDestination)

            if (sourceUri == null || !storageRepository.exists(sourceUri)) {
                artifactRepository.removeArtifact(artifact)
                withContext(Dispatchers.Main) { onFileMissing() }
                return@launch
            }

            val success = try {
                val input = storageRepository.openInputStream(sourceUri)
                val output = getApplication<Application>().contentResolver.openOutputStream(destinationUri, "wt")

                if (input != null && output != null) {
                    input.use { inStream ->
                        output.use { outStream ->
                            inStream.copyTo(outStream, 65536)
                        }
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }

            withContext(Dispatchers.Main) {
                onComplete(if (success) destinationUri else null)
            }
        }
    }

    fun openArtifact(context: Context, artifact: Artifact, onNoAppFound: (String) -> Unit) {
        viewModelScope.launch {
            val resolvedUri = storageRepository.relativeToUri(artifact.artifactDestination)
                ?: runCatching { artifact.artifactDestination.toUri() }.getOrNull()
                ?: return@launch

            val isPdf = artifact.artifactName.endsWith(".pdf", ignoreCase = true)
            val mimeType = if (isPdf) "application/pdf" else "application/epub+zip"
            val docType = if (isPdf) "PDF" else "EPUB"

            context.openFile(resolvedUri, mimeType) {
                onNoAppFound(docType)
            }
        }
    }

    fun deleteNovelPermanently(novel: Novel) {
        viewModelScope.launch(Dispatchers.IO) {
            novelRepository.deleteNovel(novel)
        }
    }

    fun toggleAlphabeticalSort() {
        val current = _sortState.value
        _sortState.value = if (current.type != SortType.ALPHABETICAL) {
            ChapterSortState(SortType.ALPHABETICAL, SortOrder.ASCENDING)
        } else {
            when (current.order) {
                SortOrder.ASCENDING -> ChapterSortState(SortType.ALPHABETICAL, SortOrder.DESCENDING)
                SortOrder.DESCENDING -> ChapterSortState(SortType.CHAPTER_NUMBER, SortOrder.ASCENDING)
            }
        }
    }

    fun toggleChapterNumberSort() {
        val current = _sortState.value
        _sortState.value = if (current.type != SortType.CHAPTER_NUMBER) {
            ChapterSortState(SortType.CHAPTER_NUMBER, SortOrder.ASCENDING)
        } else {
            when (current.order) {
                SortOrder.ASCENDING -> ChapterSortState(SortType.CHAPTER_NUMBER, SortOrder.DESCENDING)
                SortOrder.DESCENDING -> ChapterSortState(SortType.CHAPTER_NUMBER, SortOrder.ASCENDING)
            }
        }
    }

    fun setDownloadFilter(filter: DownloadFilter) {
        _downloadFilter.value = filter
    }
}