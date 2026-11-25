package ir.jibik.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.jibik.core.finance.*
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EncryptedLedgerPilotTest {
    private fun assertSameSnapshot(expected: JSONObject, actual: JSONObject) {
        assertEquals(expected.getLong("budget"), actual.getLong("budget"))
        FinancialBackup.tables.forEach { table ->
            val a = expected.getJSONObject(table); val b = actual.getJSONObject(table)
            assertEquals(a.getJSONArray("columns").toString(), b.getJSONArray("columns").toString())
            fun rows(sheet: JSONObject): List<String> {
                val data = sheet.getJSONArray("rows")
                return (0 until data.length()).map { data.getJSONArray(it).toString() }.sorted()
            }
            assertEquals(table, rows(a), rows(b))
        }
    }

    @Test fun copyOnlyPilotReopensWithKeystoreAndRejectsWrongKey() = runBlocking {
        System.loadLibrary("sqlcipher")
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val sourceName = "pilot-source-$suffix.db"
        val targetName = "pilot-cipher-$suffix.db"
        val keyFile = File(ctx.noBackupFilesDir, "pilot-$suffix.key")
        val vault = PilotDatabaseKey("jibik-pilot-$suffix", keyFile)
        val targetFile = ctx.getDatabasePath(targetName)
        fun openEncrypted(key: ByteArray): JibikDatabase =
            Room.databaseBuilder(ctx, JibikDatabase::class.java, targetName)
                .openHelperFactory(SupportOpenHelperFactory(key)).build()
        val source = JibikDatabase.open(ctx, sourceName)
        try {
            // Use only isolated fixtures; never read or write the real user database.
            val now = System.currentTimeMillis()
            source.accountDao().insertDefault(FinancialAccountEntity("wallet", "کیف پول جیبیک", 1000))
            source.ledgerDao().insert(LedgerEntity("pilot-entry", "EXPENSE", 120, "wallet", null,
                "سایر هزینه‌ها", "آزمون فارسی", now))
            source.envelopeDao().insert(MoneyEnvelope("pilot-envelope", "سفر", "wallet", 500, 200, now))
            val expected = FinancialBackup.snapshot(source, 600)
            val key = vault.getOrCreate(false)
            try {
                val target = openEncrypted(key)
                try {
                    FinancialBackup.restore(target, expected)
                    assertSameSnapshot(expected, FinancialBackup.snapshot(target, 600))
                    target.openHelper.writableDatabase.query("PRAGMA integrity_check").use {
                        assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0))
                    }
                    target.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                } finally { target.close() }
            } finally { key.fill(0) }
            assertTrue(targetFile.exists())
            val header = targetFile.inputStream().use { it.readNBytes(16) }
            assertFalse(header.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)))
            val wrong = ByteArray(32).also(java.security.SecureRandom()::nextBytes)
            val rejected = openEncrypted(wrong)
            try {
                assertTrue(runCatching { rejected.openHelper.writableDatabase.query("SELECT * FROM ledger_records").use { it.moveToFirst() } }.isFailure)
            } finally { runCatching { rejected.close() }; wrong.fill(0) }
            val restored = vault.getOrCreate(true)
            try {
                val reopened = openEncrypted(restored)
                try { assertSameSnapshot(expected, FinancialBackup.snapshot(reopened, 600)) }
                finally { reopened.close() }
            } finally { restored.fill(0) }
            assertSameSnapshot(expected, FinancialBackup.snapshot(source, 600))
            val missing = PilotDatabaseKey("jibik-missing-$suffix", File(ctx.noBackupFilesDir, "missing-$suffix.key"))
            assertTrue(runCatching { missing.getOrCreate(true) }.isFailure)
        } finally {
            source.close()
            ctx.deleteDatabase(sourceName)
            ctx.deleteDatabase(targetName)
            vault.deleteFixture()
        }
    }
}
