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
     * clientRecordVersion of records read from HUAWEI Health Kit. Health Connect
     * overwrites a record only when the new version is higher, so bump this
     * whenever the mapping changes. CSV imports use the import time instead,
     * so a corrected file overwrites what an earlier import wrote.
     */
    const val MAPPING_VERSION = 1L

    private val scale = Device(type = Device.TYPE_SCALE)

    /**
     * Independent of the source and rounded down to the minute, so the same
     * weigh-in arriving from a CSV file and from HUAWEI Health Kit upserts one record.
     */
    fun clientRecordId(sample: BodySample): String =
        "body:${sample.metric.id}:${sample.epochMillis - Math.floorMod(sample.epochMillis, MINUTE_MILLIS)}"

    fun isPlausible(sample: BodySample): Boolean =
        sample.value.isFinite() && sample.value > 0.0 && sample.value <= maxValue(sample.metric)

    /** Returns null for values that are not plausible measurements. */
    fun toRecord(sample: BodySample, zone: ZoneId, version: Long): Record? {
        if (!isPlausible(sample)) return null
        val v = sample.value

        val time = Instant.ofEpochMilli(sample.epochMillis)
        val offset = zone.rules.getOffset(time)
        val metadata = Metadata.autoRecorded(
            device = scale,
            clientRecordId = clientRecordId(sample),
            clientRecordVersion = version,
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

    private const val MINUTE_MILLIS = 60_000L

    // Upper bounds match Health Connect's own validation limits.
    private fun maxValue(metric: Metric): Double = when (metric) {
        Metric.BODY_FAT -> 100.0
        Metric.BASAL_METABOLIC_RATE -> 10_000.0
        else -> 1_000.0
    }
}
