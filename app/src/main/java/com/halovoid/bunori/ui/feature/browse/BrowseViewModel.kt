package com.halovoid.bunori.ui.feature.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.api.core.scrapper.Scrapper
import com.halovoid.bunori.data.factory.RequestFactory
import com.halovoid.bunori.data.repository.BatchRepository
import com.halovoid.bunori.data.repository.NovelRepository
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.domain.usecase.SaveNovelUseCase
import com.halovoid.bunori.domain.usecase.StartNovelCrawlUseCase
import com.halovoid.bunori.ui.core.logging.AppLog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BrowseViewModel(
    application: Application,
    private val batchRepository: BatchRepository,
    private val novelRepository: NovelRepository = NovelRepository.getInstance(application),
    private val requestFactory: RequestFactory = RequestFactory(),
    private val saveNovelUseCase: SaveNovelUseCase = SaveNovelUseCase(novelRepository),
    private val startNovelCrawlUseCase: StartNovelCrawlUseCase = StartNovelCrawlUseCase(batchRepository, requestFactory)
) : AndroidViewModel(application) {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val libraryUrls: StateFlow<Set<String>> = novelRepository.getAllNovels()
        .map { novels -> novels.map { it.url }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _addSuccess = MutableSharedFlow<Unit>()
    val addSuccess = _addSuccess.asSharedFlow()

    private val _uiEvents = Channel<BrowseUiEvent>()
    val uiEvents = _uiEvents.receiveAsFlow()

    val cancellingRequestIds: StateFlow<Set<String>> = batchRepository.cancellingRequestIds
    val activeActionIds: StateFlow<Set<String>> = batchRepository.activeActionIds

    fun resolveWebView(requestId: String, url: String) {
        viewModelScope.launch {
            AppLog.i("BrowseViewModel", "Starting WebView resolution for $requestId at $url")
            val success = Scrapper.globalResolver?.resolve(url) ?: false
            AppLog.i("BrowseViewModel", "Resolution result: $success")
            if (success) {
                AppLog.i("BrowseViewModel", "Resuming request $requestId")
                batchRepository.resumeRequest(requestId)
            }
        }
    }

    fun validateUrl(url: String): String? {
        val crawler = CrawlerFactory.getCrawlerByUrl(url)
        return if (crawler != null) {
            _error.value = null
            crawler.name
        } else {
            _error.value = "URL not supported or invalid"
            null
        }
    }

    fun saveNovelStub(novel: Novel, onSaved: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                novelRepository.saveNovelMetadata(novel)
                onSaved()
            } catch (e: Exception) {
                _error.value = "Failed to save novel: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveNovel(novel: Novel) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                saveNovelUseCase.saveDirectly(novel)
                _addSuccess.emit(Unit)
                _uiEvents.send(BrowseUiEvent.NavigateToDetail(novel.crawlerName, novel.url))
            } catch (e: Exception) {
                _error.value = "Failed to add to library: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startNovelCrawl(crawlerName: String, url: String, title: String) {
        viewModelScope.launch {
            _isLoading.value = true
            startNovelCrawlUseCase(getApplication(), crawlerName, url, title)
            _isLoading.value = false
        }
    }

    fun cancelRequest(requestId: String) {
        viewModelScope.launch {
            batchRepository.cancelRequest(requestId)
        }
    }

    fun replayRequest(requestId: String) {
        viewModelScope.launch {
            batchRepository.replayRequest(requestId)
        }
    }

    fun resumeRequest(requestId: String) {
        viewModelScope.launch {
            batchRepository.resumeRequest(requestId)
        }
    }

    fun deleteRequestRecord(id: String, requestId: Int) {
        viewModelScope.launch {
            batchRepository.deleteRequest(id)
        }
    }
}

// Alias for compatibility
typealias RequestViewModel = BrowseViewModel
