package com.lyrictica.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class YouTubeLookupCacheTest {
    @Test
    fun returnsCachedResultsWithinSoftTtlWithoutRefetching() = runBlocking {
        val tempDir = Files.createTempDirectory("yt-cache-soft").toFile()
        tempDir.deleteOnExit()

        var nowMs = 0L
        val cache = YouTubeLookupCache.create(storageDirectory = tempDir, clock = { nowMs })
        val candidate = candidate("soft")
        var calls = 0

        val first = cache.lookup("Blinding Lights", 5) {
            calls += 1
            listOf(candidate)
        }

        val second = cache.lookup("Blinding Lights", 5) {
            calls += 1
            error("Should not refetch within soft TTL")
        }

        assertEquals(listOf(candidate), first)
        assertEquals(listOf(candidate), second)
        assertEquals(1, calls)
        cache.close()
    }

    @Test
    fun refreshesStaleResultsInBackgroundWhileReturningCachedData() = runBlocking {
        val tempDir = Files.createTempDirectory("yt-cache-stale").toFile()
        tempDir.deleteOnExit()

        var nowMs = 0L
        val cache = YouTubeLookupCache.create(storageDirectory = tempDir, clock = { nowMs })
        val initial = candidate("initial")
        val refreshed = candidate("refreshed")
        val initialLoad = cache.lookup("Blinding Lights", 5) { listOf(initial) }
        assertEquals(listOf(initial), initialLoad)

        nowMs = TimeUnit.HOURS.toMillis(13)
        val refreshStarted = CountDownLatch(1)
        val refreshFinished = CountDownLatch(1)

        val stale = cache.lookup("Blinding Lights", 5) {
            refreshStarted.countDown()
            try {
                listOf(refreshed)
            } finally {
                refreshFinished.countDown()
            }
        }

        assertEquals(listOf(initial), stale)
        assertTrue(refreshStarted.await(2, TimeUnit.SECONDS))
        assertTrue(refreshFinished.await(2, TimeUnit.SECONDS))

        val afterRefresh = cache.lookup("Blinding Lights", 5) {
            error("Should use the refreshed cache")
        }

        assertEquals(listOf(refreshed), afterRefresh)
        cache.close()
    }

    @Test
    fun deduplicatesConcurrentLookupsForTheSameQuery() = runBlocking {
        val tempDir = Files.createTempDirectory("yt-cache-dedupe").toFile()
        tempDir.deleteOnExit()

        val cache = YouTubeLookupCache.create(storageDirectory = tempDir)
        val blocker = CompletableDeferred<Unit>()
        val calls = AtomicInteger(0)
        val candidate = candidate("dedupe")

        val loader: suspend () -> List<YouTubeVideoCandidate> = {
            calls.incrementAndGet()
            blocker.await()
            listOf(candidate)
        }

        val first = async { cache.lookup("Blinding Lights", 5, loader) }
        val second = async { cache.lookup("Blinding Lights", 5, loader) }

        delay(100)
        assertEquals(1, calls.get())

        blocker.complete(Unit)
        assertEquals(listOf(candidate), first.await())
        assertEquals(listOf(candidate), second.await())
        cache.close()
    }

    @Test
    fun expiresHardCachedResultsAfterThirtyDays() = runBlocking {
        val tempDir = Files.createTempDirectory("yt-cache-hard").toFile()
        tempDir.deleteOnExit()

        var nowMs = 0L
        val cache = YouTubeLookupCache.create(storageDirectory = tempDir, clock = { nowMs })
        val initial = candidate("hard-initial")
        val refreshed = candidate("hard-refreshed")
        val first = cache.lookup("Blinding Lights", 5) { listOf(initial) }
        assertEquals(listOf(initial), first)

        nowMs = TimeUnit.DAYS.toMillis(31)
        var calls = 0
        val afterExpiry = cache.lookup("Blinding Lights", 5) {
            calls += 1
            listOf(refreshed)
        }

        assertEquals(listOf(refreshed), afterExpiry)
        assertEquals(1, calls)
        cache.close()
    }

    private fun candidate(id: String): YouTubeVideoCandidate {
        return YouTubeVideoCandidate(
            videoId = id,
            title = "Title $id",
            channelTitle = "Channel $id",
            thumbnailUrl = "https://img.youtube.com/$id.jpg",
            description = "Description $id"
        )
    }
}
