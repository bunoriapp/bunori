package com.halovoid.bunori.ui.feature.browse

sealed interface BrowseUiEvent {
    data class NavigateToDetail(val crawlerName: String, val novelUrl: String) : BrowseUiEvent
}

typealias RequestUiEvent = BrowseUiEvent
