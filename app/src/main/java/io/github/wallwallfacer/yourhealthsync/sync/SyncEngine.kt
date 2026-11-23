package io.github.wallwallfacer.yourhealthsync.sync

import io.github.wallwallfacer.yourhealthsync.model.BodySample
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/** Where samples come from (Huawei Health Kit in production). End is exclusive. */
fun interface BodySource {
    suspend fun read(startMillis: Long, endMillis: Long): List<BodySample>
}

/** Where samples go (Health Connect in production). Returns how many were written. */
fun interface BodySink {
    suspend fun write(samples: List<BodySample>): Int
}

data class TimeWindow(val startMillis: Long, val endMillis: Long)

/** [firstError] is the first failure's "Type: message", shown to the user; null when nothing failed. */
data class SyncResult(val written: Int, val failedWindows: List<TimeWindow>, val firstError: String? = null)

/**
 * Copies samples from [source] to [sink] window by window.
 *
 * There is no cursor: the sink upserts by a stable client record id, so
 * re-reading a window is harmless and catches data that arrived late.
 */
class SyncEngine(
    private val source: BodySource,
    private val sink: BodySink,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Re-reads the trailing [RECENT_WINDOW_MILLIS]. Used by the periodic job and "Sync now". */
    suspend fun syncRecent(): SyncResult {
        val end = now()
        return sync(end - RECENT_WINDOW_MILLIS, end)
    }

    /** One-off import of everything from [fromMillis] until now. */
    suspend fun backfill(fromMillis: Long): SyncResult = sync(fromMillis, now())

    private suspend fun sync(startMillis: Long, endMillis: Long): SyncResult {
        var written = 0
        val failed = mutableListOf<TimeWindow>()
        var firstError: String? = null
        for (window in chunks(startMillis, endMillis)) {
            try {
                val samples = source.read(window.startMillis, window.endMillis)
                if (samples.isNotEmpty()) written += sink.write(samples)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += window
                if (firstError == null) firstError = "${e::class.simpleName}: ${e.message}"
            }
        }
        return SyncResult(written, failed, firstError)
    }

    private fun chunks(startMillis: Long, endMillis: Long): List<TimeWindow> =
        (startMillis until endMillis step MAX_CHUNK_MILLIS).map { start ->
            TimeWindow(start, minOf(start + MAX_CHUNK_MILLIS, endMillis))
        }

    companion object {
        val RECENT_WINDOW_MILLIS = TimeUnit.DAYS.toMillis(30)

        /** Huawei limits how long one query may span; 30 days stays within it. */
        val MAX_CHUNK_MILLIS = TimeUnit.DAYS.toMillis(30)
    }
}
