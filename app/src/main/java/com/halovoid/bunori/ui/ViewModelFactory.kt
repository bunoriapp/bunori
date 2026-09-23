package com.halovoid.bunori.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.halovoid.bunori.data.repository.ChapterRepository
import com.halovoid.bunori.data.repository.DownloadRepository
import com.halovoid.bunori.data.repository.NovelRepository
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.repository.ReaderRepository
import com.halovoid.bunori.data.repository.BatchRepository
import com.halovoid.bunori.ui.feature.browse.BrowseViewModel
import com.halovoid.bunori.ui.feature.source.SourceViewModel
import com.halovoid.bunori.ui.feature.activity.ActivityViewModel
import com.halovoid.bunori.ui.feature.activity.JobDetailViewModel
import com.halovoid.bunori.ui.feature.library.LibraryViewModel
import com.halovoid.bunori.ui.feature.novel.NovelViewModel
import com.halovoid.bunori.ui.feature.onboarding.FolderViewModel
import com.halovoid.bunori.ui.feature.reader.ReaderViewModel
import com.halovoid.bunori.ui.feature.search.SearchViewModel
import com.halovoid.bunori.ui.feature.search.source.SourceSearchViewModel
import com.halovoid.bunori.ui.feature.settings.SettingsViewModel

class ViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(BrowseViewModel::class.java) -> {
                BrowseViewModel(application, BatchRepository.getInstance(application)) as T
            }
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> {
                SearchViewModel(application) as T
            }
            modelClass.isAssignableFrom(SourceSearchViewModel::class.java) -> {
                SourceSearchViewModel(application) as T
            }
            modelClass.isAssignableFrom(NovelViewModel::class.java) -> {
                NovelViewModel(
                    application,
                    BatchRepository.getInstance(application),
                    preferenceRepository = PreferenceRepository.getInstance(application)
                ) as T
            }
            modelClass.isAssignableFrom(FolderViewModel::class.java) -> {
                FolderViewModel(application, PreferenceRepository.getInstance(application)) as T
            }
            modelClass.isAssignableFrom(LibraryViewModel::class.java) -> {
                LibraryViewModel(
                    application,
                    NovelRepository.getInstance(application),
                    PreferenceRepository.getInstance(application)
                ) as T
            }
            modelClass.isAssignableFrom(SourceViewModel::class.java) -> {
                SourceViewModel(application, PreferenceRepository.getInstance(application)) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(application) as T
            }
            modelClass.isAssignableFrom(JobDetailViewModel::class.java) -> {
                JobDetailViewModel(application, BatchRepository.getInstance(application)) as T
            }
            modelClass.isAssignableFrom(ActivityViewModel::class.java) -> {
                ActivityViewModel(
                    application,
                    BatchRepository.getInstance(application),
                    PreferenceRepository.getInstance(application)
                ) as T
            }
            modelClass.isAssignableFrom(ReaderViewModel::class.java) -> {
                ReaderViewModel(
                    application,
                    ChapterRepository.getInstance(application),
                    NovelRepository.getInstance(application),
                    ReaderRepository.getInstance(application),
                    PreferenceRepository.getInstance(application),
                    DownloadRepository.getInstance(application)
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
