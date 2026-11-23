package io.github.wallwallfacer.yourhealthsync.csv

import io.github.wallwallfacer.yourhealthsync.sync.SyncResult

/** One-line summary of a CSV import, shown to the user. */
object ImportReport {
    private const val MAX_LISTED_ROWS = 5

    fun describe(parse: CsvParseResult, result: SyncResult): String {
        val parts = mutableListOf("Imported ${result.written} records")
        if (parse.rejected.isNotEmpty()) {
            val listed = parse.rejected.take(MAX_LISTED_ROWS).joinToString("; ") { "line ${it.line}: ${it.reason}" }
            val more = if (parse.rejected.size > MAX_LISTED_ROWS) "; …" else ""
            parts += "${parse.rejected.size} ${plural(parse.rejected.size, "row")} rejected ($listed$more)"
        }
        if (result.skipped > 0) {
            parts += "${result.skipped} ${plural(result.skipped, "value")} skipped as implausible"
        }
        if (result.failedWindows.isNotEmpty()) {
            val n = result.failedWindows.size
            parts += "$n ${if (n == 1) "batch" else "batches"} failed (${result.firstError})"
        }
        return parts.joinToString(" · ")
    }

    private fun plural(n: Int, word: String) = if (n == 1) word else word + "s"
}
