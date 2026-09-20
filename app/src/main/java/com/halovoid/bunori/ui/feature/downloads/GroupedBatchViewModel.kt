package com.halovoid.bunori.ui.feature.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.repository.BatchRepository
import com.halovoid.bunori.domain.models.Batch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class FilterState {
    NONE, INCLUDE, EXCLUDE;

    fun next(): FilterState = when (this) {
        NONE -> INCLUDE
        INCLUDE -> EXCLUDE
        EXCLUDE -> NONE
    }
}

sealed interface BatchScope {
    data object All : BatchScope
    data class ByNovel(val novelUrl: String) : BatchScope
    data class ByDependency(val requestId: String) : BatchScope
}

class GroupedBatchViewModel(
    application: Application,
    private val batchRepository: BatchRepository
) : AndroidViewModel(application) {

    private val _scope = MutableStateFlow<BatchScope?>(null)
    
    private val _statusFilters = MutableStateFlow<Map<JobStatus, FilterState>>(emptyMap())
    val statusFilters: StateFlow<Map<JobStatus, FilterState>> = _statusFilters.asStateFlow()

    fun setStatusFilter(status: JobStatus, state: FilterState) {
        val current = _statusFilters.value.toMutableMap()
        if (state == FilterState.NONE) {
            current.remove(status)
        } else {
            current[status] = state
        }
        _statusFilters.value = current
    }

    val cancellingRequestIds: StateFlow<Set<String>> = batchRepository.cancellingRequestIds
    val activeActionIds: StateFlow<Set<String>> = batchRepository.activeActionIds

    @OptIn(ExperimentalCoroutinesApi::class)
    val allRequests: StateFlow<List<Batch>> = _scope.filterNotNull()
        .flatMapLatest { scope ->
            when (scope) {
                is BatchScope.All -> batchRepository.getRootRequests()
                is BatchScope.ByNovel -> batchRepository.getRootRequestByNovelFlow(scope.novelUrl)
                is BatchScope.ByDependency -> batchRepository.getRequestsByDependenceFlow(scope.requestId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val requests: StateFlow<List<Batch>> = combine(allRequests, _statusFilters) { list, statusMap ->
        var filtered = list

        val excludedStatuses = statusMap.filter { it.value == FilterState.EXCLUDE }.keys
        if (excludedStatuses.isNotEmpty()) {
            filtered = filtered.filter { it.status !in excludedStatuses }
        }

        val includedStatuses = statusMap.filter { it.value == FilterState.INCLUDE }.keys
        if (includedStatuses.isNotEmpty()) {
            filtered = filtered.filter { it.status in includedStatuses }
        }

        filtered
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun loadRequests(contextType: String, contextValue: String) {
        _scope.value = when (contextType.uppercase()) {
            "ALL" -> BatchScope.All
            "NOVEL" -> BatchScope.ByNovel(contextValue)
            "DEPENDENCY" -> BatchScope.ByDependency(contextValue)
            else -> BatchScope.All
        }
    }

    fun loadScope(scope: BatchScope) {
        _scope.value = scope
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

    fun resolveWebView(requestId: String, url: String) {
        viewModelScope.launch {
            val success = com.halovoid.bunori.api.core.scrapper.Scrapper.globalResolver?.resolve(url) ?: false
            if (success) {
                batchRepository.resumeRequest(requestId)
            }
        }
    }
}
