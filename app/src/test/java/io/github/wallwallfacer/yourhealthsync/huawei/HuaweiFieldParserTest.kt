package io.github.wallwallfacer.yourhealthsync.huawei

import io.github.wallwallfacer.yourhealthsync.model.BodySample
import io.github.wallwallfacer.yourhealthsync.model.Metric
import org.junit.Assert.assertEquals
import org.junit.Test

class HuaweiFieldParserTest {
    private val t = 1_790_000_000_000L

    @Test
    fun fullScaleReadingBecomesOneSamplePerSupportedMetric() {
        val fields = mapOf(
            "body_weight" to 72.4,
            "body_fat_rate" to 21.5,
            "basal_metabolism" to 1580.0,
            "bone_salt" to 3.1,
            "moisture" to 40.2,
        )

        assertEquals(
            setOf(
                BodySample(Metric.WEIGHT, t, 72.4),
                BodySample(Metric.BODY_FAT, t, 21.5),
                BodySample(Metric.BASAL_METABOLIC_RATE, t, 1580.0),
                BodySample(Metric.BONE_MASS, t, 3.1),
                BodySample(Metric.BODY_WATER_MASS, t, 40.2),
            ),
            HuaweiFieldParser.parse(t, fields).toSet(),
        )
    }

    @Test
    fun fieldsWithoutAHealthConnectTypeAreIgnored() {
        val fields = mapOf("body_weight" to 72.4, "bmi" to 23.1, "visceral_fat_level" to 7.0, "muscle_mass" to 52.0)

        assertEquals(listOf(BodySample(Metric.WEIGHT, t, 72.4)), HuaweiFieldParser.parse(t, fields))
    }

    @Test
    fun weightOnlyReadingFromManualEntryGivesOnlyWeight() {
        assertEquals(listOf(BodySample(Metric.WEIGHT, t, 80.0)), HuaweiFieldParser.parse(t, mapOf("body_weight" to 80.0)))
    }
}
