package com.halovoid.bunori.api.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object WebViewUtil {
    private const val TAG = "WebViewUtil"
    const val MINIMUM_WEBVIEW_VERSION = 118
    fun supportsWebView(context: Context): Boolean {
        return try {
            CookieManager.getInstance()
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
        } catch (_: Throwable) {
            false
        }
    }
}

fun WebView.isOutdated(): Boolean {
    return getWebViewMajorVersion() < WebViewUtil.MINIMUM_WEBVIEW_VERSION
}

@SuppressLint("SetJavaScriptEnabled")
fun WebView.setDefaultSettings() {
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT

        setSupportMultipleWindows(true)
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }

    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
}

fun WebView.setUserAgent(userAgent: String) {
    settings.userAgentString = userAgent
}

private const val WEBVIEW_BRAND = "Android WebView"
private const val CHROMIUM_BRAND = "Chromium"
private const val CHROME_BRAND = "Google Chrome"
private val CHROME_VERSION_REGEX = """Chrome/(\d+)(\.[\d.]+)?""".toRegex()

private fun WebView.getWebViewMajorVersion(): Int {
    val uaRegexMatch = """.*Chrome/(\d+)\..*""".toRegex().matchEntire(getDefaultUserAgentString())
    return if (uaRegexMatch != null && uaRegexMatch.groupValues.size > 1) {
        uaRegexMatch.groupValues[1].toInt()
    } else {
        0
    }
}

private fun WebView.getDefaultUserAgentString(): String {
    val originalUA: String = settings.userAgentString
    settings.userAgentString = null
    val defaultUserAgentString = settings.userAgentString
    settings.userAgentString = originalUA
    return defaultUserAgentString ?: ""
}
