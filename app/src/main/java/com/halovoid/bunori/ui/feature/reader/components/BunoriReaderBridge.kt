package com.halovoid.bunori.ui.feature.reader.components

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface

/**
 * JavaScript interface bridge exposed to WebKit at `window.BunoriBridge`.
 * Mediates all user touch gestures, infinite scroll boundaries, reading progress,
 * and link routing between JavaScript and Kotlin.
 */
class BunoriReaderBridge(
    private val onCenterTapCallback: () -> Unit,
    private val onPageTurnCallback: (direction: Int) -> Unit = {},
    private val onProgressUpdateCallback: (chapterId: Int, progress: Float) -> Unit,
    private val onChapterCompletedCallback: (chapterId: Int) -> Unit,
    private val onActiveChapterChangedCallback: (chapterId: Int, title: String, index: Int) -> Unit,
    private val onRequestNextChapterCallback: (chapterId: Int) -> Unit,
    private val onRequestPreviousChapterCallback: (chapterId: Int) -> Unit,
    private val onLinkClickCallback: (url: String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onCenterTap() {
        Log.d("BunoriReader", "BunoriReaderBridge.onCenterTap() invoked from JavaScript")
        mainHandler.post {
            onCenterTapCallback()
        }
    }

    @JavascriptInterface
    fun onPageTurn(direction: Int) {
        Log.d("BunoriReader", "BunoriReaderBridge.onPageTurn($direction) invoked from JavaScript")
        mainHandler.post {
            onPageTurnCallback(direction)
        }
    }

    @JavascriptInterface
    fun onProgressUpdate(chapterId: Int, progress: Float) {
        onProgressUpdateCallback(chapterId, progress)
    }

    @JavascriptInterface
    fun onChapterCompleted(chapterId: Int) {
        Log.d("BunoriReader", "BunoriReaderBridge.onChapterCompleted($chapterId) invoked from JavaScript")
        onChapterCompletedCallback(chapterId)
    }

    @JavascriptInterface
    fun onActiveChapterChanged(chapterId: Int, title: String, index: Int) {
        Log.d("BunoriReader", "BunoriReaderBridge.onActiveChapterChanged($chapterId, $title, $index)")
        onActiveChapterChangedCallback(chapterId, title, index)
    }

    @JavascriptInterface
    fun onRequestNextChapter(chapterId: Int) {
        Log.d("BunoriReader", "BunoriReaderBridge.onRequestNextChapter($chapterId)")
        onRequestNextChapterCallback(chapterId)
    }

    @JavascriptInterface
    fun onRequestPreviousChapter(chapterId: Int) {
        Log.d("BunoriReader", "BunoriReaderBridge.onRequestPreviousChapter($chapterId)")
        onRequestPreviousChapterCallback(chapterId)
    }

    @JavascriptInterface
    fun onLinkClick(url: String) {
        Log.d("BunoriReader", "BunoriReaderBridge.onLinkClick($url)")
        mainHandler.post {
            onLinkClickCallback(url)
        }
    }
}
