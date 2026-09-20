package com.halovoid.bunori.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.halovoid.bunori.data.db.entities.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads WHERE novelUrl = :novelUrl AND chapterUrl = :chapterUrl LIMIT 1")
    suspend fun getDownload(novelUrl: String, chapterUrl: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE novelUrl = :novelUrl AND chapterUrl = :chapterUrl LIMIT 1")
    fun getDownloadFlow(novelUrl: String, chapterUrl: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE novelUrl = :novelUrl AND isCache = 0 ORDER BY chapterIndex ASC")
    fun getDownloadsForNovelFlow(novelUrl: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE novelUrl = :novelUrl AND isCache = 0 ORDER BY chapterIndex ASC")
    suspend fun getDownloadsForNovel(novelUrl: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE isCache = 0 ORDER BY downloadedAt DESC")
    fun getAllDownloadsFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE isCache = 0 ORDER BY downloadedAt DESC")
    suspend fun getAllDownloads(): List<DownloadEntity>

    @Query("SELECT chapterUrl FROM downloads WHERE novelUrl = :novelUrl AND isCache = 0")
    fun getDownloadedChapterUrlsFlow(novelUrl: String): Flow<List<String>>

    @Query("SELECT chapterUrl FROM downloads WHERE novelUrl = :novelUrl AND isCache = 0")
    suspend fun getDownloadedChapterUrls(novelUrl: String): List<String>

    @Query("DELETE FROM downloads WHERE isCache = 1 AND expirationTime IS NOT NULL AND expirationTime < :currentTime")
    suspend fun deleteExpiredCache(currentTime: Long)

    @Upsert
    suspend fun upsertDownload(download: DownloadEntity): Long

    @Query("DELETE FROM downloads WHERE novelUrl = :novelUrl AND chapterUrl = :chapterUrl")
    suspend fun deleteDownload(novelUrl: String, chapterUrl: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)

    @Query("SELECT * FROM downloads WHERE isCache = 1")
    suspend fun getAllCachedDownloads(): List<DownloadEntity>

    @Query("DELETE FROM downloads WHERE isCache = 1")
    suspend fun deleteAllCachedDownloads()

    @Query("DELETE FROM downloads WHERE novelUrl = :novelUrl")
    suspend fun deleteDownloadsForNovel(novelUrl: String)
}
