package com.halovoid.bunori.ui.core.platform

import android.view.KeyEvent

/**
 * Manages volume key intercepting across the app.
 * Allows components (such as ReaderScreen) to handle hardware volume key navigation.
 */
object VolumeKeyEventManager {
    private var listener: ((keyCode: Int, event: KeyEvent) -> Boolean)? = null

    fun setListener(listener: ((keyCode: Int, event: KeyEvent) -> Boolean)?) {
        this.listener = listener
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        return listener?.invoke(event.keyCode, event) ?: false
    }
}
