package com.halovoid.bunori.ui.feature.activity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.halovoid.bunori.api.core.scrapper.Scrapper
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.repository.BatchRepository
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.domain.models.Batch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ActivityViewModel(
    application: Application,
    private val batchRepository: BatchRepository,
    private val preferenceRepository: PreferenceRepository = PreferenceRepository.getInstance(application)
) : AndroidViewModel(application) {

    val batchHistory: StateFlow<List<Batch>> = batchRepository.getRootRequests()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isCompactMode: StateFlow<Boolean> = preferenceRepository.activityCompactView
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    data class GlobalActivityStats(val completed: Int, val total: Int)

    val globalStats: StateFlow<GlobalActivityStats> = batchHistory.map { list ->
        val active = list.filter { 
            it.status == JobStatus.RUNNING || 
            it.status == JobStatus.PAUSED || 
            it.status == JobStatus.PENDING 
        }
        GlobalActivityStats(
            completed = active.sumOf { it.progressSuccess },
            total = active.sumOf { it.progressTotal }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GlobalActivityStats(0, 0)
    )

    val cancellingRequestIds: StateFlow<Set<String>> = batchRepository.cancellingRequestIds
    val activeActionIds: StateFlow<Set<String>> = batchRepository.activeActionIds

    fun setCompactMode(compact: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setActivityCompactView(compact)
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

    fun resolveWebView(requestId: String, url: String) {
        viewModelScope.launch {
            val success = Scrapper.globalResolver?.resolve(url) ?: false
            if (success) {
                batchRepository.resumeRequest(requestId)
            }
        }
    }
}

// Backward compatibility alias
typealias DownloadViewModel = ActivityViewModel
