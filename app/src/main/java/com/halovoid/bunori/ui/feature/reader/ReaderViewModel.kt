package com.halovoid.bunori.ui.feature.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.halovoid.bunori.data.repository.ChapterRepository
import com.halovoid.bunori.data.repository.DownloadRepository
import com.halovoid.bunori.data.repository.NovelRepository
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.repository.ReaderRepository
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.ChapterPayload
import com.halovoid.bunori.domain.models.CustomFont
import com.halovoid.bunori.domain.models.ReaderSettings
import com.halovoid.bunori.domain.models.ReaderTextAlign
import com.halovoid.bunori.domain.models.ReaderTheme
import com.halovoid.bunori.domain.models.ReadingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Commands sent from ViewModel to ReaderWebView runtime.
 */
sealed interface ReaderCommand {
    data class SetInitialChapter(
        val payload: ChapterPayload,
        val hasPrevious: Boolean,
        val hasNext: Boolean,
        val startAtEnd: Boolean = false
    ) : ReaderCommand

    data class AppendChapter(
        val payload: ChapterPayload,
        val hasNext: Boolean
    ) : ReaderCommand

    data class PrependChapter(
        val payload: ChapterPayload,
        val hasPrevious: Boolean
    ) : ReaderCommand

    data class SetHasMore(
        val hasPrevious: Boolean,
        val hasNext: Boolean
    ) : ReaderCommand

    data class ScrollToChapter(val chapterId: Int) : ReaderCommand

    data class PageTurn(val direction: Int) : ReaderCommand
}

