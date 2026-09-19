package com.halovoid.bunori.ui.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AppNavigationManager {
    const val EXTRA_NAV_ROUTE = "EXTRA_NAV_ROUTE"

    private val _navigationEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    fun navigateTo(route: String) {
        _navigationEvents.tryEmit(route)
    }
}
