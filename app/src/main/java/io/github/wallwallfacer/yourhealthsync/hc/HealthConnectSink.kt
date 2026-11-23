package io.github.wallwallfacer.yourhealthsync.hc

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.WeightRecord
import io.github.wallwallfacer.yourhealthsync.model.BodySample
import io.github.wallwallfacer.yourhealthsync.model.Metric
import io.github.wallwallfacer.yourhealthsync.sync.BodySink
import io.github.wallwallfacer.yourhealthsync.sync.RecordMapper
import java.time.ZoneId

/** Writes samples to Health Connect. Records upsert by client record id, so rewrites are harmless. */
class HealthConnectSink(context: Context) : BodySink {
    private val client = HealthConnectClient.getOrCreate(context.applicationContext)

    override suspend fun write(samples: List<BodySample>, version: Long): Int {
        val zone = ZoneId.systemDefault()
        val records = samples.mapNotNull { RecordMapper.toRecord(it, zone, version) }
        if (records.isEmpty()) return 0
        client.insertRecords(records)
        return records.size
    }

    companion object {
        fun permissionOf(metric: Metric): String = HealthPermission.getWritePermission(
            when (metric) {
                Metric.WEIGHT -> WeightRecord::class
                Metric.BODY_FAT -> BodyFatRecord::class
                Metric.BASAL_METABOLIC_RATE -> BasalMetabolicRateRecord::class
                Metric.BONE_MASS -> BoneMassRecord::class
                Metric.BODY_WATER_MASS -> BodyWaterMassRecord::class
            },
        )

        fun permissionsFor(metrics: Collection<Metric>): Set<String> = metrics.map(::permissionOf).toSet()

        val PERMISSIONS = permissionsFor(Metric.entries)

        /** Metrics this app may write right now. */
        suspend fun grantedMetrics(context: Context): Set<Metric> {
            val granted = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
            return Metric.entries.filter { permissionOf(it) in granted }.toSet()
        }

        fun isAvailable(context: Context): Boolean =
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

        suspend fun hasPermissions(context: Context): Boolean =
            HealthConnectClient.getOrCreate(context).permissionController
                .getGrantedPermissions().containsAll(PERMISSIONS)
    }
}
