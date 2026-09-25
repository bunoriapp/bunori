package com.halovoid.bunori.domain.usecase

import com.halovoid.bunori.data.repository.DownloadRepository
import com.halovoid.bunori.data.repository.StorageRepository
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.utils.Logger

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
                Logger.w("DeleteChapterUseCase : Failed to delete chapter file at ${download.fileLocation}", e)
            }
            downloadRepository.deleteDownload(chapter.novelUrl, chapter.url)
        }
    }
}
