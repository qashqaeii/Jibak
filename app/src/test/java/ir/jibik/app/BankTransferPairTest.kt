package ir.jibik.app

import ir.jibik.core.finance.Account
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import org.junit.Assert.*
import org.junit.Test

class BankTransferPairTest {
    private val at = 1800000000000L
    private val profiles = listOf(
        BankSenderProfile("a", "melli", "100", "4004", "a"),
        BankSenderProfile("b", "mellat", "200", "4005", "b")
    )
    private val accounts = listOf(Account("a","A",100000), Account("b","B",0))
    private val debit = BankSmsCandidateEntity("d", "melli", "ملی", false, 200000, "4004", at)
    private val credit = BankSmsCandidateEntity("c", "mellat", "ملت", true, 200000, "4005", at + 1000)
    @Test fun proposesOneOppositePairOnly() {
        val pair = bankTransferPairs(listOf(debit, credit), profiles, accounts, emptyList()).single()
        assertEquals(20000L, pair.amountToman)
        assertEquals("a", pair.fromId)
        assertEquals("b", pair.toId)
        assertTrue(bankTransferPairs(listOf(debit, credit, credit.copy(id="other")), profiles, accounts, emptyList()).isEmpty())
        assertTrue(bankTransferPairs(listOf(debit, credit.copy(receivedAtMillis=at+301000)), profiles, accounts, emptyList()).isEmpty())
        assertTrue(bankTransferPairs(listOf(debit, credit), profiles, accounts.map { it.copy(archived=true) }, emptyList()).isEmpty())
    }
    @Test fun onePreviouslyPostedAutomaticSideIsEligibleButNotManual() {
        val id = BANK_AUTO_PREFIX + debit.id
        val linked = debit.copy(state="REGISTERED", linkedTransactionId=id)
        val row = LedgerRecord(id, LedgerKind.EXPENSE, 20000, fromAccountId="a", category="سایر هزینه‌ها", note="", timestampMillis=at)
        assertEquals(1, bankTransferPairs(listOf(linked, credit), profiles, accounts, listOf(row)).size)
        assertTrue(bankTransferPairs(listOf(linked, credit), profiles, accounts, emptyList()).isEmpty())
    }
}
