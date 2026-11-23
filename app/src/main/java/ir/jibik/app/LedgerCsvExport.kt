package ir.jibik.app

import ir.jibik.core.finance.Account
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A read-only, explicitly user-requested ledger export. No network, temporary
 * world-readable file or storage permission is required. Amounts are integers
 * in TOMAN (the current Android ledger contract), never formatted decimals.
 *
 * CSV is not a restorable backup; account and transaction IDs are included so
 * the owner can reconcile rows without silently deduplicating different records.
 */
internal object LedgerCsvExport {
    private val timestampFormat = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        .withZone(ZoneOffset.UTC)

    private fun csvCell(value: String): String {
        // Spreadsheet formula injection must not execute on opening a CSV.
        // Leading spaces and control chars cannot hide a formula prefix.
        val dangerous = value.trimStart().firstOrNull() in setOf('=', '+', '-', '@')
        val escaped = if (dangerous) "'" + value else value
        return "\"" + escaped.replace("\"", "\"\"")
            .replace("\r\n", "\n").replace('\r', '\n') + "\""
    }

    private fun row(cells: List<String>): String = cells.joinToString(",") { csvCell(it) } + "\r\n"

    fun write(
        stream: OutputStream,
        accounts: List<Account>,
        records: List<LedgerRecord>,
    ) {
        // A UTF-8 BOM makes Persian text readable in common spreadsheet apps.
        stream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        val writer = stream.writer(StandardCharsets.UTF_8)
        writer.write(row(listOf(
            "transaction_id", "timestamp_utc", "type", "amount_toman",
            "source_account_id", "source_account", "destination_account_id",
            "destination_account", "category", "note",
        )))
        val names = accounts.associate { it.id to it.title }
        records.sortedWith(compareBy<LedgerRecord> { it.timestampMillis }.thenBy { it.id })
            .forEach { record ->
                val type = when (record.kind) {
                    LedgerKind.EXPENSE -> "expense"
                    LedgerKind.INCOME -> "income"
                    LedgerKind.TRANSFER -> "transfer"
                }
                writer.write(row(listOf(
                    record.id,
                    timestampFormat.format(Instant.ofEpochMilli(record.timestampMillis)),
                    type,
                    record.amountToman.toString(),
                    record.fromAccountId.orEmpty(),
                    names[record.fromAccountId].orEmpty(),
                    record.toAccountId.orEmpty(),
                    names[record.toAccountId].orEmpty(),
                    record.category,
                    record.note,
                )))
            }
        writer.flush()
    }
}
