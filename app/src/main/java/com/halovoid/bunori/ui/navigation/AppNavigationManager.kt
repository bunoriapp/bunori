package com.halovoid.bunori.ui.navigation

import com.halovoid.bunori.ui.core.components.ContextualAction
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class ContextualBottomBarConfig(
    val visible: Boolean = false,
    val selectedCount: Int? = null,
    val actions: List<ContextualAction> = emptyList()
)

object AppNavigationManager {
    const val EXTRA_NAV_ROUTE = "EXTRA_NAV_ROUTE"

    private val _navigationEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    private val _contextualBottomBar = MutableStateFlow(ContextualBottomBarConfig())
    val contextualBottomBar: StateFlow<ContextualBottomBarConfig> = _contextualBottomBar.asStateFlow()

    fun navigateTo(route: String) {
        _navigationEvents.tryEmit(route)
    }

    fun setContextualBottomBar(config: ContextualBottomBarConfig) {
        _contextualBottomBar.value = config
    }

    fun clearContextualBottomBar() {
        _contextualBottomBar.value = ContextualBottomBarConfig(visible = false)
    }
}
