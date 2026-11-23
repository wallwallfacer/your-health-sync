package io.github.wallwallfacer.yourhealthsync.csv

import io.github.wallwallfacer.yourhealthsync.model.BodySample
import io.github.wallwallfacer.yourhealthsync.model.Metric
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** A data row the parser could not read. [line] is 1-based, counting the header. */
data class RejectedRow(val line: Int, val reason: String)

data class CsvParseResult(val samples: List<BodySample>, val rejected: List<RejectedRow>)

/**
 * Reads body measurements from the app's CSV format:
 *
 *     time,weight_kg,body_fat_pct,bmr_kcal,bone_mass_kg,body_water_kg
 *     2026-11-21T08:12:00+08:00,72.4,18.2,1650,3.1,40.2
 *
 * Only `time` is required; it is ISO-8601, with an offset or in local time
 * (read in [zone]), and a space may replace the "T". Columns may come in any
 * order, unknown columns and empty cells are ignored, and quoted cells,
 * a byte-order mark and CRLF line endings are accepted. A row that cannot be
 * read is reported in [CsvParseResult.rejected]; the other rows still count.
 */
object CsvParser {
    const val HEADER = "time,weight_kg,body_fat_pct,bmr_kcal,bone_mass_kg,body_water_kg"

    private val metricByColumn = mapOf(
        "weight_kg" to Metric.WEIGHT,
        "body_fat_pct" to Metric.BODY_FAT,
        "bmr_kcal" to Metric.BASAL_METABOLIC_RATE,
        "bone_mass_kg" to Metric.BONE_MASS,
        "body_water_kg" to Metric.BODY_WATER_MASS,
    )
    private val earliestMillis = Instant.parse("2000-01-01T00:00:00Z").toEpochMilli()
    private val maxAheadMillis = TimeUnit.DAYS.toMillis(1)

    /** Throws [IllegalArgumentException] when the file is empty or its header has no `time` column. */
    fun parse(text: String, zone: ZoneId, nowMillis: Long): CsvParseResult {
        val lines = text.removePrefix("\uFEFF").lines()
        val headerIndex = lines.indexOfFirst { it.isNotBlank() }
        require(headerIndex >= 0) { "The file is empty" }
        val header = cells(lines[headerIndex]).map { it.lowercase() }
        val timeColumn = header.indexOf("time")
        require(timeColumn >= 0) { "The header has no 'time' column. Expected: $HEADER" }
        val metricColumns = header.withIndex().mapNotNull { (i, name) -> metricByColumn[name]?.let { Triple(i, name, it) } }

        val samples = mutableListOf<BodySample>()
        val rejected = mutableListOf<RejectedRow>()
        for (i in headerIndex + 1 until lines.size) {
            if (lines[i].isBlank()) continue
            val lineNumber = i + 1
            val row = cells(lines[i])
            if (row.size > header.size) {
                rejected += RejectedRow(lineNumber, "has ${row.size} cells but the header has ${header.size}")
                continue
            }
            val rawTime = row.getOrNull(timeColumn).orEmpty()
            val time = parseTime(rawTime, zone)
            if (time == null) {
                rejected += RejectedRow(lineNumber, "cannot read time '$rawTime'")
                continue
            }
            if (time < earliestMillis || time > nowMillis + maxAheadMillis) {
                rejected += RejectedRow(lineNumber, "time '$rawTime' is before 2000 or in the future")
                continue
            }
            val rowSamples = mutableListOf<BodySample>()
            var problem: String? = null
            for ((column, name, metric) in metricColumns) {
                val raw = row.getOrNull(column).orEmpty()
                if (raw.isEmpty()) continue
                val value = raw.toDoubleOrNull()
                if (value == null) {
                    problem = "cannot read $name '$raw'"
                    break
                }
                rowSamples += BodySample(metric, time, value)
            }
            if (problem != null) rejected += RejectedRow(lineNumber, problem) else samples += rowSamples
        }
        return CsvParseResult(samples, rejected)
    }

    private fun cells(line: String): List<String> = line.split(',').map { it.trim().removeSurrounding("\"").trim() }

    private fun parseTime(raw: String, zone: ZoneId): Long? {
        if (raw.isEmpty()) return null
        val iso = raw.replace(' ', 'T')
        return runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(iso).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    }
}
