package io.github.wallwallfacer.yourhealthsync.sync

import android.content.Context
import io.github.wallwallfacer.yourhealthsync.model.BodySample
import io.github.wallwallfacer.yourhealthsync.model.Metric

/** The last run's outcome. [at] is 0 when the app has never synced. */
data class LastRun(val at: Long, val written: Int, val failed: Int, val error: String?)

/** Newest synced sample per metric plus the last run, kept for the dashboard. */
object MetricStatus {
    private const val PREFS = "metric-status"

    /** Keeps, per metric, whichever of [stored] and [fresh] is newer. */
    fun merge(stored: Map<Metric, BodySample>, fresh: Map<Metric, BodySample>): Map<Metric, BodySample> =
        (stored.keys + fresh.keys).associateWith { metric ->
            listOfNotNull(stored[metric], fresh[metric]).maxBy { it.epochMillis }
        }

    fun save(context: Context, result: SyncResult, now: Long = System.currentTimeMillis()) {
        val latest = merge(latest(context), result.latest)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putLong("run_at", now)
            putInt("run_written", result.written)
            putInt("run_failed", result.failedWindows.size)
            putString("run_error", result.firstError)
            for ((metric, s) in latest) {
                putLong("t_${metric.id}", s.epochMillis)
                putString("v_${metric.id}", s.value.toString())
            }
        }.apply()
    }

    fun latest(context: Context): Map<Metric, BodySample> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Metric.entries.mapNotNull { metric ->
            val value = p.getString("v_${metric.id}", null)?.toDoubleOrNull() ?: return@mapNotNull null
            metric to BodySample(metric, p.getLong("t_${metric.id}", 0L), value)
        }.toMap()
    }

    fun lastRun(context: Context): LastRun {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return LastRun(p.getLong("run_at", 0L), p.getInt("run_written", 0), p.getInt("run_failed", 0), p.getString("run_error", null))
    }
}
