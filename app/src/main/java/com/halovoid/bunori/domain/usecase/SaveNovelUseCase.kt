package com.halovoid.bunori.domain.usecase

import com.halovoid.bunori.data.repository.ChapterRepository
import com.halovoid.bunori.data.repository.NovelRepository
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.utils.SimhashUtils

sealed interface SaveNovelResult {
    data object Saved : SaveNovelResult
    data class SimilarFound(val similarNovels: List<Novel>) : SaveNovelResult
}

/**
 * Single business action for saving a novel to the user's library with similarity check.
 */
class SaveNovelUseCase(
    private val novelRepository: NovelRepository,
    private val chapterRepository: ChapterRepository? = null
) {
    suspend fun checkAndSave(novel: Novel): SaveNovelResult {
        val hash = novel.titleHash ?: SimhashUtils.generateSimhash(novel.title)
        val similar = novelRepository.getSimilarNovels(hash, 3)

        return if (similar.isNotEmpty()) {
            SaveNovelResult.SimilarFound(similar)
        } else {
            saveDirectly(novel)
            SaveNovelResult.Saved
        }
    }

    suspend fun saveDirectly(novel: Novel) {
        novelRepository.saveNovel(novel)
        if (novel.chapters.isNotEmpty()) {
            chapterRepository?.upsertChapters(novel.chapters)
        }
    }
}
