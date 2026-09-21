package com.halovoid.bunori.data.repository

import android.content.Context
import com.halovoid.bunori.BuildConfig
import com.halovoid.bunori.api.loader.AppUpdateManager
import com.halovoid.bunori.api.loader.VersionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Main repository interface for app and extension update checking.
 */
interface UpdateRepository {
    val isAppUpdateAvailable: StateFlow<Boolean>
    val latestAppRelease: StateFlow<AppUpdateManager.AppReleaseInfo?>
    val isCrawlerUpdateAvailable: StateFlow<Boolean>

    suspend fun checkForUpdates()

    companion object {
        fun getInstance(context: Context): UpdateRepository = UpdateRepositoryImpl.getInstance(context)
    }
}

class UpdateRepositoryImpl private constructor(context: Context) : UpdateRepository {
    private val appUpdateManager = AppUpdateManager()
    private val preferenceRepository = PreferenceRepository.getInstance(context)

    private val _isAppUpdateAvailable = MutableStateFlow(false)
    override val isAppUpdateAvailable: StateFlow<Boolean> = _isAppUpdateAvailable.asStateFlow()

    private val _latestAppRelease = MutableStateFlow<AppUpdateManager.AppReleaseInfo?>(null)
    override val latestAppRelease: StateFlow<AppUpdateManager.AppReleaseInfo?> = _latestAppRelease.asStateFlow()

    private val _isCrawlerUpdateAvailable = MutableStateFlow(false)
    override val isCrawlerUpdateAvailable: StateFlow<Boolean> = _isCrawlerUpdateAvailable.asStateFlow()

    override suspend fun checkForUpdates() {
        val appBeta = preferenceRepository.betaModeApp.first()
        checkAppUpdate(appBeta)
    }

    private suspend fun checkAppUpdate(enableBeta: Boolean) {
        val info = appUpdateManager.fetchLatestAppRelease(enableBeta)
        _latestAppRelease.value = info
        _isAppUpdateAvailable.value = VersionUtils.isUpdateAvailable(BuildConfig.VERSION_NAME, info.tagName)
    }

    companion object {
        @Volatile
        private var INSTANCE: UpdateRepository? = null

        fun getInstance(context: Context): UpdateRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UpdateRepositoryImpl(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
