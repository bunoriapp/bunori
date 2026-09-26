package com.halovoid.bunori

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.halovoid.bunori.api.core.network.NetworkClient
import com.halovoid.bunori.api.core.scrapper.Scrapper
import com.halovoid.bunori.crash.CrashActivity
import com.halovoid.bunori.crash.GlobalExceptionHandler
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.scheduler.workers.BackgroundMaintenanceScheduler
import com.halovoid.bunori.extension.manager.ExtensionManager
import com.halovoid.bunori.ui.core.coil.BunoriStorageFetcher
import com.halovoid.bunori.ui.feature.source.webview.WebViewResolverImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BunoriApplication : Application(), ImageLoaderFactory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(BunoriStorageFetcher.Factory(this@BunoriApplication))
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        GlobalExceptionHandler.initialize(this, CrashActivity::class.java)
        NetworkClient.init(this)

        WebViewResolverImpl.initialize(this)
        Scrapper.globalResolver = WebViewResolverImpl.getInstance()

        applicationScope.launch {
            ExtensionManager.getInstance(this@BunoriApplication)
                .loadInstalledExtensions()
        }

        applicationScope.launch {
            PreferenceRepository.getInstance(this@BunoriApplication)
                .customUserAgent.collect { customUa ->
                    NetworkClient.currentUserAgent =
                        customUa?.takeIf { it.isNotBlank() } ?: NetworkClient.DEFAULT_USER_AGENT
                }
        }

        applicationScope.launch {
            BackgroundMaintenanceScheduler.syncAll(this@BunoriApplication)
        }
    }
}
