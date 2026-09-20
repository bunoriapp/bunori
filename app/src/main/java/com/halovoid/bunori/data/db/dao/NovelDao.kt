package com.halovoid.bunori.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.halovoid.bunori.data.db.entities.NovelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {
    @Query("SELECT * FROM novels WHERE inLibrary = 1")
    fun getAllNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE inLibrary = 1")
    suspend fun getAllNovelsOnce(): List<NovelEntity>

    @Query("SELECT * FROM novels WHERE url = :url")
    suspend fun getNovelByUrl(url: String): NovelEntity?

    @Query("SELECT * FROM novels WHERE url = :url")
    fun getNovelByUrlFlow(url: String): Flow<NovelEntity?>

    @Query("UPDATE novels SET inLibrary = :inLibrary, titleHash = :titleHash WHERE url = :url")
    suspend fun updateLibraryStatus(url: String, inLibrary: Boolean, titleHash: Long?)

    @Query("UPDATE novels SET refreshExpiry = :refreshExpiry WHERE url = :url")
    suspend fun updateRefreshExpiry(url: String, refreshExpiry: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNovel(novel: NovelEntity)

    @Upsert
    suspend fun upsertNovel(novel: NovelEntity)

    @Query("""
        SELECT * FROM novels 
        WHERE inLibrary = 0 
          AND url NOT IN (SELECT DISTINCT novelUrl FROM batches WHERE novelUrl IS NOT NULL)
          AND url NOT IN (SELECT DISTINCT novelUrl FROM tasks WHERE novelUrl IS NOT NULL)
          AND url NOT IN (SELECT DISTINCT novelUrl FROM downloads WHERE isCache = 0)
          AND url NOT IN (SELECT DISTINCT novelUrl FROM artifacts WHERE novelUrl IS NOT NULL)
    """)
    suspend fun getPrunableNovels(): List<NovelEntity>

    @Query("DELETE FROM novels WHERE url IN (:urls)")
    suspend fun deleteNovelsByUrl(urls: List<String>)

    @Delete
    suspend fun deleteNovel(novel: NovelEntity)
}