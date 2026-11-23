package io.github.wallwallfacer.yourhealthsync.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.wallwallfacer.yourhealthsync.hc.HealthConnectSink
import io.github.wallwallfacer.yourhealthsync.huawei.HuaweiBodySource
import io.github.wallwallfacer.yourhealthsync.model.Metric
import io.github.wallwallfacer.yourhealthsync.settings.AppSettings
import java.util.concurrent.TimeUnit

/** Periodic background sync of the trailing window. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Syncer.allowedMetrics(applicationContext).isEmpty()) return Result.failure()
        val result = Syncer.engine(applicationContext).syncRecent(HuaweiBodySource(applicationContext))
        MetricStatus.save(applicationContext, result)
        return if (result.failedWindows.isEmpty()) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "periodic-body-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Wires the production sink and the metrics it may write. HMS is touched only by the source a caller passes in. */
object Syncer {
    /** Types the user turned on and Health Connect lets this app write. */
    suspend fun allowedMetrics(context: Context): Set<Metric> =
        AppSettings.enabledMetrics(context) intersect HealthConnectSink.grantedMetrics(context)

    fun engine(context: Context) = SyncEngine(HealthConnectSink(context), metrics = { allowedMetrics(context) })
}

/** Last sync outcome, shown in the UI. */
object SyncStatus {
    private const val PREFS = "sync-status"

    fun save(context: Context, result: SyncResult) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("at", System.currentTimeMillis())
            .putInt("written", result.written)
            .putInt("failed", result.failedWindows.size)
            .apply()
    }

    fun describe(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = p.getLong("at", 0L)
        if (at == 0L) return "Never synced"
        val failed = p.getInt("failed", 0)
        val time = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(at))
        return "Last sync: $time, ${p.getInt("written", 0)} records" +
            if (failed > 0) ", $failed window(s) failed" else ""
    }
}
