package com.halovoid.bunori.domain.usecase

import com.halovoid.bunori.data.repository.ChapterRepository
import com.halovoid.bunori.data.repository.NovelRepository
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.utils.SimhashUtils


class SaveNovelUseCase(
    private val novelRepository: NovelRepository,
    private val chapterRepository: ChapterRepository? = null
) {
    suspend fun check(novel: Novel): List<Novel> {
        val hash = novel.titleHash ?: SimhashUtils.generateSimhash(novel.title)
        val similar = novelRepository.getSimilarNovels(hash, 3)

        return similar
    }

    suspend fun saveDirectly(novel: Novel) {
        novelRepository.saveNovel(novel)
        if (novel.chapters.isNotEmpty()) {
            chapterRepository?.upsertChapters(novel.chapters)
        }
    }
}
