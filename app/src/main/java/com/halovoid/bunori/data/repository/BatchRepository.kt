package com.halovoid.bunori.data.repository

import android.annotation.SuppressLint
import android.content.Context
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.data.db.entities.BatchEntity
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.data.db.mappers.toDomain
import com.halovoid.bunori.data.handlers.utility.crawlerName
import com.halovoid.bunori.data.scheduler.services.SchedulerService
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.domain.models.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

/**
 * Main repository interface for managing batches and tasks.
 */
interface BatchRepository {
    val cancellingBatchIds: StateFlow<Set<String>>
    val activeActionIds: StateFlow<Set<String>>

    fun getBatches(): Flow<List<Batch>>
    fun getBatchesByNovelFlow(url: String): Flow<List<Batch>>
    fun getBatchByIdFlow(batchId: String): Flow<Batch?>
    fun getTasksByBatchIdFlow(batchId: String): Flow<List<Task>>
    suspend fun getTasksByBatchId(batchId: String): List<TaskEntity>
    fun getActiveTasksByNovelFlow(novelUrl: String): Flow<List<TaskEntity>>

    suspend fun insertBatch(batch: BatchEntity)
    suspend fun insertTask(task: TaskEntity)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    suspend fun pauseBatch(batchId: String)
    suspend fun resumeBatch(batchId: String)
    suspend fun replayBatch(batchId: String)
    suspend fun cancelBatch(batchId: String)

    suspend fun replayTasks(taskIds: List<String>, batchId: String)
    suspend fun cancelTasks(taskIds: List<String>, batchId: String)

    companion object {
        fun getInstance(context: Context): BatchRepository = BatchRepositoryImpl.getInstance(context)
    }
}

class BatchRepositoryImpl private constructor(private val context: Context) : BatchRepository {
    private val db = AppDatabase.getDatabase(context)
    val batchDao = db.batchDao()
    val taskDao = db.taskDao()

    private val _cancellingBatchIds = MutableStateFlow<Set<String>>(emptySet())
    override val cancellingBatchIds: StateFlow<Set<String>> = _cancellingBatchIds.asStateFlow()

    private val _activeActionIds = MutableStateFlow<Set<String>>(emptySet())
    override val activeActionIds: StateFlow<Set<String>> = _activeActionIds.asStateFlow()

    override fun getBatches(): Flow<List<Batch>> = batchDao.getBatchesWithStatsFlow().map { list ->
        list.map { it.toDomain() }
    }.flowOn(Dispatchers.IO)

    override fun getBatchesByNovelFlow(url: String): Flow<List<Batch>> =
        batchDao.getBatchesWithStatsByNovelFlow(url).map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)

    override fun getBatchByIdFlow(batchId: String): Flow<Batch?> =
        batchDao.getBatchWithStatsByIdFlow(batchId).map { it?.toDomain() }.flowOn(Dispatchers.IO)

    override fun getTasksByBatchIdFlow(batchId: String): Flow<List<Task>> =
        taskDao.getTasksByBatchIdFlow(batchId).map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)

    override suspend fun getTasksByBatchId(batchId: String): List<TaskEntity> = withContext(Dispatchers.IO) {
        taskDao.getTasksByBatchId(batchId)
    }

    override fun getActiveTasksByNovelFlow(novelUrl: String): Flow<List<TaskEntity>> =
        taskDao.getActiveTasksByNovelFlow(novelUrl).flowOn(Dispatchers.IO)

    override suspend fun insertBatch(batch: BatchEntity) = withContext(Dispatchers.IO) {
        batchDao.insertBatch(batch)
    }

    override suspend fun insertTask(task: TaskEntity) = withContext(Dispatchers.IO) {
        taskDao.insertTask(task)
    }

    override suspend fun insertTasks(tasks: List<TaskEntity>) = withContext(Dispatchers.IO) {
        taskDao.insertTasks(tasks)
    }

    override suspend fun pauseBatch(batchId: String) = withContext(Dispatchers.IO) {
        _activeActionIds.update { it + batchId }
        try {
            batchDao.updateStatus(batchId, JobStatus.PAUSED)
            taskDao.updateUnfinishedStatusForBatch(batchId, JobStatus.PAUSED)
            SchedulerService.pauseJob(context, batchId)
        } finally {
            _activeActionIds.update { it - batchId }
        }
    }

    override suspend fun resumeBatch(batchId: String) = withContext(Dispatchers.IO) {
        _activeActionIds.update { it + batchId }
        try {
            val batch = batchDao.getBatchById(batchId)
            val crawlerName = batch?.crawlerName

            batchDao.updateStatusWithError(batchId, JobStatus.RUNNING, null)
            taskDao.resumeTasksForBatch(batchId)

            if (crawlerName != null) {
                val blockedBatches = batchDao.getBlockedBatches()
                for (b in blockedBatches) {
                    if (b.crawlerName == crawlerName) {
                        batchDao.updateStatusWithError(b.id, JobStatus.RUNNING, null)
                        taskDao.resumeTasksForBatch(b.id)
                    }
                }
                SchedulerService.unblockCrawler(context, crawlerName)
            }
            SchedulerService.resumeJob(context, batchId)
        } finally {
            _activeActionIds.update { it - batchId }
        }
    }

    override suspend fun replayBatch(batchId: String) = withContext(Dispatchers.IO) {
        _activeActionIds.update { it + batchId }
        try {
            batchDao.updateStatusWithError(batchId, JobStatus.PENDING, null)
            taskDao.resetAllTasksForBatch(batchId)
            SchedulerService.replayJob(context, batchId)
        } finally {
            _activeActionIds.update { it - batchId }
        }
    }

    override suspend fun cancelBatch(batchId: String) = withContext(Dispatchers.IO) {
        _cancellingBatchIds.update { it + batchId }
        try {
            batchDao.updateStatus(batchId, JobStatus.CANCELLED)
            taskDao.updateUnfinishedStatusForBatch(batchId, JobStatus.CANCELLED)
            SchedulerService.cancelJob(context, batchId)
        } finally {
            _cancellingBatchIds.update { it - batchId }
        }
    }

    override suspend fun replayTasks(taskIds: List<String>, batchId: String) = withContext(Dispatchers.IO) {
        if (taskIds.isEmpty()) return@withContext
        _activeActionIds.update { it + batchId }
        try {
            taskDao.resetTasks(taskIds)
            batchDao.updateStatusWithError(batchId, JobStatus.PENDING, null)
            SchedulerService.startService(context)
            SchedulerService.resumeJob(context, batchId)
        } finally {
            _activeActionIds.update { it - batchId }
        }
    }

    override suspend fun cancelTasks(taskIds: List<String>, batchId: String) = withContext(Dispatchers.IO) {
        if (taskIds.isEmpty()) return@withContext
        taskDao.cancelTasks(taskIds)
        for (taskId in taskIds) {
            SchedulerService.cancelJob(context, taskId)
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: BatchRepository? = null

        fun getInstance(context: Context): BatchRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BatchRepositoryImpl(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}