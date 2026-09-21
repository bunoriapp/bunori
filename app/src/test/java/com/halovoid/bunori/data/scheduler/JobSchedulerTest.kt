package com.halovoid.bunori.data.scheduler

import com.halovoid.bunori.data.config.SchedulerConfig
import com.halovoid.bunori.data.db.dao.BatchDao
import com.halovoid.bunori.data.db.dao.BatchWithStats
import com.halovoid.bunori.data.db.dao.TaskDao
import com.halovoid.bunori.data.db.entities.BatchEntity
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.JobType
import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.data.scheduler.jobs.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class JobSchedulerTest {

    private class ZeroBackoffPolicy : RetryPolicy {
        override fun getNextDelay(retryCount: Int): Long = 0L
    }

    private class TestJobHandler : JobHandler {
        val handleCountMap = ConcurrentHashMap<String, Int>()
        val alwaysFailTaskIds = ConcurrentHashMap.newKeySet<String>()
        val failTimesThenSucceedMap = ConcurrentHashMap<String, Int>()

        override suspend fun handle(task: TaskEntity): JobResult {
            val count = handleCountMap.compute(task.id) { _, current -> (current ?: 0) + 1 }!!

            if (alwaysFailTaskIds.contains(task.id)) {
                return JobResult.Failure(Exception("Persistent failure for ${task.id}"), isRecoverable = true)
            }

            val failThreshold = failTimesThenSucceedMap[task.id] ?: 0
            if (count <= failThreshold) {
                return JobResult.Failure(Exception("Transient failure $count/$failThreshold for ${task.id}"), isRecoverable = true)
            }

            return JobResult.Success
        }
    }

    private class FakeBatchDao : BatchDao {
        val batches = ConcurrentHashMap<String, BatchEntity>()

        override suspend fun insertBatch(batch: BatchEntity) {
            batches[batch.id] = batch
        }

        override suspend fun getBatchById(id: String): BatchEntity? {
            return batches[id]
        }

        override fun getBatchWithStatsByIdFlow(id: String): Flow<BatchWithStats?> = flow { emit(null) }
        override fun getBatchesWithStatsFlow(): Flow<List<BatchWithStats>> = flow { emit(emptyList()) }
        override fun getBatchesWithStatsByNovelFlow(novelUrl: String): Flow<List<BatchWithStats>> = flow { emit(emptyList()) }

        override suspend fun getActiveBatches(): List<BatchEntity> {
            return batches.values.filter { it.status == JobStatus.RUNNING || it.status == JobStatus.PENDING }
        }

        override suspend fun getBlockedBatches(): List<BatchEntity> {
            return batches.values.filter { it.status == JobStatus.BLOCKED }
        }

        override suspend fun hasActiveOrPendingBatches(): Boolean {
            return batches.values.any { it.status == JobStatus.RUNNING || it.status == JobStatus.PENDING || it.status == JobStatus.PAUSED }
        }

        override suspend fun hasActiveMetadataBatch(novelUrl: String): Boolean {
            return batches.values.any {
                it.novelUrl == novelUrl && it.type == JobType.NOVEL_METADATA &&
                        (it.status == JobStatus.RUNNING || it.status == JobStatus.PENDING || it.status == JobStatus.BLOCKED)
            }
        }

        override suspend fun updateStatus(id: String, status: JobStatus, updatedAt: Long) {
            batches[id]?.let {
                batches[id] = it.copy(status = status, updatedAt = updatedAt)
            }
        }

        override suspend fun updateStatusWithError(id: String, status: JobStatus, error: String?, updatedAt: Long) {
            batches[id]?.let {
                batches[id] = it.copy(status = status, error = error, updatedAt = updatedAt)
            }
        }

        override suspend fun markCompleted(id: String, status: JobStatus, completedAt: Long, updatedAt: Long) {
            batches[id]?.let {
                batches[id] = it.copy(status = status, completedAt = completedAt, updatedAt = updatedAt)
            }
        }
    }

    private class FakeTaskDao : TaskDao {
        val tasks = ConcurrentHashMap<String, TaskEntity>()

        override suspend fun insertTask(task: TaskEntity) {
            tasks[task.id] = task
        }

        override suspend fun insertTasks(tasksList: List<TaskEntity>) {
            tasksList.forEach { tasks[it.id] = it }
        }

        override suspend fun getTaskById(id: String): TaskEntity? {
            return tasks[id]
        }

        override fun getTaskByIdFlow(id: String): Flow<TaskEntity?> = flow { emit(tasks[id]) }
        override fun getTasksByBatchIdFlow(batchId: String): Flow<List<TaskEntity>> = flow { emit(tasks.values.filter { it.batchId == batchId }) }

        override suspend fun getTasksByBatchId(batchId: String): List<TaskEntity> {
            return tasks.values.filter { it.batchId == batchId }
        }

        override suspend fun getRunnableTasks(): List<TaskEntity> {
            return tasks.values
                .filter { it.status == JobStatus.PENDING }
                .sortedWith(compareByDescending<TaskEntity> { it.priority }.thenBy { it.createdAt })
        }

        override suspend fun getRunningTasks(): List<TaskEntity> {
            return tasks.values.filter { it.status == JobStatus.RUNNING }
        }

        override fun getRunningTasksFlow(): Flow<List<TaskEntity>> = flow { emit(tasks.values.filter { it.status == JobStatus.RUNNING }) }
        override fun getActiveTasksByNovelFlow(novelUrl: String): Flow<List<TaskEntity>> = flow { emit(tasks.values.filter { it.novelUrl == novelUrl && it.status != JobStatus.SUCCESS }) }

        override suspend fun updateStatus(id: String, status: JobStatus, updatedAt: Long) {
            tasks[id]?.let {
                tasks[id] = it.copy(status = status, updatedAt = updatedAt)
            }
        }

        override suspend fun markSuccess(id: String, now: Long) {
            tasks[id]?.let {
                tasks[id] = it.copy(status = JobStatus.SUCCESS, completedAt = now, updatedAt = now)
            }
        }

        override suspend fun markFailed(id: String, error: String?, attemptCount: Int, now: Long) {
            tasks[id]?.let {
                tasks[id] = it.copy(status = JobStatus.FAILED, error = error, attemptCount = attemptCount, updatedAt = now)
            }
        }

        override suspend fun markRetrying(id: String, attemptCount: Int, error: String?, now: Long) {
            tasks[id]?.let {
                tasks[id] = it.copy(status = JobStatus.RUNNING, attemptCount = attemptCount, error = error, updatedAt = now)
            }
        }

        override suspend fun updateUnfinishedStatusForBatch(batchId: String, newStatus: JobStatus, now: Long) {
            tasks.values.filter { it.batchId == batchId && it.status != JobStatus.SUCCESS }.forEach {
                tasks[it.id] = it.copy(status = newStatus, updatedAt = now)
            }
        }

        override suspend fun resumeTasksForBatch(batchId: String, now: Long) {
            tasks.values.filter { it.batchId == batchId && (it.status == JobStatus.PAUSED || it.status == JobStatus.BLOCKED) }.forEach {
                tasks[it.id] = it.copy(status = JobStatus.PENDING, attemptCount = 0, error = null, updatedAt = now)
            }
        }

        override suspend fun resetAllTasksForBatch(batchId: String, now: Long) {
            tasks.values.filter { it.batchId == batchId }.forEach {
                tasks[it.id] = it.copy(status = JobStatus.PENDING, attemptCount = 0, error = null, completedAt = null, updatedAt = now)
            }
        }

        override suspend fun resetTasks(taskIds: List<String>, now: Long) {
            taskIds.forEach { id ->
                tasks[id]?.let {
                    tasks[id] = it.copy(status = JobStatus.PENDING, attemptCount = 0, error = null, completedAt = null, updatedAt = now)
                }
            }
        }

        override suspend fun cancelTasks(taskIds: List<String>, now: Long) {
            taskIds.forEach { id ->
                tasks[id]?.let {
                    if (it.status != JobStatus.SUCCESS) {
                        tasks[id] = it.copy(status = JobStatus.CANCELLED, updatedAt = now)
                    }
                }
            }
        }
    }

    private fun createBatch(id: String, type: JobType, novelUrl: String): BatchEntity {
        return BatchEntity(
            id = id,
            name = "Batch $id",
            novelUrl = novelUrl,
            type = type,
            status = JobStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )
    }

    private fun createTask(
        id: String,
        batchId: String,
        crawlerName: String,
        type: JobType,
        priority: Int,
        novelUrl: String,
        maxAttempts: Int = 3
    ): TaskEntity {
        return TaskEntity(
            id = id,
            batchId = batchId,
            name = "Task $id",
            url = "$novelUrl/$id",
            novelUrl = novelUrl,
            priority = priority,
            type = type,
            maxAttempts = maxAttempts,
            metadata = "{\"crawlerName\":\"$crawlerName\"}",
            status = JobStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )
    }

    @Test
    fun testMultiSourceConcurrentSchedulerWithFailuresAndRetries() = runBlocking {
        val batchDao = FakeBatchDao()
        val taskDao = FakeTaskDao()
        val testHandler = TestJobHandler()

        val registry = JobHandlerRegistry()
        registry.register(JobType.RANGE_DOWNLOAD, testHandler)
        registry.register(JobType.NOVEL_METADATA, testHandler)
        registry.register(JobType.CHAPTER, testHandler)

        // Setup Source A batches and tasks
        val sourceABatchA = createBatch("BatchA_SrcA", JobType.RANGE_DOWNLOAD, "https://siteA.com/novelA")
        val sourceABatchB = createBatch("BatchB_SrcA", JobType.NOVEL_METADATA, "https://siteA.com/novelA")
        val sourceABatchC = createBatch("BatchC_SrcA", JobType.CHAPTER, "https://siteA.com/novelA")

        // 5 tasks for Source A Batch A (Range download, priority 0)
        val srcATasks = (1..5).map { i ->
            createTask("taskA_range_$i", sourceABatchA.id, "SourceA", JobType.RANGE_DOWNLOAD, priority = 0, novelUrl = "https://siteA.com/novelA", maxAttempts = 3)
        }
        val srcABatchBTask = createTask("taskA_meta", sourceABatchB.id, "SourceA", JobType.NOVEL_METADATA, priority = 5, novelUrl = "https://siteA.com/novelA")
        val srcABatchCTask = createTask("taskA_chap", sourceABatchC.id, "SourceA", JobType.CHAPTER, priority = 10, novelUrl = "https://siteA.com/novelA")

        // Specify that 3 tasks in Source A Batch A constantly fail
        testHandler.alwaysFailTaskIds.add("taskA_range_2")
        testHandler.alwaysFailTaskIds.add("taskA_range_4")
        testHandler.alwaysFailTaskIds.add("taskA_range_5")

        // Setup Source B batches and tasks
        val sourceBBatchA = createBatch("BatchA_SrcB", JobType.RANGE_DOWNLOAD, "https://siteB.com/novelB")
        val sourceBBatchB = createBatch("BatchB_SrcB", JobType.NOVEL_METADATA, "https://siteB.com/novelB")

        val srcBTasks = (1..3).map { i ->
            createTask("taskB_range_$i", sourceBBatchA.id, "SourceB", JobType.RANGE_DOWNLOAD, priority = 0, novelUrl = "https://siteB.com/novelB", maxAttempts = 3)
        }
        val srcBBatchBTask = createTask("taskB_meta", sourceBBatchB.id, "SourceB", JobType.NOVEL_METADATA, priority = 5, novelUrl = "https://siteB.com/novelB", maxAttempts = 3)

        // Specify that Source B Batch B metadata task fails the 1st time, but succeeds on 2nd attempt!
        testHandler.failTimesThenSucceedMap["taskB_meta"] = 1

        // Insert all batches and tasks into fake DAOs
        listOf(sourceABatchA, sourceABatchB, sourceABatchC, sourceBBatchA, sourceBBatchB).forEach { batchDao.insertBatch(it) }
        taskDao.insertTasks(srcATasks + srcABatchBTask + srcABatchCTask + srcBTasks + srcBBatchBTask)

        // Create scheduler with ZeroBackoffPolicy so tests run instantly
        val schedulerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val scheduler = JobScheduler(
            batchDao = batchDao,
            taskDao = taskDao,
            handlerRegistry = registry,
            config = SchedulerConfig(pollingIntervalMs = 50L, maxConcurrentJobs = 10),
            retryPolicy = ZeroBackoffPolicy(),
            scope = schedulerScope
        )

        var isCompleted = false
        scheduler.setOnEmptyListener {
            isCompleted = true
        }

        scheduler.start()

        // Wait for scheduler to finish processing all tasks
        withTimeout(5000L) {
            while (!isCompleted) {
                delay(50L)
            }
        }

        scheduler.stop()
        schedulerScope.cancel()

        // VERIFICATIONS

        // 1. Check Source A Batch A (Range Download):
        // 2 tasks succeeded (taskA_range_1, taskA_range_3)
        assertEquals(JobStatus.SUCCESS, taskDao.getTaskById("taskA_range_1")?.status)
        assertEquals(JobStatus.SUCCESS, taskDao.getTaskById("taskA_range_3")?.status)

        // The 3 failing tasks in Source A Batch A were retried until max attempts (3) and marked FAILED
        val failedTask2 = taskDao.getTaskById("taskA_range_2")
        val failedTask4 = taskDao.getTaskById("taskA_range_4")
        val failedTask5 = taskDao.getTaskById("taskA_range_5")

        assertEquals(JobStatus.FAILED, failedTask2?.status)
        assertEquals(3, failedTask2?.attemptCount)
        assertEquals(3, testHandler.handleCountMap["taskA_range_2"])

        assertEquals(JobStatus.FAILED, failedTask4?.status)
        assertEquals(3, failedTask4?.attemptCount)
        assertEquals(3, testHandler.handleCountMap["taskA_range_4"])

        assertEquals(JobStatus.FAILED, failedTask5?.status)
        assertEquals(3, failedTask5?.attemptCount)
        assertEquals(3, testHandler.handleCountMap["taskA_range_5"])

        // Since 3 tasks failed, Source A Batch A's final status is FAILED
        assertEquals(JobStatus.FAILED, batchDao.getBatchById(sourceABatchA.id)?.status)

        // 2. Check Source A High Priority Chapter task and Metadata task succeeded
        assertEquals(JobStatus.SUCCESS, taskDao.getTaskById("taskA_meta")?.status)
        assertEquals(JobStatus.SUCCESS, taskDao.getTaskById("taskA_chap")?.status)
        assertEquals(JobStatus.SUCCESS, batchDao.getBatchById(sourceABatchB.id)?.status)
        assertEquals(JobStatus.SUCCESS, batchDao.getBatchById(sourceABatchC.id)?.status)

        // 3. Check Source B Batch B (Metadata): FAILED 1st time, SUCCEEDED 2nd time!
        val taskBMeta = taskDao.getTaskById("taskB_meta")
        assertEquals(JobStatus.SUCCESS, taskBMeta?.status)
        assertEquals(2, testHandler.handleCountMap["taskB_meta"])
        assertEquals(JobStatus.SUCCESS, batchDao.getBatchById(sourceBBatchB.id)?.status)

        // 4. Check Source B Batch A (Range Download): all tasks succeeded
        srcBTasks.forEach { task ->
            assertEquals(JobStatus.SUCCESS, taskDao.getTaskById(task.id)?.status)
        }
        assertEquals(JobStatus.SUCCESS, batchDao.getBatchById(sourceBBatchA.id)?.status)
    }

    @Test
    fun testPriorityAndRoundRobinExecutionOrder() = runBlocking {
        val batchDao = FakeBatchDao()
        val taskDao = FakeTaskDao()
        val executedOrder = mutableListOf<String>()

        val registry = JobHandlerRegistry()
        val handler = object : JobHandler {
            override suspend fun handle(task: TaskEntity): JobResult {
                executedOrder.add(task.id)
                return JobResult.Success
            }
        }
        registry.register(JobType.CHAPTER, handler)
        registry.register(JobType.RANGE_DOWNLOAD, handler)

        val batchLowA = createBatch("b_low_a", JobType.RANGE_DOWNLOAD, "https://site.com/novelA")
        val batchHighB = createBatch("b_high_b", JobType.CHAPTER, "https://site.com/novelB")

        val taskLowA1 = createTask("low_a_1", batchLowA.id, "SrcA", JobType.RANGE_DOWNLOAD, priority = 0, novelUrl = "https://site.com/novelA")
        val taskLowA2 = createTask("low_a_2", batchLowA.id, "SrcA", JobType.RANGE_DOWNLOAD, priority = 0, novelUrl = "https://site.com/novelA")
        val taskHighB1 = createTask("high_b_1", batchHighB.id, "SrcB", JobType.CHAPTER, priority = 10, novelUrl = "https://site.com/novelB")

        batchDao.insertBatch(batchLowA)
        batchDao.insertBatch(batchHighB)
        taskDao.insertTasks(listOf(taskLowA1, taskLowA2, taskHighB1))

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val scheduler = JobScheduler(
            batchDao = batchDao,
            taskDao = taskDao,
            handlerRegistry = registry,
            config = SchedulerConfig(pollingIntervalMs = 50L, maxConcurrentJobs = 1),
            retryPolicy = ZeroBackoffPolicy(),
            scope = scope
        )

        var finished = false
        scheduler.setOnEmptyListener { finished = true }
        scheduler.start()

        withTimeout(3000L) {
            while (!finished) delay(50L)
        }

        scheduler.stop()
        scope.cancel()

        // High priority task (priority 10) MUST execute before low priority tasks (priority 0)
        assertEquals("high_b_1", executedOrder.first())
        assertEquals(3, executedOrder.size)
    }

    @Test
    fun testPauseAndResumeJob() = runBlocking {
        val batchDao = FakeBatchDao()
        val taskDao = FakeTaskDao()
        val registry = JobHandlerRegistry()

        val batch = createBatch("b1", JobType.RANGE_DOWNLOAD, "https://site.com/novel1")
        val task1 = createTask("t1", batch.id, "Src1", JobType.RANGE_DOWNLOAD, priority = 0, novelUrl = "https://site.com/novel1")

        batchDao.insertBatch(batch)
        taskDao.insertTask(task1)

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val scheduler = JobScheduler(
            batchDao = batchDao,
            taskDao = taskDao,
            handlerRegistry = registry,
            config = SchedulerConfig(pollingIntervalMs = 1000L),
            scope = scope
        )

        scheduler.pauseJob("b1")

        withTimeout(2000L) {
            while (batchDao.getBatchById("b1")?.status != JobStatus.PAUSED) {
                delay(50L)
            }
        }

        assertEquals(JobStatus.PAUSED, batchDao.getBatchById("b1")?.status)
        assertEquals(JobStatus.PAUSED, taskDao.getTaskById("t1")?.status)

        scheduler.resumeJob("b1")

        withTimeout(2000L) {
            while (batchDao.getBatchById("b1")?.status != JobStatus.RUNNING) {
                delay(50L)
            }
        }

        assertEquals(JobStatus.RUNNING, batchDao.getBatchById("b1")?.status)
        assertEquals(JobStatus.PENDING, taskDao.getTaskById("t1")?.status)

        scope.cancel()
    }
}
