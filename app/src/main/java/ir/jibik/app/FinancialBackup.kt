package ir.jibik.app

import android.database.Cursor
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** V1 stores typed financial rows. All SQL identifiers come from our database. */
internal object FinancialBackup {
    val tables = listOf("financial_accounts", "ledger_records", "custom_categories",
        "category_budgets", "financial_obligations", "savings_goals", "purchase_lines",
        "obligation_payments", "bank_sms_candidates", "ledger_revisions", "ledger_voids")
    private val magic = "JIBIKBAK1".toByteArray(Charsets.US_ASCII)
    const val maxBytes = 32 * 1024 * 1024

    private fun columns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("SELECT * FROM $table LIMIT 0").use { it.columnNames.toList() }

    suspend fun snapshot(room: JibikDatabase, budget: Long): JSONObject = room.withTransaction {
        val db = room.openHelper.writableDatabase
        val root = JSONObject().put("version", 2).put("budget", budget)
        tables.forEach { table ->
            db.query("SELECT * FROM $table").use { rows ->
                val data = JSONArray()
                while (rows.moveToNext()) {
                    val row = JSONArray()
                    for (i in 0 until rows.columnCount) {
                        when (rows.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> row.put(JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> row.put(rows.getLong(i))
                            Cursor.FIELD_TYPE_STRING -> row.put(rows.getString(i))
                            else -> error("Unsupported financial data")
                        }
                    }
                    data.put(row)
                }
                root.put(table, JSONObject().put("columns", JSONArray(rows.columnNames.toList()))
                    .put("rows", data))
            }
        }
        root
    }

    suspend fun restore(room: JibikDatabase, root: JSONObject) = room.withTransaction {
        require(root.getInt("version") in 1..2 && root.getLong("budget") >= 0)
        val db = room.openHelper.writableDatabase
        // Restore on a fresh ledger only. Recheck inside the write transaction.
        tables.forEach { table ->
            val predicate = if (table == "financial_accounts")
                " WHERE id != 'wallet' OR openingBalanceToman != 0" else ""
            db.query("SELECT 1 FROM $table$predicate LIMIT 1").use { check(!it.moveToFirst()) }
        }
        db.execSQL("DELETE FROM financial_accounts")
        tables.forEach { table ->
            if (root.getInt("version") == 1 && table == "ledger_voids") return@forEach
            val sheet = root.getJSONObject(table)
            val names = columns(db, table)
            val saved = sheet.getJSONArray("columns")
            val indices = (0 until saved.length()).associateBy { saved.getString(it) }
            require(indices.keys == names.toSet() && saved.length() == names.size)
            val rows = sheet.getJSONArray("rows")
            val placeholders = names.joinToString(",") { "?" }
            val sql = "INSERT INTO $table (" + names.joinToString(",") + ") VALUES ($placeholders)"
            for (i in 0 until rows.length()) {
                val row = rows.getJSONArray(i)
                require(row.length() == names.size)
                val args = names.map { name ->
                    row.get(indices.getValue(name)).takeUnless { it === JSONObject.NULL }
                }.toTypedArray()
                db.execSQL(sql, args)
            }
        }
        db.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) }
        val accounts = room.accountDao().list().map { ir.jibik.core.finance.Account(it.id, it.title, it.openingBalanceToman) }
        require(accounts.any { it.id == "wallet" })
        ir.jibik.core.finance.FinanceEngine.balances(accounts, room.ledgerDao().getAll().map { it.toRecord() })
    }

    fun encrypt(root: JSONObject, password: CharArray): ByteArray {
        val clear = root.toString().toByteArray(Charsets.UTF_8)
        try {
            require(clear.size <= maxBytes - 64)
            val random = SecureRandom()
            val header = magic + ByteArray(16).also(random::nextBytes) + ByteArray(12).also(random::nextBytes)
            return header + cipher(password, header, Cipher.ENCRYPT_MODE).doFinal(clear)
        } finally { clear.fill(0) }
    }

    fun decrypt(data: ByteArray, password: CharArray): JSONObject {
        val size = magic.size + 28
        require(data.size in (size + 16)..maxBytes && data.copyOf(magic.size).contentEquals(magic))
        val header = data.copyOf(size)
        val clear = cipher(password, header, Cipher.DECRYPT_MODE).doFinal(data, size, data.size - size)
        return try {
            JSONObject(clear.toString(Charsets.UTF_8)).also { root ->
                require(root.getInt("version") in 1..2 && root.getLong("budget") >= 0)
                tables.filter { it != "ledger_voids" || root.getInt("version") == 2 }.forEach { root.getJSONObject(it).getJSONArray("rows") }
            }
        } finally { clear.fill(0) }
    }

    private fun cipher(password: CharArray, header: ByteArray, mode: Int): Cipher {
        require(password.size in 12..256)
        val spec = PBEKeySpec(password, header.copyOfRange(magic.size, magic.size + 16),
            EncryptedLedgerCsvExport.ITERATIONS, 256)
        val key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, header.takeLast(12).toByteArray()))
                updateAAD(header)
            }
        } finally { key.fill(0) }
    }
}
