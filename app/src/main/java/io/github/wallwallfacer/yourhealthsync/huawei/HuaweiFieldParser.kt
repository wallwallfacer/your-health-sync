package io.github.wallwallfacer.yourhealthsync.huawei

import io.github.wallwallfacer.yourhealthsync.model.BodySample
import io.github.wallwallfacer.yourhealthsync.model.Metric

/**
 * Turns the field values of one Huawei body-composition sample point into
 * [BodySample]s. Keys are Huawei field names (Field.FIELD_*.name in HMS Health).
 * Fields without a Health Connect type (BMI, visceral fat, muscle mass, ...) are ignored.
 */
object HuaweiFieldParser {
    private val metricByField = mapOf(
        "body_weight" to Metric.WEIGHT, // kg
        "body_fat_rate" to Metric.BODY_FAT, // %
        "basal_metabolism" to Metric.BASAL_METABOLIC_RATE, // kcal/day
        "bone_salt" to Metric.BONE_MASS, // kg
        "moisture" to Metric.BODY_WATER_MASS, // kg
    )

    fun parse(epochMillis: Long, fields: Map<String, Double>): List<BodySample> =
        if (epochMillis <= 0) emptyList() else fields.mapNotNull { (name, value) ->
            metricByField[name]?.let { BodySample(it, epochMillis, value) }
        }
}
