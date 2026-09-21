package com.halovoid.bunori.data.db.dao

import androidx.room.*
import com.halovoid.bunori.data.db.entities.BatchEntity
import com.halovoid.bunori.data.db.entities.JobStatus
import kotlinx.coroutines.flow.Flow

data class BatchWithStats(
    @Embedded val batch: BatchEntity,
    val totalTasks: Int,
    val completedTasks: Int,
    val failedTasks: Int,
    val cancelledTasks: Int = 0,
    val runningTasks: Int = 0,
    val pendingTasks: Int = 0,
    val blockedTasks: Int = 0,
    val pausedTasks: Int = 0
)

@Dao
interface BatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: BatchEntity)

    @Query("SELECT * FROM batches WHERE id = :id")
    suspend fun getBatchById(id: String): BatchEntity?

    @Query("""
        SELECT 
            b.*,
            COUNT(t.id) AS totalTasks,
            COUNT(CASE WHEN t.status = 'SUCCESS' THEN 1 END) AS completedTasks,
            COUNT(CASE WHEN t.status = 'FAILED' THEN 1 END) AS failedTasks,
            COUNT(CASE WHEN t.status = 'CANCELLED' THEN 1 END) AS cancelledTasks,
            COUNT(CASE WHEN t.status = 'RUNNING' THEN 1 END) AS runningTasks,
            COUNT(CASE WHEN t.status = 'PENDING' THEN 1 END) AS pendingTasks,
            COUNT(CASE WHEN t.status = 'BLOCKED' THEN 1 END) AS blockedTasks,
            COUNT(CASE WHEN t.status = 'PAUSED' THEN 1 END) AS pausedTasks
        FROM batches b
        LEFT JOIN tasks t ON b.id = t.batchId
        WHERE b.id = :id
        GROUP BY b.id
    """)
    fun getBatchWithStatsByIdFlow(id: String): Flow<BatchWithStats?>

    @Query("""
        SELECT 
            b.*,
            COUNT(t.id) AS totalTasks,
            COUNT(CASE WHEN t.status = 'SUCCESS' THEN 1 END) AS completedTasks,
            COUNT(CASE WHEN t.status = 'FAILED' THEN 1 END) AS failedTasks,
            COUNT(CASE WHEN t.status = 'CANCELLED' THEN 1 END) AS cancelledTasks,
            COUNT(CASE WHEN t.status = 'RUNNING' THEN 1 END) AS runningTasks,
            COUNT(CASE WHEN t.status = 'PENDING' THEN 1 END) AS pendingTasks,
            COUNT(CASE WHEN t.status = 'BLOCKED' THEN 1 END) AS blockedTasks,
            COUNT(CASE WHEN t.status = 'PAUSED' THEN 1 END) AS pausedTasks
        FROM batches b
        LEFT JOIN tasks t ON b.id = t.batchId
        GROUP BY b.id
        ORDER BY b.createdAt DESC
    """)
    fun getBatchesWithStatsFlow(): Flow<List<BatchWithStats>>

    @Query("""
        SELECT 
            b.*,
            COUNT(t.id) AS totalTasks,
            COUNT(CASE WHEN t.status = 'SUCCESS' THEN 1 END) AS completedTasks,
            COUNT(CASE WHEN t.status = 'FAILED' THEN 1 END) AS failedTasks,
            COUNT(CASE WHEN t.status = 'CANCELLED' THEN 1 END) AS cancelledTasks,
            COUNT(CASE WHEN t.status = 'RUNNING' THEN 1 END) AS runningTasks,
            COUNT(CASE WHEN t.status = 'PENDING' THEN 1 END) AS pendingTasks,
            COUNT(CASE WHEN t.status = 'BLOCKED' THEN 1 END) AS blockedTasks,
            COUNT(CASE WHEN t.status = 'PAUSED' THEN 1 END) AS pausedTasks
        FROM batches b
        LEFT JOIN tasks t ON b.id = t.batchId
        WHERE b.novelUrl = :novelUrl
        GROUP BY b.id
        ORDER BY b.createdAt DESC
    """)
    fun getBatchesWithStatsByNovelFlow(novelUrl: String): Flow<List<BatchWithStats>>

    @Query("SELECT * FROM batches WHERE status IN ('RUNNING', 'PENDING')")
    suspend fun getActiveBatches(): List<BatchEntity>

    @Query("SELECT * FROM batches WHERE status = 'BLOCKED'")
    suspend fun getBlockedBatches(): List<BatchEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM batches WHERE status IN ('RUNNING', 'PENDING', 'PAUSED') LIMIT 1)")
    suspend fun hasActiveOrPendingBatches(): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM batches WHERE novelUrl = :novelUrl AND type = 'NOVEL_METADATA' AND status IN ('RUNNING', 'PENDING', 'BLOCKED') LIMIT 1)")
    suspend fun hasActiveMetadataBatch(novelUrl: String): Boolean

    @Query("UPDATE batches SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: JobStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE batches SET status = :status, error = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatusWithError(id: String, status: JobStatus, error: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE batches SET status = :status, error = CASE WHEN :status = 'SUCCESS' THEN NULL ELSE error END, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markCompleted(id: String, status: JobStatus, completedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())
}