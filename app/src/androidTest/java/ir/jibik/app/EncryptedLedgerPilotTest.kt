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
            source.assetDao().insertHolding(AssetHolding("pilot-gold", "سکه", "GOLD", 250L, now))
            source.assetDao().insertEvent(AssetEvent(assetId = "pilot-gold", kind = "VALUE", valueToman = 300, atMillis = now, note = "ارزش ثبت‌شده"))
            source.wealthDao().insert(WealthSnapshot.from(
                NetWorth(880.toBigInteger(), 300.toBigInteger(), 0.toBigInteger(), 0.toBigInteger(), 0.toBigInteger()), now))
            source.managementDao().saveWeeklyBudget(WeeklyBudget(
                reportRange("WEEK", requireNotNull(parseJalaliDueDate(formatJalaliDueDate(now)))).start, 300, false))
            source.ledgerDao().saveBudget(MonthlyBudget(amount = 600))
            val expected = pilotSnapshot(source)
            stageEncryptedLedger(source, targetFile, vault, ::openEncrypted)
            assertTrue(targetFile.exists())
            val header = ByteArray(16).also { bytes ->
                java.io.DataInputStream(targetFile.inputStream()).use { it.readFully(bytes) }
            }
            assertFalse(header.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)))
            val wrong = ByteArray(32).also(java.security.SecureRandom()::nextBytes)
            val rejected = openEncrypted(wrong)
            try {
                assertTrue(runCatching { rejected.openHelper.writableDatabase.query("SELECT * FROM ledger_records").use { it.moveToFirst() } }.isFailure)
            } finally { runCatching { rejected.close() }; wrong.fill(0) }
            val restored = vault.getOrCreate(true)
            try {
                val reopened = openEncrypted(restored)
                try { verifyPilotSnapshot(expected, pilotSnapshot(reopened)) }
                finally { reopened.close() }
            } finally { restored.fill(0) }
            verifyPilotSnapshot(expected, pilotSnapshot(source))
            val missing = PilotDatabaseKey("jibik-missing-$suffix", File(ctx.noBackupFilesDir, "missing-$suffix.key"))
            assertTrue(runCatching { missing.getOrCreate(true) }.isFailure)
        } finally {
            source.close()
            ctx.deleteDatabase(sourceName)
            ctx.deleteDatabase(targetName)
            vault.deleteFixture()
        }
    }
    @Test fun walSourceChangeAndMissingWrappedKeyFailClosed() = runBlocking {
        System.loadLibrary("sqlcipher")
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val sourceName = "pilot-wal-source-$suffix.db"
        val targetName = "pilot-wal-target-$suffix.db"
        val targetFile = ctx.getDatabasePath(targetName)
        val keyFile = File(ctx.noBackupFilesDir, "pilot-wal-$suffix.key")
        val savedKey = File(ctx.noBackupFilesDir, "pilot-wal-$suffix.saved")
        val vault = PilotDatabaseKey("jibik-wal-$suffix", keyFile)
        fun encrypted(key: ByteArray): JibikDatabase = Room.databaseBuilder(ctx, JibikDatabase::class.java, targetName)
            .openHelperFactory(SupportOpenHelperFactory(key)).build()
        val source = Room.databaseBuilder(ctx, JibikDatabase::class.java, sourceName)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).build()
        try {
            val now = System.currentTimeMillis()
            source.accountDao().insertDefault(FinancialAccountEntity("wallet", "کیف پول", 5000))
            source.ledgerDao().insert(LedgerEntity("before", "EXPENSE", 20, "wallet", null,
                "سایر هزینه‌ها", "پیش از انتقال", now))
            source.openHelper.writableDatabase.query("PRAGMA journal_mode").use {
                assertTrue(it.moveToFirst())
                assertEquals("wal", it.getString(0).lowercase(java.util.Locale.ROOT))
            }
            val before = pilotSnapshot(source)
            val late = LedgerEntity("late", "EXPENSE", 30, "wallet", null,
                "سایر هزینه‌ها", "ثبت هم‌زمان", now + 1)
            val failure = runCatching {
                stageEncryptedLedger(source, targetFile, vault, ::encrypted, checkpoint = { stage ->
                    if (stage == PilotStage.AFTER_REOPEN) source.ledgerDao().insert(late)
                })
            }.exceptionOrNull()
            assertNotNull("A stale staged copy must never be accepted", failure)
            assertTrue(failure?.message.orEmpty().contains("Snapshot mismatch"))
            assertEquals(setOf("before", "late"), source.ledgerDao().getAll().map { it.id }.toSet())
            val key = vault.getOrCreate(true)
            try {
                val candidate = encrypted(key)
                try { verifyPilotSnapshot(before, pilotSnapshot(candidate)) }
                finally { candidate.close() }
            } finally { key.fill(0) }
            assertTrue(runCatching { stageEncryptedLedger(source, targetFile, vault, ::encrypted) }.isFailure)
            assertTrue(keyFile.renameTo(savedKey))
            assertTrue(runCatching { vault.getOrCreate(true) }.isFailure)
            assertFalse("Do not generate a replacement key for an existing database", keyFile.exists())
            assertTrue(savedKey.renameTo(keyFile))
            val recovered = vault.getOrCreate(true)
            try {
                val candidate = encrypted(recovered)
                try { verifyPilotSnapshot(before, pilotSnapshot(candidate)) }
                finally { candidate.close() }
            } finally { recovered.fill(0) }
            assertEquals(setOf("before", "late"), source.ledgerDao().getAll().map { it.id }.toSet())
        } finally {
            source.close()
            ctx.deleteDatabase(sourceName)
            ctx.deleteDatabase(targetName)
            vault.deleteFixture()
            savedKey.delete()
        }
    }

    @Test fun abortedMigrationNeverChangesSourceOrSilentlyReusesCandidate() = runBlocking {
        System.loadLibrary("sqlcipher")
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val sourceName = "pilot-interrupt-source-$suffix.db"
        val targetName = "pilot-interrupt-target-$suffix.db"
        val targetFile = ctx.getDatabasePath(targetName)
        val keyFile = File(ctx.noBackupFilesDir, "pilot-interrupt-$suffix.key")
        val vault = PilotDatabaseKey("jibik-interrupt-$suffix", keyFile)
        fun encrypted(key: ByteArray): JibikDatabase = Room.databaseBuilder(ctx, JibikDatabase::class.java, targetName)
            .openHelperFactory(SupportOpenHelperFactory(key)).build()
        val source = JibikDatabase.open(ctx, sourceName)
        try {
            val now = System.currentTimeMillis()
            source.accountDao().insertDefault(FinancialAccountEntity("wallet", "کیف پول", 100000))
            for (index in 0 until 1001) source.ledgerDao().insert(
                LedgerEntity("e-$index", "EXPENSE", 1, "wallet", null, "سایر هزینه‌ها", "آزمون", now + index))
            source.ledgerDao().saveBudget(MonthlyBudget(amount = 15000))
            val expected = pilotSnapshot(source)
            assertTrue(runCatching {
                stageEncryptedLedger(source, targetFile, vault, ::encrypted, availableBytes = { 0L })
            }.isFailure)
            assertFalse(targetFile.exists())
            verifyPilotSnapshot(expected, pilotSnapshot(source))
            for (step in listOf(PilotStage.BEFORE_COPY, PilotStage.AFTER_COPY, PilotStage.AFTER_REOPEN)) {
                assertTrue(runCatching {
                    stageEncryptedLedger(source, targetFile, vault, ::encrypted, checkpoint = { if (it == step) error("simulated interruption") })
                }.isFailure)
                verifyPilotSnapshot(expected, pilotSnapshot(source))
                // A leftover candidate cannot be overwritten implicitly.
                if (targetFile.exists()) assertTrue(runCatching {
                    stageEncryptedLedger(source, targetFile, vault, ::encrypted)
                }.isFailure)
                // Explicit fixture-only cleanup, never performed on a real ledger.
                ctx.deleteDatabase(targetName)
                vault.deleteFixture()
            }
            stageEncryptedLedger(source, targetFile, vault, ::encrypted)
            verifyPilotSnapshot(expected, pilotSnapshot(source))
            val wrapped = keyFile.readBytes()
            keyFile.writeBytes(wrapped.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })
            assertTrue(runCatching { vault.getOrCreate(true) }.isFailure)
            verifyPilotSnapshot(expected, pilotSnapshot(source))
            keyFile.writeBytes(wrapped)
            val restored = vault.getOrCreate(true)
            try {
                val db = encrypted(restored)
                try { verifyPilotSnapshot(expected, pilotSnapshot(db)) } finally { db.close() }
            } finally { restored.fill(0) }
        } finally {
            source.close()
            ctx.deleteDatabase(sourceName); ctx.deleteDatabase(targetName); vault.deleteFixture()
        }
    }
}
