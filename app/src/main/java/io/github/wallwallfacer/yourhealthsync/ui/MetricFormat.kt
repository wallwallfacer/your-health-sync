package io.github.wallwallfacer.yourhealthsync.ui

import io.github.wallwallfacer.yourhealthsync.model.Metric
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How metrics are named and shown on screen. */
object MetricFormat {
    fun label(metric: Metric): String = when (metric) {
        Metric.WEIGHT -> "Weight"
        Metric.BODY_FAT -> "Body fat"
        Metric.BASAL_METABOLIC_RATE -> "Basal metabolic rate"
        Metric.BONE_MASS -> "Bone mass"
        Metric.BODY_WATER_MASS -> "Body water"
    }

    fun value(metric: Metric, value: Double, locale: Locale = Locale.getDefault()): String = when (metric) {
        Metric.BODY_FAT -> String.format(locale, "%.1f %%", value)
        Metric.BASAL_METABOLIC_RATE -> String.format(locale, "%.0f kcal", value)
        else -> String.format(locale, "%.1f kg", value)
    }

    fun time(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("d MMM HH:mm", locale).format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** Samsung Health reads these from Health Connect; the others stay in Health Connect only. */
    fun shownInSamsungHealth(metric: Metric): Boolean =
        metric == Metric.WEIGHT || metric == Metric.BODY_FAT || metric == Metric.BASAL_METABOLIC_RATE
}
