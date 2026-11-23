package io.github.wallwallfacer.yourhealthsync.sync

import io.github.wallwallfacer.yourhealthsync.model.BodySample
import io.github.wallwallfacer.yourhealthsync.model.Metric
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/** Where samples come from over time (HUAWEI Health Kit in production). End is exclusive. */
fun interface BodySource {
    suspend fun read(startMillis: Long, endMillis: Long): List<BodySample>
}

/** Where samples go (Health Connect in production). Returns how many were written. */
fun interface BodySink {
    suspend fun write(samples: List<BodySample>, version: Long): Int
}

data class TimeWindow(val startMillis: Long, val endMillis: Long)

/**
 * Outcome of one run. [skipped] counts implausible values that were not written.
 * [failedWindows] are read windows (sync) or write batches (import) that failed;
 * [firstError] is the first failure's "Type: message", shown to the user.
 * [latest] is the newest written sample per metric.
 */
data class SyncResult(
    val written: Int = 0,
    val skipped: Int = 0,
    val failedWindows: List<TimeWindow> = emptyList(),
    val firstError: String? = null,
    val latest: Map<Metric, BodySample> = emptyMap(),
)

/**
 * Writes samples to [sink], keeping only the metrics [metrics] allows
 * (enabled and granted) and only plausible values.
 *
 * There is no cursor: the sink upserts by a stable client record id, so
 * re-reading a window or re-importing a file is harmless.
 */
class SyncEngine(
    private val sink: BodySink,
    private val now: () -> Long = System::currentTimeMillis,
    private val metrics: suspend () -> Set<Metric> = { Metric.entries.toSet() },
    private val chunkSize: Int = MAX_RECORDS_PER_WRITE,
) {
    /** Re-reads the trailing [RECENT_WINDOW_MILLIS]. Used by the periodic job and "Sync now". */
    suspend fun syncRecent(source: BodySource): SyncResult {
        val end = now()
        return sync(source, end - RECENT_WINDOW_MILLIS, end)
    }

    /** One-off read of everything from [fromMillis] until now. */
    suspend fun backfill(source: BodySource, fromMillis: Long): SyncResult = sync(source, fromMillis, now())

    /** Writes already-loaded samples (a CSV file), versioned with the import time. */
    suspend fun import(samples: List<BodySample>): SyncResult {
        val run = Run(metrics(), version = now())
        for (chunk in run.keep(samples).chunked(chunkSize)) {
            try {
                run.write(chunk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                run.fail(TimeWindow(chunk.minOf { it.epochMillis }, chunk.maxOf { it.epochMillis } + 1), e)
            }
        }
        return run.result()
    }

    private suspend fun sync(source: BodySource, startMillis: Long, endMillis: Long): SyncResult {
        val run = Run(metrics(), version = RecordMapper.MAPPING_VERSION)
        for (window in windows(startMillis, endMillis)) {
            try {
                run.keep(source.read(window.startMillis, window.endMillis)).chunked(chunkSize).forEach { run.write(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                run.fail(window, e)
            }
        }
        return run.result()
    }

    private fun windows(startMillis: Long, endMillis: Long): List<TimeWindow> =
        (startMillis until endMillis step MAX_WINDOW_MILLIS).map { start ->
            TimeWindow(start, minOf(start + MAX_WINDOW_MILLIS, endMillis))
        }

    /** Tallies one sync or import. */
    private inner class Run(private val allowed: Set<Metric>, private val version: Long) {
        private var written = 0
        private var skipped = 0
        private val failed = mutableListOf<TimeWindow>()
        private var firstError: String? = null
        private val latest = mutableMapOf<Metric, BodySample>()

        fun keep(samples: List<BodySample>): List<BodySample> {
            val (plausible, implausible) = samples.filter { it.metric in allowed }.partition(RecordMapper::isPlausible)
            skipped += implausible.size
            return plausible
        }

        suspend fun write(chunk: List<BodySample>) {
            written += sink.write(chunk, version)
            for (s in chunk) latest.merge(s.metric, s) { old, new -> if (new.epochMillis >= old.epochMillis) new else old }
        }

        fun fail(window: TimeWindow, e: Exception) {
            failed += window
            if (firstError == null) firstError = "${e::class.simpleName}: ${e.message}"
        }

        fun result() = SyncResult(written, skipped, failed.toList(), firstError, latest.toMap())
    }

    companion object {
        val RECENT_WINDOW_MILLIS = TimeUnit.DAYS.toMillis(30)

        /** HUAWEI limits how long one query may span; 30 days stays within it. */
        val MAX_WINDOW_MILLIS = TimeUnit.DAYS.toMillis(30)

        /** Records per Health Connect insertRecords call. */
        const val MAX_RECORDS_PER_WRITE = 1000
    }
}
