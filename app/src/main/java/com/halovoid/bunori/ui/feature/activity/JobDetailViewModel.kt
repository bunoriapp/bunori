package com.halovoid.bunori.ui.feature.activity

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.repository.ArtifactRepository
import com.halovoid.bunori.data.repository.ChapterRepository
import com.halovoid.bunori.data.repository.BatchRepository
import com.halovoid.bunori.data.repository.StorageRepository
import com.halovoid.bunori.data.repository.StorageRepositoryImpl
import com.halovoid.bunori.domain.models.Artifact
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.domain.models.Task
import com.halovoid.bunori.ui.core.logging.AppLog
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class JobDetailViewModel(
    application: Application,
    private val batchRepository: BatchRepository
) : AndroidViewModel(application) {
    private val chapterRepository = ChapterRepository.getInstance(application)
    private val artifactRepository = ArtifactRepository.getInstance(application)
    private val storageRepository: StorageRepository = StorageRepositoryImpl.getInstance(application)

    private val _batchId = MutableStateFlow<String?>(null)
    fun setBatchId(id: String) {
        _batchId.value = id
    }

    private val _statusFilter = MutableStateFlow<JobStatus?>(null)
    val statusFilter: StateFlow<JobStatus?> = _statusFilter.asStateFlow()

    fun setStatusFilter(status: JobStatus?) {
        _statusFilter.value = status
    }

    val cancellingBatchIds: StateFlow<Set<String>> = batchRepository.cancellingBatchIds
    val activeActionIds: StateFlow<Set<String>> = batchRepository.activeActionIds

    fun resolveWebView(batchId: String, url: String) {
        viewModelScope.launch {
            AppLog.i("JobDetailViewModel", "Starting WebView resolution for $batchId at $url")
            val success = com.halovoid.bunori.api.core.scrapper.Scrapper.globalResolver?.resolve(url) ?: false
            AppLog.i("JobDetailViewModel", "Resolution result: $success")
            if (success) {
                AppLog.i("JobDetailViewModel", "Resuming batch $batchId")
                batchRepository.resumeBatch(batchId)
            }
        }
    }

    private fun statusPriority(status: JobStatus): Int {
        return when (status) {
            JobStatus.BLOCKED -> 0
            JobStatus.RUNNING -> 1
            JobStatus.PENDING -> 2
            JobStatus.PAUSED -> 3
            JobStatus.CANCELLING -> 4
            JobStatus.FAILED -> 5
            JobStatus.CANCELLED -> 6
            JobStatus.SUCCESS -> 7
        }
    }

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedTaskIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTaskIds: StateFlow<Set<String>> = _selectedTaskIds.asStateFlow()

    fun selectTask(taskId: String) {
        _isSelectionMode.value = true
        _selectedTaskIds.update { it + taskId }
    }

    fun toggleTaskSelection(taskId: String) {
        _selectedTaskIds.update { current ->
            val updated = if (current.contains(taskId)) current - taskId else current + taskId
            if (updated.isEmpty()) {
                _isSelectionMode.value = false
            }
            updated
        }
    }

    fun clearSelection() {
        _isSelectionMode.value = false
        _selectedTaskIds.value = emptySet()
    }

    fun selectAllTasks(tasks: List<Task>) {
        _isSelectionMode.value = true
        _selectedTaskIds.value = tasks.map { it.id }.toSet()
    }

    fun selectTasksByStatus(status: JobStatus, tasks: List<Task>) {
        val matchingIds = tasks.filter { it.status == status }.map { it.id }.toSet()
        if (matchingIds.isNotEmpty()) {
            _isSelectionMode.value = true
            _selectedTaskIds.value = matchingIds
        }
    }

    fun replaySelectedTasks(batchId: String) {
        val selected = _selectedTaskIds.value.toList()
        if (selected.isNotEmpty()) {
            viewModelScope.launch {
                batchRepository.replayTasks(selected, batchId)
                clearSelection()
            }
        }
    }

    fun cancelSelectedTasks(batchId: String) {
        val selected = _selectedTaskIds.value.toList()
        if (selected.isNotEmpty()) {
            viewModelScope.launch {
                batchRepository.cancelTasks(selected, batchId)
                clearSelection()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasks: StateFlow<List<Task>> = combine(_batchId.filterNotNull(), _statusFilter) { id, status ->
        id to status
    }
        .flatMapLatest { (id, status) ->
            batchRepository.getTasksByBatchIdFlow(id).map { taskList ->
                val filtered = if (status == null) taskList else taskList.filter { it.status == status }
                filtered.sortedWith(
                    compareBy<Task> { statusPriority(it.status) }
                        .thenBy { it.name }
                        .thenBy { it.id }
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val chapterMetadata: StateFlow<Chapter?> = _batchId
        .filterNotNull()
        .flatMapLatest { id ->
            batchRepository.getBatchByIdFlow(id)
        }
        .filterNotNull()
        .map { batch ->
            val tasks = batchRepository.getTasksByBatchId(batch.id)
            val task = tasks.firstOrNull()
            if (task?.url != null) {
                val chapters = chapterRepository.getChaptersByNovelUrl(batch.novelUrl)
                chapters.find { it.url == task.url }
            } else null
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val artifactMetadata: StateFlow<Artifact?> = _batchId
        .filterNotNull()
        .flatMapLatest { id ->
            batchRepository.getBatchByIdFlow(id)
        }
        .filterNotNull()
        .map { batch ->
            val artifacts = artifactRepository.getArtifactForBatch(batch.id)
            artifacts.find { it.requestId == batch.id }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun getBatch(batchId: String): Flow<Batch?> {
        return batchRepository.getBatchByIdFlow(batchId)
    }

    fun pauseBatch(batchId: String) {
        viewModelScope.launch {
            batchRepository.pauseBatch(batchId)
        }
    }

    fun replayBatch(batchId: String) {
        viewModelScope.launch {
            batchRepository.replayBatch(batchId)
        }
    }

    fun resumeBatch(batchId: String) {
        viewModelScope.launch {
            batchRepository.resumeBatch(batchId)
        }
    }

    fun cancelBatch(batchId: String) {
        viewModelScope.launch {
            batchRepository.cancelBatch(batchId)
        }
    }

    fun copyArtifactToUri(artifact: Artifact, destinationUri: Uri, onComplete: (Uri?) -> Unit, onFileMissing: () -> Unit) {
        viewModelScope.launch {
            val sourceUri = artifact.artifactDestination.toUri()
            if (!storageRepository.uriExists(sourceUri)) {
                artifactRepository.removeArtifact(artifact)
                onFileMissing()
                return@launch
            }
            val result = storageRepository.copyFile(sourceUri, destinationUri)
            onComplete(result)
        }
    }
}