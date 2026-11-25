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

    @Test fun bankAndCommitmentReconcileAtomicallyWithoutDuplicateMoney() = runBlocking {
        val ctx: Context = RuntimeEnvironment.getApplication()
        JibikDatabase.open(ctx, "bank-combined").withOpenDatabase { db ->
            val store = LocalLedgerStore(ctx, db)
            val day = requireNotNull(parseJalaliDueDate("1403/07/01"))
            store.loadAccounts()
            store.addObligation(FinancialObligationEntity("bill", "قبض", "BILL", 100, day, 1, "", day))
            store.addRecurring(RecurringRule("rent", "اجاره", false, "wallet", 30, day))
            val first = LedgerRecord("first", LedgerKind.EXPENSE, 30, fromAccountId = "wallet",
                category = "سایر هزینه‌ها", timestampMillis = day)
            suspend fun sms(id: String) {
                db.bankSmsCandidateDao().insertIgnore(BankSmsCandidateEntity(id, "bank", "بانک", false, 300, "", day))
            }
            sms("one")
            val before = store.load()
            assertTrue(runCatching { store.reconcileBankPayment("one", first, false,
                BankPaymentTarget.Obligation("missing")) }.isFailure)
            assertEquals(before, store.load())
            assertEquals("PENDING", db.bankSmsCandidateDao().find("one")?.state)
            assertTrue(store.loadObligationPayments().isEmpty())
            val posted = store.reconcileBankPayment("one", first, false, BankPaymentTarget.Obligation("bill"))
            assertEquals(first, posted)
            assertEquals(listOf(first), store.load())
            assertEquals(30L, store.loadObligationPayments().single().amountToman)
            assertEquals("REGISTERED", db.bankSmsCandidateDao().find("one")?.state)
            assertTrue(runCatching { store.reconcileBankPayment("one", first, false, BankPaymentTarget.Obligation("bill")) }.isFailure)
            assertEquals(1, store.load().size)
            val second = first.copy(id = "second")
            store.append(second)
            sms("two")
            val balance = FinanceEngine.balances(store.loadAccounts(), store.load())
            store.reconcileBankPayment("two", second, true, BankPaymentTarget.Recurring("rent", day))
            assertEquals(balance, FinanceEngine.balances(store.loadAccounts(), store.load()))
            assertEquals(second.id, store.allRecurringDecisions().single().transactionId)
            assertTrue(store.inspectIntegrity().isEmpty())
            sms("three")
            val third = first.copy(id = "third")
            assertTrue(runCatching { store.reconcileBankPayment("three", third, false,
                BankPaymentTarget.Recurring("rent", day)) }.isFailure)
            assertEquals("PENDING", db.bankSmsCandidateDao().find("three")?.state)
            assertEquals(2, store.load().size)
            assertEquals(balance, FinanceEngine.balances(store.loadAccounts(), store.load()))
        }
    }

    @Test fun recurringPaymentsCannotBeReusedEditedOrVoided() = runBlocking {
        val ctx: Context = RuntimeEnvironment.getApplication()
        JibikDatabase.open(ctx, "recurring-integrity").withOpenDatabase { db ->
            val store = LocalLedgerStore(ctx, db)
            val day = requireNotNull(parseJalaliDueDate("۱۴۰۳/۰۶/۱۵"))
            val record = LedgerRecord("payment", LedgerKind.EXPENSE, 30, fromAccountId = "wallet",
                category = "خرید", timestampMillis = day)
            store.append(record)
            val first = RecurringRule("first", "اجاره", false, "wallet", 30, day)
            val other = first.copy(id = "other", title = "اشتراک")
            store.addRecurring(first)
            store.addRecurring(other)
            val before = FinanceEngine.balances(store.loadAccounts(), store.load())
            store.linkRecurring(first.id, day, record)
            assertTrue(runCatching { store.linkRecurring(other.id, day, record) }.isFailure)
            assertTrue(runCatching { store.setVoided(record, true) }.isFailure)
            assertTrue(runCatching { store.correct(record, record.copy(amountToman = 31)) }.isFailure)
            store.addObligation(FinancialObligationEntity("bill", "قبض", "BILL", 100, day, 1, "", day))
            assertTrue(runCatching { store.linkObligationPayment("bill", "wallet", record.id) }.isFailure)
            assertEquals(before, FinanceEngine.balances(store.loadAccounts(), store.load()))
            assertEquals(1, store.allRecurringDecisions().size)
            assertTrue(store.loadObligationPayments().isEmpty())
            assertTrue(store.revisions(record.id).isEmpty())
            val second = record.copy(id = "settlement")
            store.append(second)
            store.linkObligationPayment("bill", "wallet", second.id)
            assertTrue(runCatching { store.linkRecurring(other.id, day, second) }.isFailure)
        }
    }

    @Test fun receiptCorrectionAndDetachingKeepAuditAndExactBalances() = runBlocking {
        val ctx: Context = RuntimeEnvironment.getApplication()
        JibikDatabase.open(ctx, "receipt-edit").withOpenDatabase { db ->
            val store = LocalLedgerStore(ctx, db)
            val day = requireNotNull(parseJalaliDueDate("۱۴۰۳/۰۶/۱۵"))
            val original = LedgerRecord("receipt", LedgerKind.EXPENSE, 30, fromAccountId = "wallet",
                category = "خرید", timestampMillis = day)
            val old = listOf(PurchaseLine("نان", "خوراک", 3, 10))
            val changed = listOf(PurchaseLine("نان", "خوراک", 2, 10))
            store.appendItemizedPurchase(original, old)
            store.addObligation(FinancialObligationEntity("bill", "قبض", "BILL", 100, day, 1, "", day))
            store.linkObligationPayment("bill", "wallet", original.id)
            val before = FinanceEngine.balances(store.loadAccounts(), store.load())
            assertTrue(runCatching { store.correctReceipt(original, original.copy(amountToman = 20), old, changed) }.isFailure)
            store.detachPaymentLinks(original)
            assertEquals(before, FinanceEngine.balances(store.loadAccounts(), store.load()))
            assertTrue(store.loadObligationPayments().isEmpty())
            assertTrue(runCatching { store.correctReceipt(original, original.copy(amountToman = 21), old, changed) }.isFailure)
            assertEquals(old, store.loadPurchaseLines(original.id))
            store.correctReceipt(original, original.copy(amountToman = 20), old, changed)
            assertEquals(changed, store.loadPurchaseLines(original.id))
            assertEquals(-20L, FinanceEngine.balances(store.loadAccounts(), store.load()).getValue("wallet"))
            assertEquals(2, store.financialEdits(original.id).size)
            assertEquals(original, store.revisions(original.id).single().previous.toRecord())
            assertTrue(runCatching { store.correctReceipt(original, original.copy(amountToman = 20), old, changed) }.isFailure)
            val backup = store.backup()
            JibikDatabase.open(ctx, "receipt-edit-restore").withOpenDatabase { targetDb ->
                val target = LocalLedgerStore(ctx, targetDb)
                target.loadAccounts()
                target.restoreBackup(backup)
                assertEquals(store.financialEdits(original.id), target.financialEdits(original.id))
                assertEquals(store.load(), target.load())
                assertEquals(changed, target.loadPurchaseLines(original.id))
            }
        }
    }

    @Test fun reopeningBankAndRecurringLinksPreservesPaymentAndDetectsLegacyConflict() = runBlocking {
        val ctx: Context = RuntimeEnvironment.getApplication()
        JibikDatabase.open(ctx, "legacy-link-review").withOpenDatabase { db ->
            val store = LocalLedgerStore(ctx, db)
            val day = requireNotNull(parseJalaliDueDate("۱۴۰۳/۰۶/۱۵"))
            val record = LedgerRecord("existing", LedgerKind.EXPENSE, 30, fromAccountId = "wallet",
                category = "خرید", timestampMillis = day)
            store.append(record)
            val rule = RecurringRule("rent", "اجاره", false, "wallet", 30, day)
            store.addRecurring(rule)
            store.addRecurring(rule.copy(id = "second"))
            val sms = BankSmsCandidateEntity("message", "bank", "بانک", false, 300, "", day)
            db.bankSmsCandidateDao().insertIgnore(sms)
            store.linkBankSms(sms.id, record)
            store.linkRecurring(rule.id, day, record)
            assertTrue(store.inspectIntegrity().isEmpty())
            // Emulate a duplicate link written by a pre-hardening version.
            db.recurringDao().decide(RecurringDecision("second", day, record.id, 30))
            assertTrue(store.inspectIntegrity().any { it.id == record.id })
            val before = FinanceEngine.balances(store.loadAccounts(), store.load())
            store.detachPaymentLinks(record)
            assertEquals(before, FinanceEngine.balances(store.loadAccounts(), store.load()))
            assertEquals("PENDING", db.bankSmsCandidateDao().find(sms.id)?.state)
            assertTrue(store.allRecurringDecisions().isEmpty())
            assertTrue(store.inspectIntegrity().isEmpty())
            store.linkBankSms(sms.id, record)
            store.linkRecurring(rule.id, day, record)
            assertEquals(before, FinanceEngine.balances(store.loadAccounts(), store.load()))
            assertEquals(1, store.financialEdits(record.id).size)
        }
    }

    @Test fun recurringUpgradeReadsOldBackupAndPreservesHistoricalDecisions() = runBlocking {
        val ctx: Context = RuntimeEnvironment.getApplication()
        val first = requireNotNull(parseJalaliDueDate("1403/07/01"))
        JibikDatabase.open(ctx, "recurring-backup-source").withOpenDatabase { sourceDb ->
            val source = LocalLedgerStore(ctx, sourceDb)
            source.loadAccounts()
            val old = RecurringRule("old", "اجاره", false, "wallet", 50, first)
            source.addRecurring(old)
            source.decideRecurring(old.id, first, null)
            val original = source.backup()
            val v4 = org.json.JSONObject(original.toString()).put("version", 4)
            val sheet = v4.getJSONObject("recurring_rules")
            val names = sheet.getJSONArray("columns")
            val retained = (0 until names.length()).filter {
                names.getString(it) !in setOf("frequency", "interval", "category", "endDay")
            }
            val columns = org.json.JSONArray()
            retained.forEach { columns.put(names.getString(it)) }
            val rows = org.json.JSONArray()
            val originalRows = sheet.getJSONArray("rows")
            for (i in 0 until originalRows.length()) {
                val row = org.json.JSONArray()
                retained.forEach { row.put(originalRows.getJSONArray(i).get(it)) }
                rows.put(row)
            }
            sheet.put("columns", columns).put("rows", rows)
            JibikDatabase.open(ctx, "recurring-backup-target").withOpenDatabase { db ->
                val target = LocalLedgerStore(ctx, db)
                target.loadAccounts()
                target.restoreBackup(v4)
                assertEquals(old, target.recurringRules().single())
                assertEquals(source.allRecurringDecisions(), target.allRecurringDecisions())
                val tomorrow = jalaliDueDayEndExclusive(System.currentTimeMillis())
                val successor = old.copy(id = "new", amount = 75, firstDay = tomorrow,
                    frequency = "WEEKLY", interval = 2, endDay = offsetJalaliDay(tomorrow, 28))
                val before = target.load()
                assertTrue(runCatching { target.replaceRecurring(old.id, successor.copy(firstDay = first)) }.isFailure)
                assertEquals(old, target.recurringRules().single())
                target.replaceRecurring(old.id, successor)
                assertEquals(before, target.load())
                assertEquals(source.allRecurringDecisions(), target.allRecurringDecisions())
                val rules = target.recurringRules().associateBy { it.id }
                assertNotNull(rules.getValue(old.id).stoppedAt)
                assertEquals(successor, rules.getValue(successor.id))
                assertTrue(rules.getValue(old.id).dueBetween(tomorrow, offsetJalaliDay(tomorrow, 365)).isEmpty())
                assertEquals(3, successor.dueBetween(tomorrow, offsetJalaliDay(tomorrow, 29)).size)
                assertTrue(runCatching { target.replaceRecurring(old.id, successor.copy(id = "another")) }.isFailure)
            }
        }
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
            it.execSQL("DROP TABLE envelope_events")
            it.execSQL("DROP TABLE money_envelopes")
            it.execSQL("DROP TABLE financial_edit_history")
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
