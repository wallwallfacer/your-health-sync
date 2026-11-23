package io.github.wallwallfacer.yourhealthsync.sync

import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.WeightRecord
import io.github.wallwallfacer.yourhealthsync.model.BodySample
import io.github.wallwallfacer.yourhealthsync.model.Metric
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordMapperTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val winterMillis = Instant.parse("2026-01-15T07:30:00Z").toEpochMilli()
    private val summerMillis = Instant.parse("2026-07-15T07:30:00Z").toEpochMilli()

    @Test
    fun weightSampleBecomesWeightRecordInKilograms() {
        val record = RecordMapper.toRecord(BodySample(Metric.WEIGHT, winterMillis, 72.4), berlin)

        assertTrue(record is WeightRecord)
        record as WeightRecord
        assertEquals(72.4, record.weight.inKilograms, 1e-9)
        assertEquals(Instant.ofEpochMilli(winterMillis), record.time)
    }

    @Test
    fun recordCarriesStableClientIdAndMappingVersion() {
        val record = RecordMapper.toRecord(BodySample(Metric.WEIGHT, winterMillis, 72.4), berlin)!!

        assertEquals("huawei:weight:$winterMillis", record.metadata.clientRecordId)
        assertEquals(RecordMapper.MAPPING_VERSION, record.metadata.clientRecordVersion)
    }

    @Test
    fun zoneOffsetIsTheZonesOffsetAtTheMeasurementInstant() {
        val winter = RecordMapper.toRecord(BodySample(Metric.WEIGHT, winterMillis, 70.0), berlin) as WeightRecord
        val summer = RecordMapper.toRecord(BodySample(Metric.WEIGHT, summerMillis, 70.0), berlin) as WeightRecord

        assertEquals(ZoneOffset.ofHours(1), winter.zoneOffset)
        assertEquals(ZoneOffset.ofHours(2), summer.zoneOffset)
    }

    @Test
    fun bodyFatSampleBecomesBodyFatRecordInPercent() {
        val record = RecordMapper.toRecord(BodySample(Metric.BODY_FAT, winterMillis, 21.5), berlin) as BodyFatRecord

        assertEquals(21.5, record.percentage.value, 1e-9)
        assertEquals("huawei:body_fat:$winterMillis", record.metadata.clientRecordId)
    }

    @Test
    fun basalMetabolismSampleBecomesBmrRecordInKcalPerDay() {
        val record = RecordMapper.toRecord(BodySample(Metric.BASAL_METABOLIC_RATE, winterMillis, 1580.0), berlin)
            as BasalMetabolicRateRecord

        assertEquals(1580.0, record.basalMetabolicRate.inKilocaloriesPerDay, 1e-6)
    }

    @Test
    fun boneMassSampleBecomesBoneMassRecord() {
        val record = RecordMapper.toRecord(BodySample(Metric.BONE_MASS, winterMillis, 3.1), berlin) as BoneMassRecord

        assertEquals(3.1, record.mass.inKilograms, 1e-9)
    }

    @Test
    fun waterMassSampleBecomesBodyWaterMassRecord() {
        val record = RecordMapper.toRecord(BodySample(Metric.BODY_WATER_MASS, winterMillis, 40.2), berlin)
            as BodyWaterMassRecord

        assertEquals(40.2, record.mass.inKilograms, 1e-9)
    }

    @Test
    fun outOfRangeValuesAreSkippedInsteadOfThrowing() {
        assertNull(RecordMapper.toRecord(BodySample(Metric.WEIGHT, winterMillis, 0.0), berlin))
        assertNull(RecordMapper.toRecord(BodySample(Metric.WEIGHT, winterMillis, -5.0), berlin))
        assertNull(RecordMapper.toRecord(BodySample(Metric.BODY_FAT, winterMillis, 150.0), berlin))
        assertNull(RecordMapper.toRecord(BodySample(Metric.BONE_MASS, winterMillis, Double.NaN), berlin))
    }
}
