package com.halovoid.bunori.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.halovoid.bunori.data.db.entities.ArtifactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtifactDao {

    @Query("SELECT * FROM artifacts WHERE id = :id")
    fun getArtifactById(id: Int): ArtifactEntity

    @Query("SELECT * FROM artifacts WHERE requestId = :id")
    fun getArtifactForBatch(id: String): List<ArtifactEntity>

    @Query("SELECT * FROM artifacts WHERE novelUrl = :url")
    fun getArtifactsByNovelFlow(url: String): Flow<List<ArtifactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtifact(artifact: ArtifactEntity)

    @Delete
    suspend fun removeArtifact(artifact: ArtifactEntity)
}