package com.halovoid.bunori.data.repository

import android.content.Context
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.data.db.mappers.toDomain
import com.halovoid.bunori.data.db.mappers.toEntity
import com.halovoid.bunori.domain.models.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Main repository interface for managing chapter data.
 */
interface ChapterRepository {
    suspend fun getChaptersByNovelUrl(url: String): List<Chapter>
    fun getChaptersFlow(url: String): Flow<List<Chapter>>
    suspend fun upsertChapters(chapters: List<Chapter>)
    suspend fun getChapterById(id: Int): Chapter
    suspend fun updateChapterReadStatus(chapterId: Int, isRead: Boolean)
    suspend fun updateChaptersReadStatus(chapterIds: List<Int>, isRead: Boolean)

    companion object {
        fun getInstance(context: Context): ChapterRepository = ChapterRepositoryImpl.getInstance(context)
    }
}

class ChapterRepositoryImpl private constructor(context: Context) : ChapterRepository {
    private val db = AppDatabase.getDatabase(context)
    private val chapterDao = db.chapterDao()

    companion object {
        @Volatile
        private var INSTANCE: ChapterRepository? = null

        fun getInstance(context: Context): ChapterRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChapterRepositoryImpl(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override suspend fun getChaptersByNovelUrl(url: String): List<Chapter> = withContext(Dispatchers.IO) {
        chapterDao.getChapterFromNovel(url).map { it.toDomain() }
    }

    override fun getChaptersFlow(url: String): Flow<List<Chapter>> {
        return chapterDao.getChaptersFlow(url)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun upsertChapters(chapters: List<Chapter>) = withContext(Dispatchers.IO) {
        if (chapters.isNotEmpty()) {
            chapterDao.upsertChapters(chapters.map { it.toEntity() })
        }
    }

    override suspend fun getChapterById(id: Int): Chapter = withContext(Dispatchers.IO) {
        chapterDao.getChapterById(id).toDomain()
    }

    override suspend fun updateChapterReadStatus(chapterId: Int, isRead: Boolean) = withContext(Dispatchers.IO) {
        chapterDao.updateChapterReadStatus(chapterId, isRead)
    }

    override suspend fun updateChaptersReadStatus(chapterIds: List<Int>, isRead: Boolean) = withContext(Dispatchers.IO) {
        if (chapterIds.isNotEmpty()) {
            chapterDao.updateChaptersReadStatus(chapterIds, isRead)
        }
    }
}