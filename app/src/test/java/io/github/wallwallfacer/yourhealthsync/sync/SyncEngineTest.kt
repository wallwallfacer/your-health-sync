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

    private class FakeSink(private val failWhen: (List<BodySample>) -> Boolean = { false }) : BodySink {
        val written = mutableListOf<BodySample>()
        val batches = mutableListOf<List<BodySample>>()
        val versions = mutableListOf<Long>()
        override suspend fun write(samples: List<BodySample>, version: Long): Int {
            batches += samples
            versions += version
            if (failWhen(samples)) throw IllegalStateException("health connect write failed")
            written += samples
            return samples.size
        }
    }

    private fun weight(at: Long, kg: Double = 70.0) = BodySample(Metric.WEIGHT, at, kg)
    private fun fat(at: Long) = BodySample(Metric.BODY_FAT, at, 20.0)
    private fun engine(sink: BodySink, metrics: Set<Metric> = Metric.entries.toSet(), chunkSize: Int = 1000) =
        SyncEngine(sink, now = { now }, metrics = { metrics }, chunkSize = chunkSize)

    @Test
    fun syncRecentReadsTheTrailingThirtyDaysAndWritesWhatItFinds() = runTest {
        val source = FakeSource(listOf(weight(now - 2 * day), weight(now - 40 * day)))
        val sink = FakeSink()

        val result = engine(sink).syncRecent(source)

        assertEquals(listOf(TimeWindow(now - 30 * day, now)), source.windows)
        assertEquals(listOf(weight(now - 2 * day)), sink.written)
        assertEquals(1, result.written)
        assertEquals(emptyList<TimeWindow>(), result.failedWindows)
        assertEquals(null, result.firstError)
    }

    @Test
    fun syncWritesWithTheMappingVersion() = runTest {
        val sink = FakeSink()

        engine(sink).syncRecent(FakeSource(listOf(weight(now - day))))

        assertEquals(listOf(RecordMapper.MAPPING_VERSION), sink.versions)
    }

    @Test
    fun backfillSplitsTheRangeIntoContiguousChunksOfAtMostThirtyDays() = runTest {
        val source = FakeSource()

        engine(FakeSink()).backfill(source, fromMillis = now - 75 * day)

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

        val result = engine(sink).backfill(source, fromMillis = now - 75 * day)

        assertEquals(listOf(old, recent), sink.written)
        assertEquals(2, result.written)
        assertEquals(1, result.failedWindows.size)
        assertTrue(failingStart in result.failedWindows[0].startMillis until result.failedWindows[0].endMillis)
    }

    @Test
    fun aFailingWriteCountsAsAFailedWindow() = runTest {
        val result = engine(FakeSink { true }).syncRecent(FakeSource(listOf(weight(now - day))))

        assertEquals(listOf(TimeWindow(now - 30 * day, now)), result.failedWindows)
        assertEquals(0, result.written)
    }

    @Test
    fun theFirstErrorMessageIsKeptForTheUser() = runTest {
        val result = engine(FakeSink()).backfill(FakeSource { _, _ -> true }, fromMillis = now - 75 * day)

        assertEquals("IllegalStateException: huawei read failed", result.firstError)
    }

    @Test
    fun emptyWindowsDoNotCallTheSink() = runTest {
        val sink = FakeSink()

        engine(sink).syncRecent(FakeSource())

        assertEquals(0, sink.batches.size)
    }

    @Test
    fun cancellationIsNotSwallowedAsAFailedWindow() = runTest {
        val source = BodySource { _, _ -> throw CancellationException("cancelled") }
        try {
            engine(FakeSink()).syncRecent(source)
            fail("expected CancellationException")
        } catch (expected: CancellationException) {
            // ok
        }
    }

    @Test
    fun onlyAllowedMetricsAreWritten() = runTest {
        val sink = FakeSink()

        engine(sink, metrics = setOf(Metric.WEIGHT)).syncRecent(FakeSource(listOf(weight(now - day), fat(now - day))))

        assertEquals(listOf(weight(now - day)), sink.written)
    }

    @Test
    fun implausibleValuesAreCountedAsSkippedAndNotSentToTheSink() = runTest {
        val sink = FakeSink()

        val result = engine(sink).import(listOf(weight(now - day), weight(now - 2 * day, kg = 0.0)))

        assertEquals(listOf(weight(now - day)), sink.written)
        assertEquals(1, result.written)
        assertEquals(1, result.skipped)
    }

    @Test
    fun importWritesInChunksWithTheImportTimeAsVersion() = runTest {
        val sink = FakeSink()
        val samples = (1..5).map { weight(now - it * day) }

        val result = engine(sink, chunkSize = 2).import(samples)

        assertEquals(listOf(2, 2, 1), sink.batches.map { it.size })
        assertEquals(listOf(now, now, now), sink.versions)
        assertEquals(5, result.written)
    }

    @Test
    fun aFailingImportChunkIsReportedAndTheOthersStillWrite() = runTest {
        val bad = weight(now - 3 * day)
        val sink = FakeSink { bad in it }
        val samples = (1..5).map { weight(now - it * day) }

        val result = engine(sink, chunkSize = 2).import(samples)

        assertEquals(3, result.written)
        assertEquals(1, result.failedWindows.size)
        assertEquals("IllegalStateException: health connect write failed", result.firstError)
    }

    @Test
    fun latestHoldsTheNewestWrittenSamplePerMetric() = runTest {
        val result = engine(FakeSink()).import(listOf(weight(now - 2 * day, 71.0), weight(now - day, 72.0), fat(now - 5 * day)))

        assertEquals(mapOf(Metric.WEIGHT to weight(now - day, 72.0), Metric.BODY_FAT to fat(now - 5 * day)), result.latest)
    }
}
