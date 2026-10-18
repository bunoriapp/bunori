package com.halovoid.bunori.ui.feature.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.halovoid.bunori.api.core.scrapper.Scrapper
import com.halovoid.bunori.data.factory.JobFactory
import com.halovoid.bunori.data.repository.BatchRepository
import com.halovoid.bunori.data.repository.ChapterRepository
import com.halovoid.bunori.data.repository.NovelRepository
import com.halovoid.bunori.data.scheduler.services.SchedulerService
import com.halovoid.bunori.domain.models.Novel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BrowseViewModel(
    application: Application,
    private val batchRepository: BatchRepository,
    private val novelRepository: NovelRepository = NovelRepository.getInstance(application),
    private val jobFactory: JobFactory = JobFactory(),
) : AndroidViewModel(application) {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoading = MutableStateFlow(false)

    val libraryUrls: StateFlow<Set<String>> = novelRepository.getAllNovels()
        .map { novels -> novels.map { it.url }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _addSuccess = MutableSharedFlow<Unit>()

    private val _uiEvents = Channel<BrowseUiEvent>()

    fun resolveWebView(requestId: String, url: String) {
        viewModelScope.launch {
            val success = Scrapper.globalResolver?.resolve(url) ?: false
            if (success) {
                batchRepository.resumeBatch(requestId)
            }
        }
    }

    private val chapterRepository: ChapterRepository = ChapterRepository.getInstance(application)

    fun saveNovelStub(novel: Novel, onSaved: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val existing = novelRepository.getNovelByUrl(novel.url)
                if (existing == null) {
                    novelRepository.saveNovel(novel)
                    val batch = jobFactory.createMetadataBatchFromUrl(novel.crawlerName, novel.url, novel.title)
                    val task = jobFactory.createMetadataTaskFromUrl(batch.id, novel.crawlerName, novel.url, novel.title)
                    batchRepository.insertBatch(batch)
                    batchRepository.insertTask(task)
                    SchedulerService.startService(application)
                } else {
                    val chapters = chapterRepository.getChaptersByNovelUrl(novel.url)
                    if (chapters.isEmpty()) {
                        val batch = jobFactory.createMetadataBatchFromUrl(novel.crawlerName, novel.url, novel.title)
                        val task = jobFactory.createMetadataTaskFromUrl(batch.id, novel.crawlerName, novel.url, novel.title)
                        batchRepository.insertBatch(batch)
                        batchRepository.insertTask(task)
                        SchedulerService.startService(application)
                    }
                }
                onSaved()
            } catch (e: Exception) {
                _error.value = "Failed to save novel: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
sealed interface BrowseUiEvent {
    data class NavigateToDetail(val crawlerName: String, val novelUrl: String) : BrowseUiEvent
}