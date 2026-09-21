package com.halovoid.bunori.data.scheduler

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class SourceRateLimiter {
    private val mutexes = ConcurrentHashMap<String, Mutex>()
    private val nextAllowedTime = ConcurrentHashMap<String, Long>()

    suspend fun acquire(crawlerName: String, cooldownMs: Long, maxJitterMs: Long = 0L) {
        if (cooldownMs <= 0) return
        val mutex = mutexes.computeIfAbsent(crawlerName) { Mutex() }

        val waitMs = mutex.withLock {
            val now = System.currentTimeMillis()
            val jitter = if (maxJitterMs > 0) Random.nextLong(0, maxJitterMs + 1) else 0L
            val targetInterval = cooldownMs + jitter

            val earliestStart = maxOf(now, (nextAllowedTime[crawlerName] ?: 0L))
            nextAllowedTime[crawlerName] = earliestStart + targetInterval

            (earliestStart - now).coerceAtLeast(0)
        }

        if (waitMs > 0) delay(waitMs.milliseconds)
    }
}
