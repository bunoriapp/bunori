package com.halovoid.bunori.data.repository

import android.content.Context
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.data.db.mappers.toDomain
import com.halovoid.bunori.data.db.mappers.toEntity
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.utils.SimhashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Main repository for managing novel data in the Data layer.
 * Coordinates between the [com.halovoid.bunori.api.core.crawler.Crawler]s (Network)
 * and [com.halovoid.bunori.data.db.AppDatabase] (Local Storage).
 */
class NovelRepository private constructor(context: Context) {
    /** Access to the Room database instance. */
    private val db = AppDatabase.getDatabase(context)
    /** Data Access Object for novel-related database operations. */
    private val novelDao = db.novelDao()
    private val chapterDao = db.chapterDao()

    companion object {
        @Volatile
        private var INSTANCE: NovelRepository? = null

        fun getInstance(context: Context): NovelRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NovelRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Retrieves all novels saved in the local database.
     * @return A [Flow] emitting the latest list of [com.halovoid.bunori.domain.models.Novel]s.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllNovels(): Flow<List<Novel>> {
        return novelDao.getAllNovels().flatMapLatest { novelEntities ->
            if (novelEntities.isEmpty()) {
                flowOf(emptyList())
            } else {
                val flows = novelEntities.map { novelEntity ->
                    chapterDao.getChaptersFlow(novelEntity.url).map { chapterEntities ->
                        novelEntity.toDomain().copy(
                            chapters = chapterEntities.map { it.toDomain() }
                        )
                    }
                }
                combine(*flows.toTypedArray()) { novels ->
                    novels.toList()
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    fun getNovelByUrlFlow(url: String): Flow<Novel?> {
        return novelDao.getNovelByUrlFlow(url).map { it?.toDomain() }
    }

    /**
     * Retrieves full details for a novel.
     *
     * @param novelUrl The URL of the novel.
     * @return The populated [com.halovoid.bunori.domain.models.Novel] or null if not found.
     */
    suspend fun getNovelDetails(novelUrl: String): Novel? = withContext(Dispatchers.IO) {
        val novel = novelDao.getNovelByUrl(novelUrl)?.toDomain() ?: return@withContext null
        val chapters = chapterDao.getChapterFromNovel(novelUrl).map { it.toDomain() }
        novel.copy(chapters = chapters)
    }

    /**
     * Persists a novel and its chapters to the local Room database.
     * @param novel The novel to save.
     */
    suspend fun saveNovelMetadata(novel: Novel) = withContext(Dispatchers.IO) {
        val existing = novelDao.getNovelByUrl(novel.url)
        val inLibrary = if (existing != null && existing.inLibrary) true else novel.inLibrary
        val titleHash = if (inLibrary) {
            existing?.titleHash ?: novel.titleHash ?: SimhashUtils.generateSimhash(novel.title)
        } else {
            null
        }
        val refreshExpiry = if (novel.refreshExpiry > 0L) {
            novel.refreshExpiry
        } else {
            existing?.refreshExpiry ?: 0L
        }
        val novelToSave = novel.copy(
            title = if (novel.title.isNotBlank()) novel.title else (existing?.title ?: novel.title),
            author = novel.author?.takeIf { it.isNotBlank() } ?: existing?.author,
            description = novel.description?.takeIf { it.isNotBlank() } ?: existing?.description,
            coverUrl = if (existing?.coverUrl?.let { it.startsWith("file:") || it.startsWith("content:") } == true) {
                existing.coverUrl
            } else {
                novel.coverUrl ?: existing?.coverUrl
            },
            coverHttpsUrl = novel.coverHttpsUrl ?: existing?.coverHttpsUrl,
            crawlerName = novel.crawlerName.ifBlank { existing?.crawlerName ?: "" },
            inLibrary = inLibrary,
            titleHash = titleHash,
            refreshExpiry = refreshExpiry
        )
        
        novelDao.upsertNovel(novelToSave.toEntity())
        
        // Save chapters if present
        if (novel.chapters.isNotEmpty()) {
            chapterDao.upsertChapters(novel.chapters.map { it.toEntity() })
        }
    }

    suspend fun toggleLibrary(url: String, inLibrary: Boolean) = withContext(Dispatchers.IO) {
        val novel = novelDao.getNovelByUrl(url) ?: return@withContext
        val hash = if (inLibrary) {
            novel.titleHash ?: SimhashUtils.generateSimhash(novel.title)
        } else {
            null
        }
        novelDao.updateLibraryStatus(url, inLibrary, hash)
    }

    suspend fun updateRefreshExpiry(url: String, refreshExpiry: Long) = withContext(Dispatchers.IO) {
        novelDao.updateRefreshExpiry(url, refreshExpiry)
    }

    suspend fun getSimilarNovels(hash: Long, threshold: Int): List<Novel> = withContext(Dispatchers.IO) {
        novelDao.getAllNovelsOnce().map { it.toDomain() }.filter { existingNovel ->
            existingNovel.titleHash?.let { existingHash ->
                SimhashUtils.hammingDistance(hash, existingHash) <= threshold
            } ?: false
        }
    }

    suspend fun getPrunableNovels(): List<Novel> = withContext(Dispatchers.IO) {
        novelDao.getPrunableNovels().map { it.toDomain() }
    }

    suspend fun deleteNovelsByUrl(urls: List<String>) = withContext(Dispatchers.IO) {
        if (urls.isNotEmpty()) {
            novelDao.deleteNovelsByUrl(urls)
        }
    }

    /**
     * Deletes a novel and its chapters from the local database.
     * @param novel The novel to delete.
     */
    suspend fun deleteNovel(novel: Novel) = withContext(Dispatchers.IO) {
        val entity = novel.toEntity()
        novelDao.deleteNovel(entity)
    }
}
