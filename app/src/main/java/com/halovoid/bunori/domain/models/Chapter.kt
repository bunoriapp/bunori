package com.halovoid.bunori.domain.models

/**
 * Data model for a chapter.
 * 
 * NOTE: To maintain binary compatibility with external crawler DEX bundles,
 * the primary constructor must keep its original 7 parameters.
 * New fields like [sourceUrl] are added as regular properties.
 */
data class Chapter(
    val id: Int,
    val url: String,
    val title: String,
    val index: Int,
    val novelUrl: String,
    val isDownloaded: Boolean = false,
    var sourceUrl: String? = null,
    var read: Boolean = false,
    var scanlationSource: String = "Not Provided"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Chapter

        if (id != other.id) return false
        if (url != other.url) return false
        if (title != other.title) return false
        if (index != other.index) return false
        if (novelUrl != other.novelUrl) return false
        if (isDownloaded != other.isDownloaded) return false
        if (sourceUrl != other.sourceUrl) return false
        if (read != other.read) return false
        if (scanlationSource != other.scanlationSource) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + url.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + index.hashCode()
        result = 31 * result + novelUrl.hashCode()
        result = 31 * result + isDownloaded.hashCode()
        result = 31 * result + (sourceUrl?.hashCode() ?: 0)
        result = 31 * result + read.hashCode()
        result = 31 * result + scanlationSource.hashCode()
        return result
    }
}
