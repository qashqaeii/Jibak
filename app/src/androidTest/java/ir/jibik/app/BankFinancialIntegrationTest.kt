package ir.jibik.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.jibik.core.finance.Account
import ir.jibik.core.finance.FinanceEngine
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BankFinancialIntegrationTest {
    @Test fun confirmedPairAndVoidedAutoPreserveLedgerAndEvidence() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "bank-test-" + UUID.randomUUID().toString() + "-"
        val files = mutableSetOf<String>()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                files.add(prefix + name)
                return base.getSharedPreferences(prefix + name, mode)
            }
        }
        val db = Room.inMemoryDatabaseBuilder(base, JibikDatabase::class.java).build()
        try {
            val store = LocalLedgerStore(context, db)
            store.load()
            store.addAccount(Account("a", "مبدأ", 100000))
            store.addAccount(Account("b", "مقصد", 0))
            BankSenderRegistry.save(context, "melli", "10001", "4004", "a")
            BankSenderRegistry.save(context, "mellat", "10002", "4005", "b")
            val now = System.currentTimeMillis()
            val debit = BankSmsMatch("melli", "ملی", 200000, false, "4004", "10001", now, "a".repeat(64))
            val credit = BankSmsMatch("mellat", "ملت", 200000, true, "4005", "10002", now+1000, "b".repeat(64))
            assertEquals(2, store.importBankSms(listOf(debit, credit)))
            val pair = bankTransferPairs(store.bankSmsSuggestions(), BankSenderRegistry.active(context),
                store.loadAccounts(), store.load()).single()
            val transfer = store.confirmBankTransfer(pair, context)
            assertEquals(LedgerKind.TRANSFER, transfer.kind)
            assertEquals(1, store.load().size)
            assertEquals(2, db.bankSmsCandidateDao().linked(transfer.id).size)
            val balances = FinanceEngine.balances(store.loadAccounts(), store.load())
            assertEquals(80000L, balances.getValue("a"))
            assertEquals(20000L, balances.getValue("b"))
            assertEquals(0, store.importBankSms(listOf(debit, credit)))
            assertTrue(runCatching { store.confirmBankTransfer(pair, context) }.isFailure)
            assertEquals(1, store.load().size)

            val extra = debit.copy(fingerprint = "c".repeat(64), amountRial = 50000, receivedAtMillis = now+600000)
            assertEquals(1, store.importBankSms(listOf(extra)))
            val row = LedgerRecord(BANK_AUTO_PREFIX + extra.fingerprint, LedgerKind.EXPENSE, 5000,
                fromAccountId = "a", category = "سایر هزینه‌ها", note = "", timestampMillis = extra.receivedAtMillis)
            store.registerBankSms(extra.fingerprint, row)
            assertEquals(75000L, FinanceEngine.balances(store.loadAccounts(), store.load()).getValue("a"))
            store.voidBankAuto(row)
            assertEquals(80000L, FinanceEngine.balances(store.loadAccounts(), store.load()).getValue("a"))
            assertEquals("IGNORED", db.bankSmsCandidateDao().find(extra.fingerprint)?.state)
            assertEquals(0, store.importBankSms(listOf(extra)))
            assertTrue(runCatching { store.voidBankAuto(row) }.isFailure)
            assertTrue(store.voided().any { it.id == row.id })
            assertTrue(FinancialIntegrity.inspect(db).none { it.label.contains("بانکی") })
        } finally {
            db.close()
            files.forEach { base.deleteSharedPreferences(it) }
        }
    }
}
