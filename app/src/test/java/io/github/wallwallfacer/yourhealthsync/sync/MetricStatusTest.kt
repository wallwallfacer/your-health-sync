package io.github.wallwallfacer.yourhealthsync.sync

import io.github.wallwallfacer.yourhealthsync.model.BodySample
import io.github.wallwallfacer.yourhealthsync.model.Metric
import org.junit.Assert.assertEquals
import org.junit.Test

class MetricStatusTest {
    private fun weight(at: Long, kg: Double) = BodySample(Metric.WEIGHT, at, kg)
    private fun fat(at: Long) = BodySample(Metric.BODY_FAT, at, 20.0)

    @Test
    fun aNewerSampleReplacesTheStoredOne() {
        assertEquals(
            mapOf(Metric.WEIGHT to weight(2, 71.0)),
            MetricStatus.merge(mapOf(Metric.WEIGHT to weight(1, 70.0)), mapOf(Metric.WEIGHT to weight(2, 71.0))),
        )
    }

    @Test
    fun anOlderSampleFromABackfillDoesNotReplaceTheStoredOne() {
        assertEquals(
            mapOf(Metric.WEIGHT to weight(5, 70.0)),
            MetricStatus.merge(mapOf(Metric.WEIGHT to weight(5, 70.0)), mapOf(Metric.WEIGHT to weight(1, 60.0))),
        )
    }

    @Test
    fun metricsMissingFromTheRunAreKept() {
        assertEquals(
            mapOf(Metric.WEIGHT to weight(5, 70.0), Metric.BODY_FAT to fat(3)),
            MetricStatus.merge(mapOf(Metric.WEIGHT to weight(5, 70.0)), mapOf(Metric.BODY_FAT to fat(3))),
        )
    }
}
