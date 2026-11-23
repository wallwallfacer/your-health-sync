package io.github.wallwallfacer.yourhealthsync.ui

import io.github.wallwallfacer.yourhealthsync.model.Metric
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricFormatTest {
    @Test
    fun valuesCarryTheirUnitWithSensiblePrecision() {
        assertEquals("72.4 kg", MetricFormat.value(Metric.WEIGHT, 72.4, Locale.ENGLISH))
        assertEquals("18.2 %", MetricFormat.value(Metric.BODY_FAT, 18.24, Locale.ENGLISH))
        assertEquals("1650 kcal", MetricFormat.value(Metric.BASAL_METABOLIC_RATE, 1649.6, Locale.ENGLISH))
        assertEquals("3.1 kg", MetricFormat.value(Metric.BONE_MASS, 3.1, Locale.ENGLISH))
        assertEquals("40.0 kg", MetricFormat.value(Metric.BODY_WATER_MASS, 40.0, Locale.ENGLISH))
    }

    @Test
    fun timeIsShortDayMonthAndClockInTheGivenZone() {
        val t = Instant.parse("2026-11-21T00:12:00Z").toEpochMilli()

        assertEquals("21 Nov 08:12", MetricFormat.time(t, ZoneId.of("Asia/Singapore"), Locale.ENGLISH))
    }

    @Test
    fun onlyWeightFatAndBmrShowUpInSamsungHealth() {
        assertTrue(MetricFormat.shownInSamsungHealth(Metric.WEIGHT))
        assertTrue(MetricFormat.shownInSamsungHealth(Metric.BODY_FAT))
        assertTrue(MetricFormat.shownInSamsungHealth(Metric.BASAL_METABOLIC_RATE))
        assertFalse(MetricFormat.shownInSamsungHealth(Metric.BONE_MASS))
        assertFalse(MetricFormat.shownInSamsungHealth(Metric.BODY_WATER_MASS))
    }
}