class ReaderViewModel(
    application: Application,
    private val chapterRepository: ChapterRepository,
    private val novelRepository: NovelRepository,
    private val readerRepository: ReaderRepository,
    private val preferenceRepository: PreferenceRepository = PreferenceRepository.getInstance(application),
    private val downloadRepository: DownloadRepository = DownloadRepository.getInstance(application)
) : AndroidViewModel(application) {

    // --- Reader Settings & Custom Fonts Flow ---
    val readerSettings: StateFlow<ReaderSettings> = preferenceRepository.readerSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    val customFonts: StateFlow<List<CustomFont>> = preferenceRepository.customFonts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- State & Sequences ---
    private var allChapters: List<Chapter> = emptyList()
    private var chapterIndexById: Map<Int, Int> = emptyMap()
    private var crawlerName: String = ""
    private var currentNovelUrl: String = ""
    private var centerPos: Int = -1

    // Cache of raw HTML chapter content
    private val rawContentCache = mutableMapOf<Int, String>()

    // --- Web View Commands Flow ---
    private val _commands = MutableSharedFlow<ReaderCommand>(replay = 1, extraBufferCapacity = 16)
    val commands: SharedFlow<ReaderCommand> = _commands.asSharedFlow()

    // --- Observable UI States ---
    private val _currentChapter = MutableStateFlow<Chapter?>(null)
    val currentChapter: StateFlow<Chapter?> = _currentChapter.asStateFlow()

    private val _currentChapterNumber = MutableStateFlow(0)
    val currentChapterNumber: StateFlow<Int> = _currentChapterNumber.asStateFlow()

    private val _totalChapters = MutableStateFlow(0)
    val totalChapters: StateFlow<Int> = _totalChapters.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _readingProgress = MutableStateFlow(0f)
    val readingProgress: StateFlow<Float> = _readingProgress.asStateFlow()

    // --- Dynamic Content / Verification Required (Cloudflare / Next.js) ---
    private val _blockedChapter = MutableStateFlow<Chapter?>(null)
    val blockedChapter: StateFlow<Chapter?> = _blockedChapter.asStateFlow()

    private val _isBlockedOrEmpty = MutableStateFlow(false)
    val isBlockedOrEmpty: StateFlow<Boolean> = _isBlockedOrEmpty.asStateFlow()

    // --- Table of Contents ---
    private val _tocChapters = MutableStateFlow<List<Chapter>>(emptyList())
    val tocChapters: StateFlow<List<Chapter>> = _tocChapters.asStateFlow()

    private fun findChapterPosition(idOrIndex: Int): Int? {
        return chapterIndexById[idOrIndex]
            ?: allChapters.indexOfFirst { it.index == idOrIndex }.takeIf { it >= 0 }
    }

    /**
     * Initializes the reader.
     * Inherits the reading sequence from [playlistChapterIds] if provided,
     * or from [ReadingPlaylistHolder], respecting NovelScreen filters and sort order.
     */
    fun start(novelUrl: String, initialChapterId: Int, playlistChapterIds: List<Int>? = null) {
        if (currentNovelUrl == novelUrl && allChapters.isNotEmpty()) return
        currentNovelUrl = novelUrl

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _isBlockedOrEmpty.value = false
            _blockedChapter.value = null

            crawlerName = novelRepository.getNovelByUrl(novelUrl)?.crawlerName.orEmpty()
            var dbChapters = chapterRepository.getChaptersByNovelUrl(novelUrl)

            // Resilient fallback to downloads table when chapters table is empty (e.g., offline mode)
            if (dbChapters.isEmpty()) {
                val downloads = downloadRepository.getDownloadsForNovel(novelUrl)
                dbChapters = downloads.mapIndexed { idx, dl ->
                    Chapter(
                        id = if (dl.id > 0) dl.id.toInt() else (idx + 1),
                        novelUrl = dl.novelUrl,
                        url = dl.chapterUrl,
                        title = dl.chapterTitle.ifBlank { "Chapter ${dl.chapterIndex}" },
                        index = dl.chapterIndex,
                        scanlationSource = dl.scanlationSource,
                        read = false
                    )
                }
            }

            // Honor active playlist context (scanlation filter, downloaded filter, sort order)
            val playlist = playlistChapterIds ?: ReadingPlaylistHolder.getPlaylist(novelUrl)
            allChapters = if (!playlist.isNullOrEmpty()) {
                val chapterMap = dbChapters.associateBy { it.id }
                val indexMap = dbChapters.associateBy { it.index }
                playlist.mapNotNull { chapterMap[it] ?: indexMap[it] }
            } else {
                dbChapters.sortedBy { it.index }
            }

            if (allChapters.isEmpty()) {
                allChapters = dbChapters
            }

            chapterIndexById = allChapters.withIndex().associate { (i, c) -> c.id to i }
            _tocChapters.value = allChapters
            _totalChapters.value = allChapters.size

            val startPos = findChapterPosition(initialChapterId) ?: 0
            centerPos = startPos
            val initialChapter = allChapters.getOrNull(startPos)
            if (initialChapter != null) {
                _currentChapter.value = initialChapter
                _currentChapterNumber.value = startPos + 1

                val payload = fetchChapterPayload(initialChapter)
                checkBlockedOrEmpty(payload.htmlContent, initialChapter)

                val hasPrev = startPos > 0
                val hasNext = startPos < allChapters.size - 1
                _commands.emit(ReaderCommand.SetInitialChapter(payload, hasPrev, hasNext))
            }

            _isLoading.value = false
        }
    }

    /**
     * Fetches and emits the next chapter in the reading playlist.
     */
    fun loadNextChapter(afterChapterId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentPos = findChapterPosition(afterChapterId) ?: return@launch
                val nextPos = currentPos + 1
                if (nextPos !in allChapters.indices) {
                    _commands.emit(ReaderCommand.SetHasMore(hasPrevious = currentPos > 0, hasNext = false))
                    return@launch
                }

                val nextChapter = allChapters[nextPos]
                val payload = fetchChapterPayload(nextChapter)
                checkBlockedOrEmpty(payload.htmlContent, nextChapter)
                val hasNext = (nextPos + 1) in allChapters.indices
                _commands.emit(ReaderCommand.AppendChapter(payload, hasNext))
            } catch (e: Exception) {
                android.util.Log.e("ReaderViewModel", "Failed to load next chapter after $afterChapterId: ${e.message}", e)
                // Unblock JS sentinel so infinite scroll does not stay locked forever
                val currentPos = findChapterPosition(afterChapterId) ?: -1
                _commands.emit(ReaderCommand.SetHasMore(hasPrevious = currentPos > 0, hasNext = true))
            }
        }
    }

    /**
     * Fetches and emits the previous chapter in the reading playlist.
     */
    fun loadPreviousChapter(beforeChapterId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentPos = findChapterPosition(beforeChapterId) ?: return@launch
                val prevPos = currentPos - 1
                if (prevPos !in allChapters.indices) {
                    _commands.emit(ReaderCommand.SetHasMore(hasPrevious = false, hasNext = currentPos < allChapters.size - 1))
                    return@launch
                }

                val prevChapter = allChapters[prevPos]
                val payload = fetchChapterPayload(prevChapter)
                checkBlockedOrEmpty(payload.htmlContent, prevChapter)
                val hasPrev = (prevPos - 1) in allChapters.indices
                _commands.emit(ReaderCommand.PrependChapter(payload, hasPrev))
            } catch (e: Exception) {
                android.util.Log.e("ReaderViewModel", "Failed to load previous chapter before $beforeChapterId: ${e.message}", e)
                // Unblock JS sentinel so infinite scroll does not stay locked forever
                val currentPos = findChapterPosition(beforeChapterId) ?: -1
                _commands.emit(ReaderCommand.SetHasMore(hasPrevious = true, hasNext = currentPos < allChapters.size - 1))
            }
        }
    }

    /**
     * Called by WebView bridge when center 50% viewport chapter changes.
     */
    fun onActiveChapterChanged(chapterId: Int, title: String = "", index: Int = 0) {
        val pos = findChapterPosition(chapterId) ?: return
        if (pos == centerPos) return
        centerPos = pos

        val chapter = allChapters.getOrNull(pos) ?: return
        _currentChapter.value = chapter
        _currentChapterNumber.value = pos + 1
    }

    /**
     * Marks a chapter completed in the database and updates playlist state.
     */
    fun onChapterCompleted(chapterId: Int) {
        val pos = findChapterPosition(chapterId) ?: return
        val chapter = allChapters.getOrNull(pos) ?: return
        if (!chapter.read) {
            viewModelScope.launch(Dispatchers.IO) {
                chapterRepository.updateChapterReadStatus(chapter.id, true)
                allChapters = allChapters.map { ch ->
                    if (ch.id == chapter.id) ch.apply { read = true } else ch
                }
                _tocChapters.value = allChapters
            }
        }
    }

    /**
     * Called by WebView bridge scroll/page progress listener.
     */
    fun onProgressUpdate(chapterId: Int, progress: Float) {
        _readingProgress.value = progress
        // 90% scroll completion fallback as specified
        if (progress >= 0.90f) {
            onChapterCompleted(chapterId)
        }
    }

    /**
     * Advances or reverses page in Paged Mode without full reload.
     */
    fun turnPage(direction: Int) {
        viewModelScope.launch {
            _commands.emit(ReaderCommand.PageTurn(direction))
        }
    }

    /**
     * Called when the user jumps to a chapter from TOC, Prev/Next buttons, or page turns.
     */
    fun jumpToChapter(chapterId: Int, startAtEnd: Boolean = false) {
        val pos = findChapterPosition(chapterId) ?: return
        val targetChapter = allChapters.getOrNull(pos) ?: return

        centerPos = pos
        _currentChapter.value = targetChapter
        _currentChapterNumber.value = pos + 1
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = fetchChapterPayload(targetChapter)
                checkBlockedOrEmpty(payload.htmlContent, targetChapter)
                val hasPrev = pos > 0
                val hasNext = pos < allChapters.size - 1
                _commands.emit(ReaderCommand.SetInitialChapter(payload, hasPrev, hasNext, startAtEnd))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun reloadChapter(chapterId: Int) {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                rawContentCache.remove(chapterId)
                val pos = findChapterPosition(chapterId) ?: centerPos
                val chapter = allChapters.getOrNull(pos) ?: return@launch

                val payload = fetchChapterPayload(chapter)
                checkBlockedOrEmpty(payload.htmlContent, chapter)
                val hasPrev = pos > 0
                val hasNext = pos < allChapters.size - 1
                _commands.emit(ReaderCommand.SetInitialChapter(payload, hasPrev, hasNext))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun dismissBlockedState() {
        _isBlockedOrEmpty.value = false
    }

    // --- Reader Settings Mutations ---

    fun updateTheme(theme: ReaderTheme) = viewModelScope.launch {
        preferenceRepository.updateReaderTheme(theme)
    }

    fun updateReadingMode(mode: ReadingMode) = viewModelScope.launch {
        preferenceRepository.updateReadingMode(mode)
    }

    fun updateFontFamily(fontFamily: String) = viewModelScope.launch {
        preferenceRepository.updateReaderFont(fontFamily)
    }

    fun updateFontSize(sizeSp: Int) = viewModelScope.launch {
        preferenceRepository.updateReaderFontSize(sizeSp)
    }

    fun updateLineHeight(lineHeight: Float) = viewModelScope.launch {
        preferenceRepository.updateReaderLineHeight(lineHeight)
    }

    fun updateHorizontalPadding(paddingDp: Int) = viewModelScope.launch {
        preferenceRepository.updateReaderPadding(paddingDp)
    }

    fun updateTextAlign(align: ReaderTextAlign) = viewModelScope.launch {
        preferenceRepository.updateReaderTextAlign(align)
    }

    fun updateVolumeKeyPageTurn(enabled: Boolean) = viewModelScope.launch {
        preferenceRepository.updateVolumeKeyPageTurn(enabled)
    }

    fun updateKeepScreenAwake(enabled: Boolean) = viewModelScope.launch {
        preferenceRepository.updateKeepScreenAwake(enabled)
    }

    fun updateDimImages(enabled: Boolean) = viewModelScope.launch {
        preferenceRepository.updateDimImages(enabled)
    }

    fun updateShowTapZoneOverlay(enabled: Boolean) = viewModelScope.launch {
        preferenceRepository.updateShowTapZoneOverlay(enabled)
    }

    fun updateCustomCode(css: String, js: String) = viewModelScope.launch {
        preferenceRepository.updateCustomCode(css, js)
    }

    fun addCustomFont(font: CustomFont) = viewModelScope.launch {
        preferenceRepository.addCustomFont(font)
    }

    fun removeCustomFont(font: CustomFont) = viewModelScope.launch {
        preferenceRepository.removeCustomFont(font)
    }

    // --- Payload & Anti-Bot Detection ---

    private suspend fun fetchChapterPayload(chapter: Chapter): ChapterPayload {
        val cached = rawContentCache[chapter.id]
        val html = if (cached != null) {
            cached
        } else {
            val fetched = readerRepository.getChapterContent(chapter, crawlerName)
            rawContentCache[chapter.id] = fetched
            fetched
        }

        val scanlatorText = chapter.scanlationSource.takeIf {
            it.isNotBlank() && it != "NotProvided" && it != "Not Provided"
        } ?: crawlerName.takeIf { it.isNotBlank() } ?: ""

        return ChapterPayload(
            id = chapter.id,
            title = chapter.title,
            index = chapter.index,
            htmlContent = html,
            scanlator = scanlatorText
        )
    }

    private fun checkBlockedOrEmpty(html: String, chapter: Chapter) {
        val trimmed = html.trim()
        val isBlocked = trimmed.contains("cf-browser-verification", ignoreCase = true) ||
                trimmed.contains("challenge-platform", ignoreCase = true) ||
                trimmed.contains("turnstile", ignoreCase = true) ||
                trimmed.contains("Attention Required! | Cloudflare", ignoreCase = true) ||
                (trimmed.contains("Cloudflare", ignoreCase = true) && trimmed.contains("Just a moment", ignoreCase = true)) ||
                trimmed.contains("Couldn't load this chapter", ignoreCase = true) ||
                (trimmed.length < 150 && !trimmed.contains("<p>", ignoreCase = true))

        if (isBlocked) {
            _blockedChapter.value = chapter
            _isBlockedOrEmpty.value = true
        } else {
            if (_blockedChapter.value?.id == chapter.id) {
                _blockedChapter.value = null
                _isBlockedOrEmpty.value = false
            }
        }
    }
}