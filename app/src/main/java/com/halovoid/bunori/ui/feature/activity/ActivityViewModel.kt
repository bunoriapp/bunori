package com.halovoid.bunori.ui.feature.activity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.halovoid.bunori.api.core.scrapper.Scrapper
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.handlers.utility.crawlerName
import com.halovoid.bunori.data.repository.BatchRepository
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.ui.feature.activity.components.getSourceDisplayName
import com.halovoid.bunori.ui.navigation.AppNavigationManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ActivityViewModel(
    application: Application,
    private val batchRepository: BatchRepository,
    private val preferenceRepository: PreferenceRepository = PreferenceRepository.getInstance(application)
) : AndroidViewModel(application) {

    val batchHistory: StateFlow<List<Batch>> = batchRepository.getBatches()
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

    private val _selectedBatchIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedBatchIds: StateFlow<Set<String>> = _selectedBatchIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedBatchIds.map { it.isNotEmpty() }
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

    fun toggleBatchSelection(batchId: String) {
        _selectedBatchIds.update { current ->
            if (current.contains(batchId)) current - batchId else current + batchId
        }
    }

    fun selectBatch(batchId: String) {
        _selectedBatchIds.update { it + batchId }
    }

    fun selectAllBatches(batches: List<Batch>) {
        _selectedBatchIds.value = batches.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedBatchIds.value = emptySet()
    }

    fun selectBatchesBySourceDisplayName(sourceName: String, visibleBatches: List<Batch>) {
        val matchingIds = visibleBatches
            .filter { it.getSourceDisplayName() == sourceName }
            .map { it.id }
        if (matchingIds.isNotEmpty()) {
            _selectedBatchIds.update { it + matchingIds }
        }
    }

    fun selectBatchesBySource(visibleBatches: List<Batch>) {
        val currentSelected = _selectedBatchIds.value
        if (currentSelected.isEmpty()) return

        val selectedSources = visibleBatches
            .filter { it.id in currentSelected }
            .mapNotNull { it.crawlerName ?: it.novelUrl }
            .toSet()

        if (selectedSources.isEmpty()) return

        val matchingIds = visibleBatches
            .filter { batch -> (batch.crawlerName ?: batch.novelUrl) in selectedSources }
            .map { it.id }

        _selectedBatchIds.update { it + matchingIds }
    }

    fun cancelSelectedBatches() {
        val selectedIds = _selectedBatchIds.value
        if (selectedIds.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            selectedIds.forEach { batchId ->
                batchRepository.cancelBatch(batchId)
            }
        }
    }

    fun replaySelectedBatches() {
        val selectedIds = _selectedBatchIds.value
        if (selectedIds.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            selectedIds.forEach { batchId ->
                batchRepository.replayBatch(batchId)
            }
        }
    }

    fun setCompactMode(compact: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setActivityCompactView(compact)
        }
    }

    fun cancelBatch(batchId: String) {
        viewModelScope.launch {
            batchRepository.cancelBatch(batchId)
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

    fun resolveWebView(batchId: String, url: String) {
        viewModelScope.launch {
            val success = Scrapper.globalResolver?.resolve(url) ?: false
            if (success) {
                batchRepository.resumeBatch(batchId)
            }
        }
    }
}
