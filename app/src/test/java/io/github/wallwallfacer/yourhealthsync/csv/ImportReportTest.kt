package io.github.wallwallfacer.yourhealthsync.csv

import io.github.wallwallfacer.yourhealthsync.sync.SyncResult
import io.github.wallwallfacer.yourhealthsync.sync.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportReportTest {
    @Test
    fun aCleanImportOnlyCountsRecords() {
        assertEquals(
            "Imported 12 records",
            ImportReport.describe(CsvParseResult(emptyList(), emptyList()), SyncResult(written = 12)),
        )
    }

    @Test
    fun everyProblemIsListedWithLineNumbersAndTheFirstError() {
        val parse = CsvParseResult(emptyList(), listOf(RejectedRow(3, "cannot read time 'x'"), RejectedRow(7, "bad")))
        val result = SyncResult(written = 4, skipped = 1, failedWindows = listOf(TimeWindow(0, 1)), firstError = "IOException: boom")

        assertEquals(
            "Imported 4 records · 2 rows rejected (line 3: cannot read time 'x'; line 7: bad) · " +
                "1 value skipped as implausible · 1 batch failed (IOException: boom)",
            ImportReport.describe(parse, result),
        )
    }

    @Test
    fun onlyTheFirstFiveRejectedRowsAreSpelledOut() {
        val parse = CsvParseResult(emptyList(), (2..9).map { RejectedRow(it, "bad") })

        assertEquals(
            "Imported 0 records · 8 rows rejected (line 2: bad; line 3: bad; line 4: bad; line 5: bad; line 6: bad; …)",
            ImportReport.describe(parse, SyncResult()),
        )
    }
}
