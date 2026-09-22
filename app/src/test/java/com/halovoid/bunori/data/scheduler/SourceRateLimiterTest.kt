package com.halovoid.bunori.data.scheduler

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SourceRateLimiterTest {

    @Test
    fun testRateLimiterEnforcesSpacing() = runBlocking {
        val rateLimiter = SourceRateLimiter()
        val crawler = "TestCrawler"

        // First acquire should be instant
        val t1 = System.currentTimeMillis()
        rateLimiter.acquire(crawler, 200L)
        val elapsed1 = System.currentTimeMillis() - t1
        assertTrue("First acquire should not delay significantly ($elapsed1 ms)", elapsed1 < 100)

        // Second acquire should enforce ~200ms spacing
        val t2 = System.currentTimeMillis()
        rateLimiter.acquire(crawler, 200L)
        val elapsed2 = System.currentTimeMillis() - t2
        assertTrue("Second acquire should delay for cooldown ($elapsed2 ms)", elapsed2 >= 150)
    }

    @Test
    fun testRateLimiterIndependentBetweenCrawlers() = runBlocking {
        val rateLimiter = SourceRateLimiter()

        // Acquire crawler A
        rateLimiter.acquire("CrawlerA", 500L)

        // Acquiring crawler B immediately should not be blocked by CrawlerA
        val t = System.currentTimeMillis()
        rateLimiter.acquire("CrawlerB", 500L)
        val elapsed = System.currentTimeMillis() - t
        assertTrue("Different crawlers should not block each other ($elapsed ms)", elapsed < 100)
    }

    @Test
    fun testRateLimiterWithJitter() = runBlocking {
        val rateLimiter = SourceRateLimiter()
        val crawler = "JitterCrawler"

        rateLimiter.acquire(crawler, 150L, maxJitterMs = 50L)

        val t = System.currentTimeMillis()
        rateLimiter.acquire(crawler, 150L, maxJitterMs = 50L)
        val elapsed = System.currentTimeMillis() - t
        assertTrue("Acquire with jitter should delay at least the base cooldown ($elapsed ms)", elapsed >= 100)
    }

    @Test
    fun testRateLimiterConcurrentWorkersSpacing() = runBlocking {
        val rateLimiter = SourceRateLimiter()
        val crawler = "ConcurrentCrawler"

        val tStart = System.currentTimeMillis()
        val job1 = async {
            rateLimiter.acquire(crawler, 200L)
        }
        val job2 = async {
            rateLimiter.acquire(crawler, 200L)
        }

        job1.await()
        job2.await()

        val totalElapsed = System.currentTimeMillis() - tStart
        assertTrue("Two concurrent acquires should be serialized with cooldown ($totalElapsed ms)", totalElapsed >= 180)
    }
}
