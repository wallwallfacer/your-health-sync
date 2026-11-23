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
import io.github.wallwallfacer.yourhealthsync.sync.BodySink
import io.github.wallwallfacer.yourhealthsync.sync.RecordMapper
import java.time.ZoneId

/** Writes samples to Health Connect. Records upsert by client record id, so rewrites are harmless. */
class HealthConnectSink(context: Context) : BodySink {
    private val client = HealthConnectClient.getOrCreate(context.applicationContext)

    override suspend fun write(samples: List<BodySample>): Int {
        val zone = ZoneId.systemDefault()
        val records = samples.mapNotNull { RecordMapper.toRecord(it, zone) }
        if (records.isEmpty()) return 0
        client.insertRecords(records)
        return records.size
    }

    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getWritePermission(WeightRecord::class),
            HealthPermission.getWritePermission(BodyFatRecord::class),
            HealthPermission.getWritePermission(BasalMetabolicRateRecord::class),
            HealthPermission.getWritePermission(BoneMassRecord::class),
            HealthPermission.getWritePermission(BodyWaterMassRecord::class),
        )

        fun isAvailable(context: Context): Boolean =
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

        suspend fun hasPermissions(context: Context): Boolean =
            HealthConnectClient.getOrCreate(context).permissionController
                .getGrantedPermissions().containsAll(PERMISSIONS)
    }
}
