package com.halovoid.bunori.ui.feature.crawler.webview

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Bridge between Android WebKit and Kotlin for the interactive chapter DOM extractor.
 */
class ChapterExtractorBridge(
    private val onContentDetected: (selector: String, wordCount: Int, preview: String, html: String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onExtractionDetected(selector: String, wordCount: Int, previewText: String, htmlContent: String) {
        mainHandler.post {
            onContentDetected(selector, wordCount, previewText, htmlContent)
        }
    }
}
