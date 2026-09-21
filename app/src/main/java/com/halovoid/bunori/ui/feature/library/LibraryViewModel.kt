package com.halovoid.bunori.ui.feature.library

import android.app.Application
import com.halovoid.bunori.domain.models.Novel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.halovoid.bunori.data.repository.NovelRepository
import com.halovoid.bunori.data.repository.PreferenceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class LibraryViewModel(
    application: Application,
    private val novelRepository: NovelRepository = NovelRepository.getInstance(application),
    private val preferenceRepository: PreferenceRepository = PreferenceRepository.getInstance(application)
) : AndroidViewModel(application) {

    val showAllSavedNovels: StateFlow<Boolean> = preferenceRepository.showAllSavedNovels
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val novels: StateFlow<List<Novel>> = showAllSavedNovels
        .flatMapLatest { showAll ->
            if (showAll) {
                novelRepository.getAllSavedNovelsFlow()
            } else {
                novelRepository.getAllNovels()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val libraryCompactView: StateFlow<Boolean> = preferenceRepository.libraryCompactView
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun toggleShowAllSavedNovels() {
        viewModelScope.launch {
            preferenceRepository.setShowAllSavedNovels(!showAllSavedNovels.value)
        }
    }

    fun setShowAllSavedNovels(show: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setShowAllSavedNovels(show)
        }
    }

    fun setLibraryCompactView(compact: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setLibraryCompactView(compact)
        }
    }
}
