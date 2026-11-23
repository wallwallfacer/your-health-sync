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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordMapperTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val winterMillis = Instant.parse("2026-01-15T07:30:00Z").toEpochMilli()
    private val summerMillis = Instant.parse("2026-07-15T07:30:00Z").toEpochMilli()

    @Test
    fun weightSampleBecomesWeightRecordInKilograms() {
        val record = RecordMapper.toRecord(BodySample(Metric.WEIGHT, winterMillis, 72.4), berlin, version = 1L)

        assertTrue(record is WeightRecord)
        record as WeightRecord
        assertEquals(72.4, record.weight.inKilograms, 1e-9)
        assertEquals(Instant.ofEpochMilli(winterMillis), record.time)
    }

    @Test
    fun recordCarriesASourceAgnosticClientIdAndTheGivenVersion() {
        val record = RecordMapper.toRecord(BodySample(Metric.WEIGHT, winterMillis, 72.4), berlin, version = 42L)!!

        assertEquals("body:weight:$winterMillis", record.metadata.clientRecordId)
        assertEquals(42L, record.metadata.clientRecordVersion)
    }

    @Test
    fun clientIdIsTheSameWithinOneMinuteSoSourcesDeduplicate() {
        val fromHuawei = BodySample(Metric.WEIGHT, winterMillis + 42_123, 72.4)
        val fromCsv = BodySample(Metric.WEIGHT, winterMillis, 72.4)

        assertEquals(RecordMapper.clientRecordId(fromCsv), RecordMapper.clientRecordId(fromHuawei))
        assertEquals(
            Instant.ofEpochMilli(winterMillis + 42_123),
            (RecordMapper.toRecord(fromHuawei, berlin, version = 1L) as WeightRecord).time,
        )
    }

    @Test
    fun plausibilityMatchesWhatTheMapperAccepts() {
        assertTrue(RecordMapper.isPlausible(BodySample(Metric.WEIGHT, winterMillis, 72.4)))
        assertFalse(RecordMapper.isPlausible(BodySample(Metric.BODY_FAT, winterMillis, 150.0)))
    }

    @Test
    fun zoneOffsetIsTheZonesOffsetAtTheMeasurementInstant() {
        val winter = RecordMapper.toRecord(BodySample(Metric.WEIGHT, winterMillis, 70.0), berlin, version = 1L) as WeightRecord
        val summer = RecordMapper.toRecord(BodySample(Metric.WEIGHT, summerMillis, 70.0), berlin, version = 1L) as WeightRecord

        assertEquals(ZoneOffset.ofHours(1), winter.zoneOffset)
        assertEquals(ZoneOffset.ofHours(2), summer.zoneOffset)
    }

    @Test
    fun bodyFatSampleBecomesBodyFatRecordInPercent() {
        val record = RecordMapper.toRecord(BodySample(Metric.BODY_FAT, winterMillis, 21.5), berlin, version = 1L) as BodyFatRecord

        assertEquals(21.5, record.percentage.value, 1e-9)
        assertEquals("body:body_fat:$winterMillis", record.metadata.clientRecordId)
    }

    @Test
    fun basalMetabolismSampleBecomesBmrRecordInKcalPerDay() {
        val record = RecordMapper.toRecord(BodySample(Metric.BASAL_METABOLIC_RATE, winterMillis, 1580.0), berlin, version = 1L)
            as BasalMetabolicRateRecord

        assertEquals(1580.0, record.basalMetabolicRate.inKilocaloriesPerDay, 1e-6)
    }

    @Test
    fun boneMassSampleBecomesBoneMassRecord() {
        val record = RecordMapper.toRecord(BodySample(Metric.BONE_MASS, winterMillis, 3.1), berlin, version = 1L) as BoneMassRecord

        assertEquals(3.1, record.mass.inKilograms, 1e-9)
    }

    @Test
    fun waterMassSampleBecomesBodyWaterMassRecord() {
        val record = RecordMapper.toRecord(BodySample(Metric.BODY_WATER_MASS, winterMillis, 40.2), berlin, version = 1L)
            as BodyWaterMassRecord

        assertEquals(40.2, record.mass.inKilograms, 1e-9)
    }

    @Test
    fun outOfRangeValuesAreSkippedInsteadOfThrowing() {
        assertNull(RecordMapper.toRecord(BodySample(Metric.WEIGHT, winterMillis, 0.0), berlin, version = 1L))
        assertNull(RecordMapper.toRecord(BodySample(Metric.WEIGHT, winterMillis, -5.0), berlin, version = 1L))
        assertNull(RecordMapper.toRecord(BodySample(Metric.BODY_FAT, winterMillis, 150.0), berlin, version = 1L))
        assertNull(RecordMapper.toRecord(BodySample(Metric.BONE_MASS, winterMillis, Double.NaN), berlin, version = 1L))
    }
}
