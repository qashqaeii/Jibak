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
    private inline fun JibikDatabase.withOpenDatabase(block: (JibikDatabase) -> Unit) {
        try { block(this) } finally { close() }
    }

    @Test fun migrateCorrectLinkAndRestoreWithoutChangingBalances() = runBlocking {
        val ctx: Context = RuntimeEnvironment.getApplication()
        val name = "recovery-test"
        val record = LedgerRecord("purchase", LedgerKind.EXPENSE, 30, fromAccountId = "wallet",
            category = "خرید", timestampMillis = 1000)
        JibikDatabase.open(ctx, name).withOpenDatabase { db ->
            val store = LocalLedgerStore(ctx, db)
            store.append(record)
        }
        // Construct the v8 layout and reopen through the production migration chain.
        SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE ledger_revisions")
            it.execSQL("DROP TABLE monthly_budget")
            it.execSQL("DROP TABLE ledger_voids")
            it.execSQL("DROP TABLE recurring_decisions")
            it.execSQL("DROP TABLE recurring_rules")
            it.version = 8
        }
        JibikDatabase.open(ctx, name).withOpenDatabase { db ->
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
            val receipt = record.copy(id = "receipt", amountToman = 12)
            source.appendItemizedPurchase(receipt, listOf(PurchaseLine("نان", "خوراک", 2, 6)))
            source.setVoided(receipt, true)
            val first = requireNotNull(parseJalaliDueDate("۱۴۰۳/۰۶/۳۱"))
            val rule = RecurringRule("rent", "اجاره", false, "wallet", 20, first)
            source.addRecurring(rule)
            val shortMonth = currentJalaliMonth(requireNotNull(parseJalaliDueDate("۱۴۰۳/۰۷/۰۱")))
            val next = requireNotNull(rule.dueIn(shortMonth))
            assertEquals("۱۴۰۳/۰۷/۳۰", formatJalaliDueDate(next))
            source.decideRecurring(rule.id, first, 18)
            assertTrue(runCatching { source.decideRecurring(rule.id, first, 18) }.isFailure)
            source.decideRecurring(rule.id, next, null)
            assertEquals(0, pendingOccurrenceCount(rule, source.allRecurringDecisions(), jalaliDueDayEndExclusive(next)))
            assertEquals(1, source.load().count { it.note == rule.title })
            assertEquals(18L, source.load().single { it.note == rule.title }.amountToman)
            source.updateBudgetToman(500)
            val pass = "correct-password".toCharArray()
            val bytes = FinancialBackup.encrypt(source.backup(), pass)
            assertTrue(runCatching { FinancialBackup.decrypt(bytes, "wrong-password".toCharArray()) }.isFailure)
            val backup = FinancialBackup.decrypt(bytes, pass)
            JibikDatabase.open(ctx, "restored").withOpenDatabase { targetDb ->
                val target = LocalLedgerStore(ctx, targetDb)
                target.loadAccounts()
                val damaged = org.json.JSONObject(backup.toString())
                damaged.getJSONObject("ledger_revisions").getJSONArray("rows").put(org.json.JSONArray())
                assertTrue(runCatching { target.restoreBackup(damaged) }.isFailure)
                assertTrue(target.load().isEmpty())
                assertEquals(0L, target.budgetToman())
                target.restoreBackup(backup)
                assertEquals(source.voided(), target.voided())
                assertEquals(source.loadAllPurchaseLines(), target.loadAllPurchaseLines())
                assertEquals(source.load(), target.load())
                assertEquals(source.revisions(record.id), target.revisions(record.id))
                assertEquals(source.loadObligationPayments(), target.loadObligationPayments())
                assertEquals(500L, target.budgetToman())
                assertEquals(FinanceEngine.balances(source.loadAccounts(), source.load()),
                    FinanceEngine.balances(target.loadAccounts(), target.load()))
                assertTrue(runCatching { target.restoreBackup(backup) }.isFailure)
                assertEquals(source.load(), target.load())
                assertEquals(source.recurringRules(), target.recurringRules())
                assertEquals(source.recurringDecisions(shortMonth), target.recurringDecisions(shortMonth))
            }
        }
    }
}
