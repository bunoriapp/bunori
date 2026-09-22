package com.halovoid.bunori.domain.usecase

import android.net.Uri
import com.halovoid.bunori.data.repository.DownloadRepository
import com.halovoid.bunori.data.repository.StorageRepository
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.ui.core.logging.AppLog

/**
 * Single business action for deleting a downloaded chapter's file and updating its database state.
 */
class DeleteChapterUseCase(
    private val downloadRepository: DownloadRepository,
    private val storageRepository: StorageRepository
) {
    suspend operator fun invoke(chapter: Chapter) {
        val download = downloadRepository.getDownload(chapter.novelUrl, chapter.url)
        if (download != null) {
            try {
                storageRepository.delete(download.fileLocation)
            } catch (e: Exception) {
                AppLog.w("DeleteChapterUseCase", "Failed to delete chapter file at ${download.fileLocation}", e)
            }
            downloadRepository.deleteDownload(chapter.novelUrl, chapter.url)
        }
    }
}
