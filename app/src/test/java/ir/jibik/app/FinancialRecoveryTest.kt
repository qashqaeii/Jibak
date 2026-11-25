package ir.jibik.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import ir.jibik.core.finance.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class FinancialRecoveryTest {
    @Test fun migrateCorrectLinkAndRestoreWithoutChangingBalances() = runBlocking {
        val ctx: Context = RuntimeEnvironment.getApplication()
        val name = "recovery-test"
        val record = LedgerRecord("purchase", LedgerKind.EXPENSE, 30, fromAccountId = "wallet",
            category = "خرید", timestampMillis = 1000)
        JibikDatabase.open(ctx, name).use { db ->
            val store = LocalLedgerStore(ctx, db)
            store.append(record)
        }
        // Construct the v8 layout and reopen through the production migration chain.
        SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE ledger_revisions")
            it.execSQL("DROP TABLE monthly_budget")
            it.execSQL("DROP TABLE ledger_voids")
            it.version = 8
        }
        JibikDatabase.open(ctx, name).use { db ->
            val source = LocalLedgerStore(ctx, db)
            val corrected = record.copy(amountToman = 25)
            source.correct(record, corrected)
            source.setVoided(corrected, true)
            assertTrue(source.load().isEmpty())
            source.setVoided(corrected, false)
            assertEquals(record, source.revisions(record.id).single().previous.toRecord())
            assertTrue(runCatching { source.correct(record, record.copy(amountToman = 1)) }.isFailure)
            source.addObligation(FinancialObligationEntity("bill", "قبض", "BILL", 40, 5000, 1, "", 1000))
            repeat(2) { source.linkObligationPayment("bill", "wallet", record.id) }
            assertEquals(1, source.loadObligationPayments().size)
            source.updateBudgetToman(500)
            val pass = "correct-password".toCharArray()
            val bytes = FinancialBackup.encrypt(source.backup(), pass)
            assertTrue(runCatching { FinancialBackup.decrypt(bytes, "wrong-password".toCharArray()) }.isFailure)
            val backup = FinancialBackup.decrypt(bytes, pass)
            JibikDatabase.open(ctx, "restored").use { targetDb ->
                val target = LocalLedgerStore(ctx, targetDb)
                target.loadAccounts()
                target.restoreBackup(backup)
                assertEquals(source.load(), target.load())
                assertEquals(source.revisions(record.id), target.revisions(record.id))
                assertEquals(source.loadObligationPayments(), target.loadObligationPayments())
                assertEquals(500L, target.budgetToman())
                assertEquals(FinanceEngine.balances(source.loadAccounts(), source.load()),
                    FinanceEngine.balances(target.loadAccounts(), target.load()))
                assertTrue(runCatching { target.restoreBackup(backup) }.isFailure)
                assertEquals(listOf(corrected), target.load())
            }
        }
    }
}
