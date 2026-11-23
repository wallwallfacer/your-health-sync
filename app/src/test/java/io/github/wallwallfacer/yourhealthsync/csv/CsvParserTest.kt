package io.github.wallwallfacer.yourhealthsync.csv

import io.github.wallwallfacer.yourhealthsync.model.BodySample
import io.github.wallwallfacer.yourhealthsync.model.Metric
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvParserTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val now = Instant.parse("2026-11-23T00:00:00Z").toEpochMilli()
    private fun parse(text: String) = CsvParser.parse(text, zone, now)
    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun local(iso: String) = LocalDateTime.parse(iso).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun everyColumnBecomesASampleOfItsMetric() {
        val result = parse(
            "time,weight_kg,body_fat_pct,bmr_kcal,bone_mass_kg,body_water_kg\n" +
                "2026-11-21T08:12:00+08:00,72.4,18.2,1650,3.1,40.2\n",
        )

        val t = millis("2026-11-21T00:12:00Z")
        assertEquals(
            listOf(
                BodySample(Metric.WEIGHT, t, 72.4),
                BodySample(Metric.BODY_FAT, t, 18.2),
                BodySample(Metric.BASAL_METABOLIC_RATE, t, 1650.0),
                BodySample(Metric.BONE_MASS, t, 3.1),
                BodySample(Metric.BODY_WATER_MASS, t, 40.2),
            ),
            result.samples,
        )
        assertEquals(emptyList<RejectedRow>(), result.rejected)
    }

    @Test
    fun timesWithoutAnOffsetAreReadInTheGivenZone() {
        val result = parse("time,weight_kg\n2026-11-21T08:12,70\n2026-11-20 07:05:30,71\n")

        assertEquals(listOf(local("2026-11-21T08:12"), local("2026-11-20T07:05:30")), result.samples.map { it.epochMillis })
    }

    @Test
    fun emptyCellsAreSkippedAndOnlyTimeIsRequired() {
        val result = parse("time,weight_kg,body_fat_pct\n2026-11-21T08:12,,18.2\n2026-11-22T08:12\n")

        assertEquals(listOf(BodySample(Metric.BODY_FAT, local("2026-11-21T08:12"), 18.2)), result.samples)
        assertEquals(emptyList<RejectedRow>(), result.rejected)
    }

    @Test
    fun columnsMayBeReorderedUnknownOnesIgnoredAndHeaderCaseIsFree() {
        val result = parse("Weight_KG,note,TIME\n72.4,after run,2026-11-21T08:12\n")

        assertEquals(listOf(BodySample(Metric.WEIGHT, local("2026-11-21T08:12"), 72.4)), result.samples)
    }

    @Test
    fun byteOrderMarkCrlfQuotesAndBlankLinesAreTolerated() {
        val result = parse("\uFEFFtime,weight_kg\r\n\r\n\"2026-11-21 08:12\",\"72.4\"\r\n")

        assertEquals(listOf(BodySample(Metric.WEIGHT, local("2026-11-21T08:12"), 72.4)), result.samples)
    }

    @Test
    fun badRowsAreRejectedWithTheirLineNumberAndOtherRowsStillParse() {
        val result = parse(
            "time,weight_kg\n" +
                "2026-11-19T08:00,70\n" +
                "yesterday,71\n" +
                "2026-11-21T08:00,72,5\n" +
                "2026-11-22T08:00,7O\n",
        )

        assertEquals(listOf(70.0), result.samples.map { it.value })
        assertEquals(listOf(3, 4, 5), result.rejected.map { it.line })
        assertTrue(result.rejected[0].reason.contains("time"))
        // A decimal comma splits the row, so it is rejected rather than read as 72.
        assertTrue(result.rejected[1].reason.contains("cells"))
        assertTrue(result.rejected[2].reason.contains("weight_kg"))
    }

    @Test
    fun implausibleTimesAreRejected() {
        val result = parse("time,weight_kg\n1999-12-31T23:00,70\n2026-11-25T08:00,70\n2026-11-23T08:00,70\n")

        assertEquals(listOf(2, 3), result.rejected.map { it.line })
        assertEquals(1, result.samples.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aHeaderWithoutTimeIsRefused() {
        parse("date,weight_kg\n2026-11-21,70\n")
    }

    @Test(expected = IllegalArgumentException::class)
    fun anEmptyFileIsRefused() {
        parse("")
    }
}
