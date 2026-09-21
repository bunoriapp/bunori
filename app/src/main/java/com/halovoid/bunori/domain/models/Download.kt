package com.halovoid.bunori.domain.models

data class Download(
    val id: Long = 0,
    val novelUrl: String,
    val chapterUrl: String,
    val fileLocation: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val scanlationSource: String = "Not Provided",
    val novelTitle: String,
    val sizeBytes: Long = 0,
    val downloadedAt: Long = System.currentTimeMillis(),
    val isCache: Boolean = false,
    val expirationTime: Long? = null
)

data class NovelDownloadStats(
    val totalChapters: Int = 0,
    val totalSizeBytes: Long = 0L,
    val lastDownloadedAt: Long = 0L
) {
    val formattedSize: String
        get() {
            if (totalSizeBytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var size = totalSizeBytes.toDouble()
            var unitIndex = 0
            while (size >= 1024 && unitIndex < units.size - 1) {
                size /= 1024
                unitIndex++
            }
            return String.format(java.util.Locale.US, "%.1f %s", size, units[unitIndex])
        }
}

data class DownloadedNovelSummary(
    val novelUrl: String,
    val novelTitle: String,
    val scanlationSource: String = "Offline",
    val totalChapters: Int = 0,
    val totalSizeBytes: Long = 0L,
    val lastDownloadedAt: Long = 0L
) {
    val formattedSize: String
        get() {
            if (totalSizeBytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var size = totalSizeBytes.toDouble()
            var unitIndex = 0
            while (size >= 1024 && unitIndex < units.size - 1) {
                size /= 1024
                unitIndex++
            }
            return String.format(java.util.Locale.US, "%.1f %s", size, units[unitIndex])
        }
}
