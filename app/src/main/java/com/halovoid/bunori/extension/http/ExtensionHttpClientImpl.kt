package com.halovoid.bunori.extension.http

import android.webkit.CookieManager
import com.halovoid.bunori.api.core.network.NetworkClient
import com.halovoid.bunori.extension.api.http.ExtensionHttpClient
import com.halovoid.bunori.ui.feature.source.webview.WebViewResolverImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

/**
 * Android host implementation of [ExtensionHttpClient].
 *
 * Backed by OkHttp and integrates with WebView cookie handling and User-Agent resolution.
 */
class ExtensionHttpClientImpl(
    private val client: OkHttpClient = NetworkClient.okHttpClient
) : ExtensionHttpClient {

    private val defaultUserAgent =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private fun getResolverUserAgent(url: String): String {
        return try {
            val resolver = WebViewResolverImpl.getInstance()
            val ua = resolver.getUserAgent(url)
            if (ua.isNotBlank()) ua else defaultUserAgent
        } catch (_: Exception) {
            defaultUserAgent
        }
    }

    private fun getCookiesForUrl(url: String): String? {
        return try {
            CookieManager.getInstance().getCookie(url)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildRequest(
        url: String,
        headers: Map<String, String>,
        builderAction: (Request.Builder) -> Unit = {}
    ): Request {
        val builder = Request.Builder().url(url)

        // Set User-Agent
        builder.header("User-Agent", getResolverUserAgent(url))

        // Apply custom headers (can override default User-Agent/Cookie if needed)
        headers.forEach { (k, v) -> builder.header(k, v) }

        builderAction(builder)
        return builder.build()
    }

    override suspend fun get(
        url: String,
        headers: Map<String, String>
    ): Response = withContext(Dispatchers.IO) {
        val request = buildRequest(url, headers) { it.get() }
        client.newCall(request).execute()
    }

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: RequestBody?
    ): Response = withContext(Dispatchers.IO) {
        val request = buildRequest(url, headers) {
            if (body != null) it.post(body)
        }
        client.newCall(request).execute()
    }

    override suspend fun fetch(
        url: String,
        headers: Map<String, String>
    ): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null

        val maxAttempts = 3
        var currentAttempt = 1

        while (currentAttempt <= maxAttempts) {
            try {
                get(url, headers).use { response ->
                    val responseBody = response.body?.string()
                    if (response.isSuccessful) {
                        return@withContext responseBody
                    } else if (response.code in listOf(429, 500, 502, 503, 504) && currentAttempt < maxAttempts) {
                        delay(currentAttempt * 1000L)
                        currentAttempt++
                    } else {
                        return@withContext null
                    }
                }
            } catch (e: IOException) {
                if (currentAttempt < maxAttempts) {
                    delay(currentAttempt * 1000L)
                    currentAttempt++
                } else {
                    return@withContext null
                }
            } catch (e: Exception) {
                return@withContext null
            }
        }
        null
    }

    override suspend fun document(
        url: String,
        headers: Map<String, String>
    ): Document? {
        val html = fetch(url, headers) ?: return null
        return Jsoup.parse(html, url)
    }

    override suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null

        try {
            get(url).use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
