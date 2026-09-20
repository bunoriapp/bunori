package com.halovoid.bunori

import android.app.Application
import com.halovoid.bunori.api.core.network.NetworkClient
import com.halovoid.bunori.api.core.scrapper.Scrapper
import com.halovoid.bunori.crash.CrashActivity
import com.halovoid.bunori.crash.GlobalExceptionHandler
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.scheduler.workers.BackgroundMaintenanceScheduler
import com.halovoid.bunori.extension.manager.ExtensionManager
import com.halovoid.bunori.ui.feature.crawler.webview.WebViewResolverImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BunoriApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        GlobalExceptionHandler.initialize(this, CrashActivity::class.java)
        NetworkClient.init(this)

        // Initialize WebView Resolver
        WebViewResolverImpl.initialize(this)
        Scrapper.globalResolver = WebViewResolverImpl.getInstance()

        // Load installed extensions as early as possible
        applicationScope.launch {
            ExtensionManager.getInstance(this@BunoriApplication)
                .loadInstalledExtensions()
        }

        // Sync custom user agent
        applicationScope.launch {
            PreferenceRepository.getInstance(this@BunoriApplication)
                .customUserAgent.collect { customUa ->
                    NetworkClient.currentUserAgent =
                        customUa?.takeIf { it.isNotBlank() } ?: NetworkClient.DEFAULT_USER_AGENT
                }
        }

        // Initialize and sync background maintenance schedules (Auto-backup, Novel pruning, Cache clearing)
        applicationScope.launch {
            BackgroundMaintenanceScheduler.syncAll(this@BunoriApplication)
        }
    }
}
