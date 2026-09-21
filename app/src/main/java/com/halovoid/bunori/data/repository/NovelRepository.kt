package com.halovoid.bunori.data.repository

import android.content.Context
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.data.db.mappers.toDomain
import com.halovoid.bunori.data.db.mappers.toEntity
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.utils.SimhashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Main repository interface for managing novel metadata.
 */
interface NovelRepository {
    fun getAllNovels(): Flow<List<Novel>>
    fun getNovelByUrlFlow(url: String): Flow<Novel?>
    suspend fun getNovelByUrl(novelUrl: String): Novel?
    suspend fun saveNovel(novel: Novel)
    suspend fun toggleLibrary(url: String, inLibrary: Boolean)
    suspend fun getSimilarNovels(hash: Long, threshold: Int): List<Novel>
    suspend fun getPrunableNovels(): List<Novel>
    suspend fun deleteNovelsByUrl(urls: List<String>)
    suspend fun deleteNovel(novel: Novel)

    companion object {
        fun getInstance(context: Context): NovelRepository = NovelRepositoryImpl.getInstance(context)
    }
}

class NovelRepositoryImpl private constructor(context: Context) : NovelRepository {
    private val db = AppDatabase.getDatabase(context)
    private val novelDao = db.novelDao()

    companion object {
        @Volatile
        private var INSTANCE: NovelRepository? = null

        fun getInstance(context: Context): NovelRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NovelRepositoryImpl(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun getAllNovels(): Flow<List<Novel>> {
        return novelDao.getAllNovels()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override fun getNovelByUrlFlow(url: String): Flow<Novel?> {
        return novelDao.getNovelByUrlFlow(url)
            .map { it?.toDomain() }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getNovelByUrl(novelUrl: String): Novel? = withContext(Dispatchers.IO) {
        novelDao.getNovelByUrl(novelUrl)?.toDomain()
    }

    override suspend fun saveNovel(novel: Novel) = withContext(Dispatchers.IO) {
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
    }

    override suspend fun toggleLibrary(url: String, inLibrary: Boolean) = withContext(Dispatchers.IO) {
        novelDao.updateLibraryStatus(url, inLibrary)
        if (inLibrary) {
            val novel = novelDao.getNovelByUrl(url)
            if (novel != null && novel.titleHash == null) {
                val hash = SimhashUtils.generateSimhash(novel.title)
                novelDao.updateTitleHash(url, hash)
            }
        }
    }

    override suspend fun getSimilarNovels(hash: Long, threshold: Int): List<Novel> = withContext(Dispatchers.IO) {
        novelDao.getAllNovelsOnce().map { it.toDomain() }.filter { existingNovel ->
            existingNovel.titleHash?.let { existingHash ->
                SimhashUtils.hammingDistance(hash, existingHash) <= threshold
            } ?: false
        }
    }

    override suspend fun getPrunableNovels(): List<Novel> = withContext(Dispatchers.IO) {
        novelDao.getPrunableNovels().map { it.toDomain() }
    }

    override suspend fun deleteNovelsByUrl(urls: List<String>) = withContext(Dispatchers.IO) {
        if (urls.isNotEmpty()) {
            novelDao.deleteNovelsByUrl(urls)
        }
    }

    override suspend fun deleteNovel(novel: Novel) = withContext(Dispatchers.IO) {
        val entity = novel.toEntity()
        novelDao.deleteNovel(entity)
    }
}
