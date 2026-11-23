package io.github.wallwallfacer.yourhealthsync.sync

import io.github.wallwallfacer.yourhealthsync.model.BodySample
import io.github.wallwallfacer.yourhealthsync.model.Metric
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncEngineTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 1_800_000_000_000L

    /** Source that serves samples from a fixed list and records every window it was asked for. */
    private class FakeSource(
        private val samples: List<BodySample> = emptyList(),
        private val failWhen: (Long, Long) -> Boolean = { _, _ -> false },
    ) : BodySource {
        val windows = mutableListOf<TimeWindow>()
        override suspend fun read(startMillis: Long, endMillis: Long): List<BodySample> {
            windows += TimeWindow(startMillis, endMillis)
            if (failWhen(startMillis, endMillis)) throw IllegalStateException("huawei read failed")
            return samples.filter { it.epochMillis in startMillis until endMillis }
        }
    }

    private class FakeSink(private val fail: Boolean = false) : BodySink {
        val written = mutableListOf<BodySample>()
        var calls = 0
        override suspend fun write(samples: List<BodySample>): Int {
            calls++
            if (fail) throw IllegalStateException("health connect write failed")
            written += samples
            return samples.size
        }
    }

    private fun weight(at: Long) = BodySample(Metric.WEIGHT, at, 70.0)

    @Test
    fun syncRecentReadsTheTrailingThirtyDaysAndWritesWhatItFinds() = runTest {
        val source = FakeSource(listOf(weight(now - 2 * day), weight(now - 40 * day)))
        val sink = FakeSink()

        val result = SyncEngine(source, sink) { now }.syncRecent()

        assertEquals(listOf(TimeWindow(now - 30 * day, now)), source.windows)
        assertEquals(listOf(weight(now - 2 * day)), sink.written)
        assertEquals(SyncResult(written = 1, failedWindows = emptyList()), result)
    }

    @Test
    fun backfillSplitsTheRangeIntoContiguousChunksOfAtMostThirtyDays() = runTest {
        val source = FakeSource()

        SyncEngine(source, FakeSink()) { now }.backfill(fromMillis = now - 75 * day)

        val windows = source.windows
        assertEquals(3, windows.size)
        assertEquals(now - 75 * day, windows.first().startMillis)
        assertEquals(now, windows.last().endMillis)
        windows.zipWithNext().forEach { (a, b) -> assertEquals(a.endMillis, b.startMillis) }
        windows.forEach { assertTrue(it.endMillis - it.startMillis <= 30 * day) }
    }

    @Test
    fun aFailingReadIsReportedWithoutAbortingTheOtherChunks() = runTest {
        val old = weight(now - 70 * day)
        val recent = weight(now - 1 * day)
        val failingStart = now - 45 * day
        val source = FakeSource(listOf(old, recent)) { start, end -> failingStart in start until end }
        val sink = FakeSink()

        val result = SyncEngine(source, sink) { now }.backfill(fromMillis = now - 75 * day)

        assertEquals(listOf(old, recent), sink.written)
        assertEquals(2, result.written)
        assertEquals(1, result.failedWindows.size)
        assertTrue(failingStart in result.failedWindows[0].startMillis until result.failedWindows[0].endMillis)
    }

    @Test
    fun aFailingWriteCountsAsAFailedWindow() = runTest {
        val result = SyncEngine(FakeSource(listOf(weight(now - day))), FakeSink(fail = true)) { now }.syncRecent()

        assertEquals(SyncResult(written = 0, failedWindows = listOf(TimeWindow(now - 30 * day, now))), result)
    }

    @Test
    fun emptyWindowsDoNotCallTheSink() = runTest {
        val sink = FakeSink()

        SyncEngine(FakeSource(), sink) { now }.syncRecent()

        assertEquals(0, sink.calls)
    }

    @Test
    fun cancellationIsNotSwallowedAsAFailedWindow() = runTest {
        val source = object : BodySource {
            override suspend fun read(startMillis: Long, endMillis: Long): List<BodySample> =
                throw CancellationException("cancelled")
        }
        try {
            SyncEngine(source, FakeSink()) { now }.syncRecent()
            fail("expected CancellationException")
        } catch (expected: CancellationException) {
            // ok
        }
    }
}
