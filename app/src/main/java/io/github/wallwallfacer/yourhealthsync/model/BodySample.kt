package io.github.wallwallfacer.yourhealthsync.model

/** One body-composition measurement, independent of where it came from. */
data class BodySample(val metric: Metric, val epochMillis: Long, val value: Double)

/**
 * Metrics this app syncs. [id] is part of the Health Connect client record id,
 * so it must never change once released.
 */
enum class Metric(val id: String) {
    WEIGHT("weight"), // kg
    BODY_FAT("body_fat"), // percent
    BASAL_METABOLIC_RATE("bmr"), // kcal per day
    BONE_MASS("bone_mass"), // kg
    BODY_WATER_MASS("body_water_mass"), // kg
}
