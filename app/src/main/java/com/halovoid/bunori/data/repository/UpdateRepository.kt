package com.halovoid.bunori.data.repository

import android.content.Context
import com.halovoid.bunori.BuildConfig
import com.halovoid.bunori.api.loader.AppUpdateManager
import com.halovoid.bunori.api.loader.VersionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class UpdateRepository private constructor(context: Context) {
    private val appUpdateManager = AppUpdateManager()
    private val preferenceRepository = PreferenceRepository.getInstance(context)

    private val _isAppUpdateAvailable = MutableStateFlow(false)
    val isAppUpdateAvailable: StateFlow<Boolean> = _isAppUpdateAvailable.asStateFlow()

    private val _latestAppRelease = MutableStateFlow<AppUpdateManager.AppReleaseInfo?>(null)
    val latestAppRelease: StateFlow<AppUpdateManager.AppReleaseInfo?> = _latestAppRelease.asStateFlow()

    private val _isCrawlerUpdateAvailable = MutableStateFlow(false)
    val isCrawlerUpdateAvailable: StateFlow<Boolean> = _isCrawlerUpdateAvailable.asStateFlow()

    suspend fun checkForUpdates() {
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
                INSTANCE ?: UpdateRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
