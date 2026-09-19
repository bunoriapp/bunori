package com.halovoid.bunori.ui.feature.reader

/**
 * In-memory holder for the active reading playlist.
 * Allows the Reader to inherit the exact filtered & sorted chapter sequence from NovelScreen,
 * respecting active scanlations, downloaded-only filter, and custom sorting.
 */
object ReadingPlaylistHolder {
    @Volatile
    private var novelUrl: String? = null
    @Volatile
    private var playlistChapterIds: List<Int>? = null

    fun setPlaylist(novelUrl: String, chapterIds: List<Int>) {
        this.novelUrl = novelUrl
        this.playlistChapterIds = chapterIds
    }

    fun getPlaylist(novelUrl: String): List<Int>? {
        return if (this.novelUrl == novelUrl) playlistChapterIds else null
    }

    fun clear() {
        novelUrl = null
        playlistChapterIds = null
    }
}
