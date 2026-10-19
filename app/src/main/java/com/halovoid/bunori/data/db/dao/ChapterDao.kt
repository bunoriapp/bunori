package com.halovoid.bunori.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.halovoid.bunori.data.db.entities.ChapterEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE novelUrl = :url ORDER BY `index` ASC, id ASC")
    fun getChapterFromNovel(url: String): List<ChapterEntity>
    @Query("SELECT * FROM chapters WHERE novelUrl = :url ORDER BY `index` ASC, id ASC")
    fun getChaptersFlow(url: String): Flow<List<ChapterEntity>>
    @Query("SELECT * FROM chapters WHERE id = :id")
    fun getChapterById(id: Int): ChapterEntity
    @Query("UPDATE chapters SET read = :read WHERE id = :chapterId")
    suspend fun updateChapterReadStatus(chapterId: Int, read: Boolean)
    @Query("UPDATE chapters SET read = :read WHERE id IN (:chapterIds)")
    suspend fun updateChaptersReadStatus(chapterIds: List<Int>, read: Boolean)
    @Upsert
    suspend fun upsertChapters(chapters: List<ChapterEntity>)
    @Query("UPDATE chapters SET bookmarked = :bookmarked WHERE id = :chapterId")
    suspend fun updateChapterBookmarkStatus(chapterId: Int, bookmarked: Boolean)

    @Query("UPDATE chapters SET bookmarked = :bookmarked WHERE id IN (:chapterIds)")
    suspend fun updateChaptersBookmarkStatus(chapterIds: List<Int>, bookmarked: Boolean)

}