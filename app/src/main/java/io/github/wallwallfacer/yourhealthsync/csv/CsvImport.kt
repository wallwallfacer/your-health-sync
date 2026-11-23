package io.github.wallwallfacer.yourhealthsync.csv

import android.content.Context
import android.net.Uri
import io.github.wallwallfacer.yourhealthsync.sync.SyncResult
import io.github.wallwallfacer.yourhealthsync.sync.Syncer
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads a CSV file the user picked and writes it to Health Connect. Needs no HMS. */
object CsvImport {
    /** MIME types offered by the file picker; providers label CSV files inconsistently. */
    val MIME_TYPES = arrayOf(
        "text/csv",
        "text/comma-separated-values",
        "application/csv",
        "text/plain",
        "application/vnd.ms-excel",
        "*/*",
    )

    data class Outcome(val parse: CsvParseResult, val result: SyncResult) {
        val summary: String get() = ImportReport.describe(parse, result)
    }

    /** Throws when the file cannot be read or its header is not the app's format. */
    suspend fun run(context: Context, uri: Uri): Outcome {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("Cannot open the file")
        }
        val parse = withContext(Dispatchers.Default) {
            CsvParser.parse(text, ZoneId.systemDefault(), System.currentTimeMillis())
        }
        return Outcome(parse, Syncer.engine(context).import(parse.samples))
    }
}
