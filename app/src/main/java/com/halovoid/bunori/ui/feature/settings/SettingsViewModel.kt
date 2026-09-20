package com.halovoid.bunori.ui.feature.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.halovoid.bunori.BuildConfig
import com.halovoid.bunori.data.scheduler.workers.BackgroundMaintenanceScheduler
import com.halovoid.bunori.api.loader.AppUpdateManager
import com.halovoid.bunori.api.loader.UpdateDownloader
import com.halovoid.bunori.api.loader.UpdateInstaller
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.repository.UpdateRepository
import com.halovoid.bunori.data.repository.NovelRepository
import com.halovoid.bunori.data.repository.DownloadRepositoryImpl
import java.io.File
import com.halovoid.bunori.domain.models.CustomFont
import com.halovoid.bunori.domain.models.ReaderSettings
import com.halovoid.bunori.domain.models.ReaderTextAlign
import com.halovoid.bunori.domain.models.ReaderTheme
import com.halovoid.bunori.domain.models.ReadingMode
import com.halovoid.bunori.extension.manager.ExtensionManager
import com.halovoid.bunori.ui.core.theme.ThemeMode
import com.halovoid.bunori.ui.feature.novel.DownloadFilter
import com.halovoid.bunori.ui.feature.novel.SortOrder
import com.halovoid.bunori.ui.feature.novel.SortType
import com.halovoid.bunori.ui.feature.onboarding.UriUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class AppUpdateState {
    object Idle : AppUpdateState()
    object Loading : AppUpdateState()
    data class UpdateAvailable(
        val tagName: String,
        val releaseUrl: String,
        val apkDownloadUrl: String?,
        val releaseNotes: String? = null,
        val publishedAt: String? = null
    ) : AppUpdateState()
    object Downloading : AppUpdateState()
    data class ReadyToInstall(val uri: String) : AppUpdateState()
    object Installing : AppUpdateState()
    object UpToDate : AppUpdateState()
    data class Error(val message: String) : AppUpdateState()
}

