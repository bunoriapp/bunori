package com.halovoid.bunori.api.core.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import com.halovoid.bunori.api.core.network.WebKitCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CloudflareBypassException(message: String = "Failed to bypass Cloudflare challenge") : IOException(message)

class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: WebKitCookieJar,
    defaultUserAgentProvider: () -> String,
) : WebViewInterceptor(context, defaultUserAgentProvider) {

    private val executor = ContextCompat.getMainExecutor(context)

    companion object {
        private const val TAG = "CloudflareInterceptor"
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare", "ddos-guard")
        private val COOKIE_NAMES = listOf("cf_clearance", "__ddg1_", "__ddg2_", "__ddg8_", "__ddg9_", "__ddg10_")
    }

    override fun shouldIntercept(response: Response): Boolean {
        val hasCfMitigated = response.header("cf-mitigated") == "challenge"
        val serverHeader = response.header("Server")?.lowercase()
        val isCfServer = serverHeader?.let { s -> 
            SERVER_CHECK.any { s.contains(it) } 
        } == true
        val isChallengeCode = response.code in listOf(403, 429, 503)
        val isDdosGuard = serverHeader?.contains("ddos-guard") == true && 
            cookieManager.get(response.request.url).none { it.name.startsWith("__ddg1") }

        return (hasCfMitigated && isCfServer) || (isChallengeCode && isCfServer) || hasCfMitigated || isDdosGuard
    }

    override fun intercept(
        chain: Interceptor.Chain,
        request: Request,
        response: Response,
    ): Response {
        Log.i(TAG, "[HeadlessWebView] Cloudflare challenge intercepted for ${request.url} (HTTP ${response.code})")
        try {
            response.close()
            cookieManager.remove(request.url, COOKIE_NAMES, 0)
            val oldCookie = cookieManager.get(request.url)
                .firstOrNull { it.name == "cf_clearance" }
            Log.i(TAG, "[HeadlessWebView] Prior cf_clearance: ${oldCookie?.value?.take(15) ?: "none"}")

            resolveWithWebView(request, oldCookie)

            Log.i(TAG, "[HeadlessWebView] Cloudflare successfully resolved! Retrying ${request.url}")
            return chain.proceed(request)
        } catch (e: CloudflareBypassException) {
            Log.w(TAG, "[HeadlessWebView] Cloudflare bypass could not complete automatically: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[HeadlessWebView] Error during Cloudflare resolution: ${e.message}", e)
            throw IOException(e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request, oldCookie: Cookie?) {
        val latch = CountDownLatch(1)
        var webview: WebView? = null

        var challengeFound = false
        var cloudflareBypassed = false
        var interactiveRequested = false

        val origRequestUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)

        Log.i(TAG, "[HeadlessWebView] Launching headless WebView for $origRequestUrl")

        executor.execute {
            try {
                webview = createWebView(originalRequest)

                webview?.addJavascriptInterface(
                    object {
                        @Suppress("unused")
                        @JavascriptInterface
                        fun interactiveDetected() {
                            Log.w(TAG, "[HeadlessWebView] Cloudflare Turnstile requested human interaction (interactiveBegin). Aborting headless mode.")
                            interactiveRequested = true
                            latch.countDown()
                        }
                    },
                    "bunori",
                )

                webview?.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        Log.i(TAG, "[HeadlessWebView] Page started loading: $url")
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        val title = view.title ?: ""
                        Log.i(TAG, "[HeadlessWebView] Page finished: url=$url, title='$title'")

                        fun isCleared(): Boolean {
                            val current = cookieManager.get(origRequestUrl.toHttpUrl())
                                .firstOrNull { it.name == "cf_clearance" }
                            return current != null && (oldCookie == null || current.value != oldCookie.value)
                        }

                        if (isCleared()) {
                            Log.i(TAG, "[HeadlessWebView] SUCCESS: Acquired valid cf_clearance cookie!")
                            cloudflareBypassed = true
                            latch.countDown()
                            return
                        }

                        val isChallengeTitle = title.contains("Just a moment", ignoreCase = true) ||
                            title.contains("Attention Required", ignoreCase = true) ||
                            title.contains("Security Check", ignoreCase = true)

                        if (isChallengeTitle) {
                            challengeFound = true
                            Log.i(TAG, "[HeadlessWebView] Challenge page detected in WebView. Injecting Turnstile listener...")
                            view.evaluateJavascript(
                                """
                                    addEventListener("message", ({data}) => {
                                        if (data?.source === "cloudflare-challenge" && data?.event === "interactiveBegin") {
                                            bunori.interactiveDetected();
                                        }
                                    });
                                """.trimIndent(),
                                null,
                            )
                        } else if (!challengeFound && url == origRequestUrl) {
                            Log.i(TAG, "[HeadlessWebView] Non-challenge page loaded.")
                            latch.countDown()
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        val isMain = request?.isForMainFrame == true
                        val code = errorResponse?.statusCode ?: 0
                        Log.i(TAG, "[HeadlessWebView] HTTP Error: code=$code, isMain=$isMain, url=${request?.url}")
                        if (isMain) {
                            val hasCfMitigated = errorResponse?.responseHeaders?.any { (k, v) ->
                                k.equals("cf-mitigated", ignoreCase = true) && v.equals("challenge", ignoreCase = true)
                            } == true
                            if (hasCfMitigated || code in listOf(403, 429, 503)) {
                                Log.i(TAG, "[HeadlessWebView] Cloudflare challenge status confirmed ($code)")
                                challengeFound = true
                            }
                        }
                    }
                }

                webview?.loadUrl(origRequestUrl, headers)
            } catch (e: Exception) {
                Log.e(TAG, "[HeadlessWebView] Error setting up headless WebView: ${e.message}", e)
                latch.countDown()
            }
        }

        // Poll for cf_clearance periodically instead of blindly waiting for full timeout
        val startTime = System.currentTimeMillis()
        val maxWaitMs = 30_000L
        val pollIntervalMs = 500L

        while (latch.count > 0 && (System.currentTimeMillis() - startTime) < maxWaitMs) {
            val current = cookieManager.get(origRequestUrl.toHttpUrl())
                .firstOrNull { it.name == "cf_clearance" }
            if (current != null && (oldCookie == null || current.value != oldCookie.value)) {
                Log.i(TAG, "[HeadlessWebView] SUCCESS: cf_clearance detected via polling! Value=${current.value.take(20)}...")
                cloudflareBypassed = true
                latch.countDown()
                break
            }
            try {
                latch.await(pollIntervalMs, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                break
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "[HeadlessWebView] Resolution finished in ${elapsed}ms (bypassed=$cloudflareBypassed, interactiveRequested=$interactiveRequested)")

        executor.execute {
            webview?.run {
                stopLoading()
                destroy()
            }
        }

        if (!cloudflareBypassed) {
            val reason = if (interactiveRequested) {
                "Interactive Cloudflare Turnstile challenge requires manual resolution"
            } else {
                "Cloudflare challenge did not resolve within ${elapsed}ms"
            }
            throw CloudflareBypassException(reason)
        }
    }
}
