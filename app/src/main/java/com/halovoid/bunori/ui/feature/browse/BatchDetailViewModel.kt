package com.halovoid.bunori.ui.feature.browse

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.repository.ArtifactRepository
import com.halovoid.bunori.data.repository.ChapterRepository
import com.halovoid.bunori.data.repository.BatchRepository
import com.halovoid.bunori.domain.models.Artifact
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.ui.core.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BatchDetailViewModel(
    application: Application,
    private val batchRepository: BatchRepository
) : AndroidViewModel(application) {
    private val chapterRepository = ChapterRepository.getInstance(application)
    private val artifactRepository = ArtifactRepository.getInstance(application)

    private val _requestId = MutableStateFlow<String?>(null)
    fun setRequestId(id: String) {
        _requestId.value = id
    }

    private val _statusFilter = MutableStateFlow<JobStatus?>(null)
    val statusFilter: StateFlow<JobStatus?> = _statusFilter.asStateFlow()

    fun setStatusFilter(status: JobStatus?) {
        _statusFilter.value = status
    }

    val cancellingRequestIds: StateFlow<Set<String>> = batchRepository.cancellingRequestIds
    val activeActionIds: StateFlow<Set<String>> = batchRepository.activeActionIds

    fun resolveWebView(requestId: String, url: String) {
        viewModelScope.launch {
            AppLog.i("BatchDetailViewModel", "Starting WebView resolution for $requestId at $url")
            val success = com.halovoid.bunori.api.core.scrapper.Scrapper.globalResolver?.resolve(url) ?: false
            AppLog.i("BatchDetailViewModel", "Resolution result: $success")
            if (success) {
                AppLog.i("BatchDetailViewModel", "Resuming request $requestId")
                batchRepository.resumeRequest(requestId)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val linkedRequests: StateFlow<List<Batch>> = combine(_requestId.filterNotNull(), _statusFilter) { id, status ->
        id to status
    }
        .flatMapLatest { (id, status) ->
            batchRepository.getRequestsByDependenceFlow(id).map { requests ->
                if (status == null) requests else requests.filter { it.status == status }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val chapterMetadata: StateFlow<Chapter?> = _requestId
        .filterNotNull()
        .flatMapLatest { id ->
            batchRepository.getRequestByIdFlow(id)
        }
        .filterNotNull()
        .map { request ->
            val novelUrl = request.novelUrl ?: request.parentNovel
            if (novelUrl != null && request.url != null) {
                val chapters = chapterRepository.getChaptersByNovelUrl(novelUrl)
                chapters.find { it.url == request.url }
            } else null
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val artifactMetadata: StateFlow<Artifact?> = _requestId
        .filterNotNull()
        .flatMapLatest { id ->
            batchRepository.getRequestByIdFlow(id)
        }
        .filterNotNull()
        .map { request ->
            val artifacts = artifactRepository.getArtifactForRequest(request.id)
            artifacts.find { it.requestId == request.id }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun getRequest(requestId: String): Flow<Batch?> {
        return batchRepository.getRequestByIdFlow(requestId)
    }

    fun pauseRequest(requestId: String) {
        viewModelScope.launch {
            batchRepository.pauseRequest(requestId)
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

    fun cancelRequest(requestId: String) {
        viewModelScope.launch {
            batchRepository.cancelRequest(requestId)
        }
    }

    fun copyArtifactToUri(artifact: Artifact, destinationUri: Uri, onComplete: (Uri?) -> Unit, onFileMissing: () -> Unit) {
        viewModelScope.launch {
            if (!artifactRepository.artifactExists(artifact)) {
                artifactRepository.removeArtifact(artifact)
                onFileMissing()
                return@launch
            }
            val result = artifactRepository.copyArtifactToUri(artifact, destinationUri)
            onComplete(result)
        }
    }
}

// Alias for compatibility
typealias RequestDetailViewModel = BatchDetailViewModel