class SettingsViewModel(
    application: Application,
    private val preferenceRepository: PreferenceRepository = PreferenceRepository.getInstance(application),
    private val updateRepository: UpdateRepository = UpdateRepository.getInstance(application)
) : AndroidViewModel(application) {
    private val updateDownloader = UpdateDownloader(application)

    private val _refreshing = MutableStateFlow(false)
    private val _downloadUri = MutableStateFlow<String?>(null)
    private val _isDownloading = MutableStateFlow(false)
    private val _isInstalling = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val betaModeApp: StateFlow<Boolean> = preferenceRepository.betaModeApp.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val betaModeCrawlers: StateFlow<Boolean> = preferenceRepository.betaModeCrawlers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val customUserAgent: StateFlow<String?> = preferenceRepository.customUserAgent.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val extensionRepoUrl: StateFlow<String> = preferenceRepository.extensionRepoUrl.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.halovoid.bunori.data.repository.DEFAULT_EXTENSION_REPO_URL
    )

    val ignoreImages: StateFlow<Boolean> = preferenceRepository.ignoreImages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val maxConcurrentJobs: StateFlow<Int> = preferenceRepository.maxConcurrentJobs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 3
    )

    val searchCompactView: StateFlow<Boolean> = preferenceRepository.searchCompactView.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val libraryCompactView: StateFlow<Boolean> = preferenceRepository.libraryCompactView.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val activityCompactView: StateFlow<Boolean> = preferenceRepository.activityCompactView.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val defaultChapterDownloadFilter: StateFlow<DownloadFilter> = preferenceRepository.defaultChapterDownloadFilter
        .map { filterName ->
            runCatching { DownloadFilter.valueOf(filterName) }.getOrDefault(DownloadFilter.ALL)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DownloadFilter.ALL
        )

    val defaultChapterSortType: StateFlow<SortType> = preferenceRepository.defaultChapterSortType
        .map { sortTypeName ->
            runCatching { SortType.valueOf(sortTypeName) }.getOrDefault(SortType.CHAPTER_NUMBER)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SortType.CHAPTER_NUMBER
        )

    val defaultChapterSortOrder: StateFlow<SortOrder> = preferenceRepository.defaultChapterSortOrder
        .map { orderName ->
            runCatching { SortOrder.valueOf(orderName) }.getOrDefault(SortOrder.ASCENDING)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SortOrder.ASCENDING
        )

    val defaultSourceFilter: StateFlow<String> = preferenceRepository.defaultSourceFilter.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "ALL"
    )

    val backupFrequency: StateFlow<String> = preferenceRepository.backupFrequency.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Off"
    )

    val novelPruneFrequency: StateFlow<String> = preferenceRepository.novelPruneFrequency.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Every 10 Days"
    )

    val cacheClearFrequency: StateFlow<String> = preferenceRepository.cacheClearFrequency.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Every 4 Days"
    )

    val lastNovelPruneTime: StateFlow<Long> = preferenceRepository.lastNovelPruneTime.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    val lastCacheClearTime: StateFlow<Long> = preferenceRepository.lastCacheClearTime.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    val themeMode: StateFlow<ThemeMode> = preferenceRepository.themeMode
        .map { modeName ->
            runCatching { ThemeMode.valueOf(modeName) }.getOrDefault(ThemeMode.SYSTEM)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.SYSTEM
        )

    val selectedThemeId: StateFlow<String> = preferenceRepository.selectedThemeId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "DEFAULT"
    )

    val isAmoledMode: StateFlow<Boolean> = preferenceRepository.isAmoledMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val exportFolderUri: StateFlow<Uri?> = preferenceRepository.exportFolderUri.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val friendlyPath: Flow<String> = exportFolderUri.map { uri ->
        if (uri == null) "" else UriUtils.getFriendlyPath(getApplication(), uri)
    }

    private val localUpdateState: Flow<AppUpdateState?> = combine(
        _isDownloading, _downloadUri, _isInstalling, _error
    ) { downloading, uri, installing, error ->
        when {
            error != null -> AppUpdateState.Error(error)
            installing -> AppUpdateState.Installing
            uri != null -> AppUpdateState.ReadyToInstall(uri)
            downloading -> AppUpdateState.Downloading
            else -> null
        }
    }

    val updateState: StateFlow<AppUpdateState> = combine(
        updateRepository.latestAppRelease,
        _refreshing,
        localUpdateState
    ) { latest, refreshing, localState ->
        localState ?: when {
            refreshing -> AppUpdateState.Loading
            latest != null -> {
                val currentVersion = BuildConfig.VERSION_NAME
                if (AppUpdateManager.isUpdateAvailable(currentVersion, latest.tagName)) {
                    AppUpdateState.UpdateAvailable(
                        latest.tagName,
                        latest.releaseUrl,
                        latest.apkDownloadUrl,
                        latest.body,
                        latest.publishedAt
                    )
                } else {
                    AppUpdateState.UpToDate
                }
            }
            else -> AppUpdateState.Idle
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppUpdateState.Idle
    )

    init {
        checkForUpdates()
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _refreshing.value = true
            _error.value = null
            try {
                updateRepository.checkForUpdates()
            } catch (e: Exception) {
                _error.value = "Failed to check for updates"
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun startUpdateDownload(url: String) {
        viewModelScope.launch {
            _isDownloading.value = true
            _error.value = null
            try {
                val downloadId = updateDownloader.downloadUpdate(url, "Bunori_update.apk")
                updateDownloader.getDownloadStatus(downloadId).collect { status ->
                    when (status) {
                        is UpdateDownloader.DownloadStatus.Success -> {
                            _downloadUri.value = status.uri
                            _isDownloading.value = false
                        }
                        is UpdateDownloader.DownloadStatus.Error -> {
                            _error.value = status.message
                            _isDownloading.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                _error.value = "Download failed: ${e.message}"
                _isDownloading.value = false
            }
        }
    }

    fun installUpdate(context: Context, uri: String) {
        viewModelScope.launch {
            try {
                _error.value = null
                UpdateInstaller.installApk(context, uri)
                _isInstalling.value = false
            } catch (e: Exception) {
                _error.value = "Failed to launch installer: ${e.message}"
                _isInstalling.value = false
            }
        }
    }

    fun setBetaModeApp(enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setBetaModeApp(enabled)
            checkForUpdates()
        }
    }

    fun setBetaModeCrawlers(enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setBetaModeCrawlers(enabled)
            checkForUpdates()
        }
    }

    fun setIgnoreImages(enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setIgnoreImages(enabled)
        }
    }

    fun setMaxConcurrentJobs(jobs: Int) {
        viewModelScope.launch {
            preferenceRepository.setMaxConcurrentJobs(jobs)
        }
    }

    fun setExportFolder(uri: Uri) {
        viewModelScope.launch {
            preferenceRepository.setExportFolder(uri)
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            preferenceRepository.setOnboardingCompleted(false)
        }
    }

    fun setSearchCompactView(compact: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setSearchCompactView(compact)
        }
    }

    fun setLibraryCompactView(compact: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setLibraryCompactView(compact)
        }
    }

    fun setActivityCompactView(compact: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setActivityCompactView(compact)
        }
    }

    fun setDefaultChapterDownloadFilter(filter: DownloadFilter) {
        viewModelScope.launch {
            preferenceRepository.setDefaultChapterDownloadFilter(filter.name)
        }
    }

    fun setDefaultChapterSortType(sortType: SortType) {
        viewModelScope.launch {
            preferenceRepository.setDefaultChapterSortType(sortType.name)
        }
    }

    fun setDefaultChapterSortOrder(sortOrder: SortOrder) {
        viewModelScope.launch {
            preferenceRepository.setDefaultChapterSortOrder(sortOrder.name)
        }
    }

    fun setDefaultSourceFilter(sourceFilter: String) {
        viewModelScope.launch {
            preferenceRepository.setDefaultSourceFilter(sourceFilter)
        }
    }

    fun setBackupFrequency(frequency: String) {
        viewModelScope.launch {
            preferenceRepository.setBackupFrequency(frequency)
            BackgroundMaintenanceScheduler.scheduleBackupWork(getApplication(), frequency)
        }
    }

    fun setNovelPruneFrequency(frequency: String) {
        viewModelScope.launch {
            preferenceRepository.setNovelPruneFrequency(frequency)
            BackgroundMaintenanceScheduler.scheduleNovelPruningWork(getApplication(), frequency)
        }
    }

    fun setCacheClearFrequency(frequency: String) {
        viewModelScope.launch {
            preferenceRepository.setCacheClearFrequency(frequency)
            BackgroundMaintenanceScheduler.scheduleCacheClearWork(getApplication(), frequency)
        }
    }

    fun pruneNovelsNow(onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val novelRepo = NovelRepository.getInstance(getApplication())
                val prunable = novelRepo.getPrunableNovels()
                if (prunable.isNotEmpty()) {
                    novelRepo.deleteNovelsByUrl(prunable.map { it.url })
                }
                preferenceRepository.setLastNovelPruneTime(System.currentTimeMillis())
                onComplete(prunable.size)
            } catch (e: Exception) {
                onComplete(0)
            }
        }
    }

    fun clearCacheNow(onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val downloadRepo = DownloadRepositoryImpl.getInstance(getApplication())
                val cached = downloadRepo.getAllCachedDownloads()
                var count = 0
                cached.forEach { dl ->
                    if (dl.fileLocation.isNotBlank() && !dl.fileLocation.startsWith("content://")) {
                        val path = dl.fileLocation.removePrefix("file://")
                        val file = File(path)
                        if (file.exists() && file.delete()) count++
                    }
                }
                val cacheDir = File(getApplication<Application>().cacheDir, "chapter_cache")
                if (cacheDir.exists() && cacheDir.isDirectory) {
                    cacheDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.delete()) count++
                    }
                }
                downloadRepo.deleteAllCachedDownloads()
                preferenceRepository.setLastCacheClearTime(System.currentTimeMillis())
                onComplete(cached.size.coerceAtLeast(count))
            } catch (e: Exception) {
                onComplete(0)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferenceRepository.setThemeMode(mode.name)
        }
    }

    fun setSelectedThemeId(themeId: String) {
        viewModelScope.launch {
            preferenceRepository.setSelectedThemeId(themeId)
        }
    }

    fun setAmoledMode(enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setAmoledMode(enabled)
        }
    }

    fun setExtensionRepoUrl(url: String) {
        viewModelScope.launch {
            preferenceRepository.setExtensionRepoUrl(url)
        }
    }

    fun installExtensionFromUri(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val extensionManager = ExtensionManager.getInstance(getApplication())
            val result = extensionManager.installFromUri(uri)
            result.onSuccess { loaded ->
                onResult(true, "Installed ${loaded.manifest.name}")
            }.onFailure { err ->
                onResult(false, "Failed to install: ${err.message}")
            }
        }
    }

    fun setCustomUserAgent(userAgent: String?) {
        viewModelScope.launch {
            preferenceRepository.setCustomUserAgent(userAgent)
            com.halovoid.bunori.api.core.network.NetworkClient.currentUserAgent =
                userAgent?.takeIf { it.isNotBlank() } ?: com.halovoid.bunori.api.core.network.NetworkClient.DEFAULT_USER_AGENT
        }
    }

    // --- Reader Settings & Custom Fonts ---
    val readerSettings: StateFlow<ReaderSettings> = preferenceRepository.readerSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderSettings()
    )

    val customFonts: StateFlow<List<CustomFont>> = preferenceRepository.customFonts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateReaderTheme(theme: ReaderTheme) = viewModelScope.launch {
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
}
