package ir.jibik.app

import androidx.room.withTransaction
import java.io.File
import org.json.JSONObject

/** Test-only staging contract. It never changes the active database or deletes a file. */
internal enum class PilotStage { BEFORE_COPY, AFTER_COPY, AFTER_REOPEN }

internal suspend fun pilotSnapshot(db: JibikDatabase): JSONObject = db.withTransaction {
    FinancialBackup.snapshot(db, db.ledgerDao().budget() ?: 0L)
}

internal fun verifyPilotSnapshot(expected: JSONObject, actual: JSONObject) {
    check(expected.getLong("budget") == actual.getLong("budget")) { "Budget mismatch" }
    FinancialBackup.tables.forEach { table ->
        fun canonicalRows(sheet: JSONObject): List<String> {
            val columns = sheet.getJSONArray("columns")
            val order = (0 until columns.length()).sortedBy { columns.getString(it) }
            val data = sheet.getJSONArray("rows")
            return (0 until data.length()).map { index ->
                val row = data.getJSONArray(index)
                org.json.JSONArray().apply { order.forEach { put(row.get(it)) } }.toString()
            }.sorted()
        }
        val a = expected.getJSONObject(table)
        val b = actual.getJSONObject(table)
        fun columns(sheet: JSONObject): Set<String> {
            val names = sheet.getJSONArray("columns")
            return (0 until names.length()).map { names.getString(it) }.toSet()
        }
        check(columns(a) == columns(b) && canonicalRows(a) == canonicalRows(b)) { "Snapshot mismatch: $table" }
    }
}

internal suspend fun stageEncryptedLedger(
    source: JibikDatabase, targetFile: File, vault: PilotDatabaseKey,
    openEncrypted: (ByteArray) -> JibikDatabase,
    availableBytes: () -> Long = { targetFile.parentFile?.let {
        android.os.StatFs(it.absolutePath).availableBytes
    } ?: 0L },
    checkpoint: (PilotStage) -> Unit = {},
) {
    // Only isolated fixtures are allowed. Never try opening over the active ledger.
    require(targetFile.name.startsWith("pilot-") && source.openHelper.databaseName?.startsWith("pilot-") == true)
    check(source.openHelper.databaseName != targetFile.name)
    check(listOf("", "-wal", "-shm", "-journal").none { File(targetFile.path + it).exists() })
    val expected = pilotSnapshot(source)
    val size = expected.toString().toByteArray(Charsets.UTF_8).size.toLong()
    check(size <= FinancialBackup.maxBytes - 64) { "Pilot snapshot exceeds size limit" }
    check(availableBytes() >= size * 4 + 8L * 1024 * 1024) { "Not enough staging space" }
    checkpoint(PilotStage.BEFORE_COPY)
    val key = vault.getOrCreate(false)
    try {
        val target = openEncrypted(key)
        try {
            target.withTransaction {
                check((target.ledgerDao().budget() ?: 0L) == 0L)
                FinancialBackup.restore(target, expected)
                target.ledgerDao().saveBudget(MonthlyBudget(amount = expected.getLong("budget")))
            }
            verifyPilotSnapshot(expected, pilotSnapshot(target))
        } finally { target.close() }
    } finally { key.fill(0) }
    checkpoint(PilotStage.AFTER_COPY)
    val reopenedKey = vault.getOrCreate(true)
    try {
        val target = openEncrypted(reopenedKey)
        try {
            verifyPilotSnapshot(expected, pilotSnapshot(target))
            target.openHelper.writableDatabase.query("PRAGMA integrity_check").use {
                check(it.moveToFirst() && it.getString(0) == "ok")
            }
            target.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) }
        } finally { target.close() }
    } finally { reopenedKey.fill(0) }
    checkpoint(PilotStage.AFTER_REOPEN)
    verifyPilotSnapshot(expected, pilotSnapshot(source))
}
