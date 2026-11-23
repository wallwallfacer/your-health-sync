package io.github.wallwallfacer.yourhealthsync.sync

import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import io.github.wallwallfacer.yourhealthsync.model.BodySample
import io.github.wallwallfacer.yourhealthsync.model.Metric
import java.time.Instant
import java.time.ZoneId

/** Pure mapping from [BodySample] to Health Connect records. */
object RecordMapper {
    /**
     * Written as clientRecordVersion. Bump it whenever the mapping changes so
     * Health Connect overwrites records written by an older mapping.
     */
    const val MAPPING_VERSION = 1L

    private val scale = Device(type = Device.TYPE_SCALE)

    fun clientRecordId(sample: BodySample): String = "huawei:${sample.metric.id}:${sample.epochMillis}"

    /** Returns null for values that are not plausible measurements. */
    fun toRecord(sample: BodySample, zone: ZoneId): Record? {
        val v = sample.value
        if (!v.isFinite() || v <= 0.0 || v > maxValue(sample.metric)) return null

        val time = Instant.ofEpochMilli(sample.epochMillis)
        val offset = zone.rules.getOffset(time)
        val metadata = Metadata.autoRecorded(
            device = scale,
            clientRecordId = clientRecordId(sample),
            clientRecordVersion = MAPPING_VERSION,
        )
        return when (sample.metric) {
            Metric.WEIGHT -> WeightRecord(time, offset, Mass.kilograms(v), metadata)
            Metric.BODY_FAT -> BodyFatRecord(time, offset, Percentage(v), metadata)
            Metric.BASAL_METABOLIC_RATE ->
                BasalMetabolicRateRecord(time, offset, Power.kilocaloriesPerDay(v), metadata)
            Metric.BONE_MASS -> BoneMassRecord(time, offset, Mass.kilograms(v), metadata)
            Metric.BODY_WATER_MASS -> BodyWaterMassRecord(time, offset, Mass.kilograms(v), metadata)
        }
    }

    // Upper bounds match Health Connect's own validation limits.
    private fun maxValue(metric: Metric): Double = when (metric) {
        Metric.BODY_FAT -> 100.0
        Metric.BASAL_METABOLIC_RATE -> 10_000.0
        else -> 1_000.0
    }
}
