package com.halovoid.bunori.data.db.dao

import androidx.room.*
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun getTaskByIdFlow(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE batchId = :batchId ORDER BY priority DESC, createdAt ASC")
    fun getTasksByBatchIdFlow(batchId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE batchId = :batchId")
    suspend fun getTasksByBatchId(batchId: String): List<TaskEntity>

    @Query("""
        SELECT t.* FROM tasks t
        INNER JOIN batches b ON t.batchId = b.id
        WHERE t.status = 'PENDING' AND b.status IN ('PENDING', 'RUNNING')
        ORDER BY t.priority DESC, t.createdAt ASC, t.rowid ASC
    """)
    suspend fun getRunnableTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status = 'RUNNING'")
    suspend fun getRunningTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status = 'RUNNING'")
    fun getRunningTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE novelUrl = :novelUrl AND status != 'SUCCESS'")
    fun getActiveTasksByNovelFlow(novelUrl: String): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: JobStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET status = 'SUCCESS', error = NULL, completedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun markSuccess(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET status = 'FAILED', error = :error, attemptCount = :attemptCount, updatedAt = :now WHERE id = :id")
    suspend fun markFailed(id: String, error: String?, attemptCount: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET status = 'RUNNING', error = :error, attemptCount = :attemptCount, updatedAt = :now WHERE id = :id")
    suspend fun markRetrying(id: String, attemptCount: Int, error: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET status = :newStatus, updatedAt = :now WHERE batchId = :batchId AND status != 'SUCCESS'")
    suspend fun updateUnfinishedStatusForBatch(batchId: String, newStatus: JobStatus, now: Long = System.currentTimeMillis())

    @Query("""
        UPDATE tasks 
        SET status = 'PENDING', error = NULL, attemptCount = 0, updatedAt = :now 
        WHERE (batchId = :batchId OR id = :batchId) AND (status = 'PAUSED' OR status = 'BLOCKED')
    """)
    suspend fun resumeTasksForBatch(batchId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET status = 'PENDING', error = NULL, attemptCount = 0, completedAt = NULL, updatedAt = :now WHERE batchId = :batchId")
    suspend fun resetAllTasksForBatch(batchId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET status = 'PENDING', error = NULL, attemptCount = 0, completedAt = NULL, updatedAt = :now WHERE id IN (:taskIds)")
    suspend fun resetTasks(taskIds: List<String>, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET status = 'CANCELLED', updatedAt = :now WHERE id IN (:taskIds) AND status != 'SUCCESS'")
    suspend fun cancelTasks(taskIds: List<String>, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM tasks WHERE batchId = :batchId")
    suspend fun deleteByBatchId(batchId: String)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)
}
