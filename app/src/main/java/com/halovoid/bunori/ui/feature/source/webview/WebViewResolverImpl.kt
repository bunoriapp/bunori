package com.halovoid.bunori.ui.feature.source.webview

import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.WebSettings
import androidx.core.content.edit
import androidx.core.net.toUri
import com.halovoid.bunori.api.core.scrapper.WebViewResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

class WebViewResolverImpl(private val context: Context) : WebViewResolver {
    private val sharedPrefs = context.getSharedPreferences("webview_prefs", Context.MODE_PRIVATE)

    companion object {
        private val activeResolutions = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
        private var instance: WebViewResolverImpl? = null

        fun initialize(context: Context) {
            if (instance == null) {
                instance = WebViewResolverImpl(context)
            }
        }

        fun getInstance(): WebViewResolverImpl {
            return instance ?: throw IllegalStateException("WebViewResolverImpl not initialized")
        }

        fun onResolutionResult(host: String, success: Boolean) {
            activeResolutions.remove(host)?.complete(success)
        }
    }

    override suspend fun resolve(url: String): Boolean = withContext(Dispatchers.IO) {
        val host = url.toUri().host ?: return@withContext false

        Log.i("WebViewResolver", "Resolution requested for host: $host ($url)")
        activeResolutions[host]?.let { existingDeferred ->
            Log.i("WebViewResolver", "Waiting for ongoing resolution for $host")
            return@withContext existingDeferred.await()
        }

        val deferred = CompletableDeferred<Boolean>()
        val previous = activeResolutions.putIfAbsent(host, deferred)
        if (previous != null) {
            return@withContext previous.await()
        }

        try {
            val intent = Intent(context, WebViewActivity::class.java).apply {
                putExtra("url", url)
                putExtra("host", host)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)

            val result = deferred.await()
            if (result) {
                delay(500.milliseconds)
            }
            result
        } catch (e: Exception) {
            Log.e("WebViewResolver", "Failed to launch WebView Activity for $host", e)
            activeResolutions.remove(host)
            false
        }
    }

    override fun getUserAgent(url: String?): String {
        val host = url?.toUri()?.host
        if (host != null) {
            val domainUa = sharedPrefs.getString("ua_$host", null)
            if (!domainUa.isNullOrBlank()) return domainUa
        }
        return WebSettings.getDefaultUserAgent(context)
    }

    fun saveUserAgent(host: String, userAgent: String) {
        sharedPrefs.edit { putString("ua_$host", userAgent) }
    }
}
