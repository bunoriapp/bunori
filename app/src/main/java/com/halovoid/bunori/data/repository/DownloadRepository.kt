package com.halovoid.bunori.data.repository

import android.content.Context
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.data.db.mappers.toDomain
import com.halovoid.bunori.data.db.mappers.toEntity
import com.halovoid.bunori.domain.models.Download
import com.halovoid.bunori.domain.models.DownloadedNovelSummary
import com.halovoid.bunori.domain.models.NovelDownloadStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface DownloadRepository {
    suspend fun getDownload(novelUrl: String, chapterUrl: String): Download?
    suspend fun getDownloadById(id: Long): Download?
    fun getDownloadFlow(novelUrl: String, chapterUrl: String): Flow<Download?>
    fun getDownloadsForNovelFlow(novelUrl: String): Flow<List<Download>>
    suspend fun getDownloadsForNovel(novelUrl: String): List<Download>
    fun getAllDownloadsFlow(): Flow<List<Download>>
    suspend fun getAllDownloads(): List<Download>
    suspend fun getDownloadedChapterUrls(novelUrl: String): List<String>
    fun getDownloadedChapterUrlsFlow(novelUrl: String): Flow<List<String>>
    suspend fun saveDownload(download: Download): Long
    suspend fun deleteDownload(novelUrl: String, chapterUrl: String)
    suspend fun deleteDownloadById(id: Long)
    suspend fun deleteDownloadsForNovel(novelUrl: String)
    suspend fun getAllCachedDownloads(): List<Download>
    suspend fun deleteAllCachedDownloads()

    companion object {
        fun getInstance(context: Context): DownloadRepository = DownloadRepositoryImpl.getInstance(context)
    }
}

class DownloadRepositoryImpl private constructor(context: Context) : DownloadRepository {
    private val downloadDao = AppDatabase.getDatabase(context).downloadDao()

    companion object {
        @Volatile
        private var INSTANCE: DownloadRepository? = null

        fun getInstance(context: Context): DownloadRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadRepositoryImpl(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override suspend fun getDownload(novelUrl: String, chapterUrl: String): Download? {
        return downloadDao.getDownload(novelUrl, chapterUrl)?.toDomain()
    }

    override suspend fun getDownloadById(id: Long): Download? {
        return downloadDao.getDownloadById(id)?.toDomain()
    }

    override fun getDownloadFlow(novelUrl: String, chapterUrl: String): Flow<Download?> {
        return downloadDao.getDownloadFlow(novelUrl, chapterUrl).map { it?.toDomain() }
    }

    override fun getDownloadsForNovelFlow(novelUrl: String): Flow<List<Download>> {
        return downloadDao.getDownloadsForNovelFlow(novelUrl).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getDownloadsForNovel(novelUrl: String): List<Download> {
        return downloadDao.getDownloadsForNovel(novelUrl).map { it.toDomain() }
    }

    override fun getAllDownloadsFlow(): Flow<List<Download>> {
        return downloadDao.getAllDownloadsFlow().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAllDownloads(): List<Download> {
        return downloadDao.getAllDownloads().map { it.toDomain() }
    }

    override suspend fun getDownloadedChapterUrls(novelUrl: String): List<String> {
        return downloadDao.getDownloadedChapterUrls(novelUrl)
    }

    override fun getDownloadedChapterUrlsFlow(novelUrl: String): Flow<List<String>> {
        return downloadDao.getDownloadedChapterUrlsFlow(novelUrl)
    }

    override suspend fun saveDownload(download: Download): Long {
        return downloadDao.upsertDownload(download.toEntity())
    }

    override suspend fun deleteDownload(novelUrl: String, chapterUrl: String) {
        downloadDao.deleteDownload(novelUrl, chapterUrl)
    }

    override suspend fun deleteDownloadById(id: Long) {
        downloadDao.deleteDownloadById(id)
    }

    override suspend fun deleteDownloadsForNovel(novelUrl: String) {
        downloadDao.deleteDownloadsForNovel(novelUrl)
    }

    override suspend fun getAllCachedDownloads(): List<Download> {
        return downloadDao.getAllCachedDownloads().map { it.toDomain() }
    }

    override suspend fun deleteAllCachedDownloads() {
        downloadDao.deleteAllCachedDownloads()
    }
}
