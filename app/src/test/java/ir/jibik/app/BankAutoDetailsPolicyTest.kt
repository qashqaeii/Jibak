package ir.jibik.app
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import org.junit.Assert.*
import org.junit.Test
class BankAutoDetailsPolicyTest {
    private val row = LedgerRecord(BANK_AUTO_PREFIX + "a".repeat(64), LedgerKind.EXPENSE, 100,
        fromAccountId="wallet", category="سایر هزینه‌ها", note="", timestampMillis=1000)
    @Test fun pendingOnlyUntilNoteOrMeaningfulCategoryAndNeverTransfer() {
        assertTrue(needsBankAutoDetails(row))
        assertFalse(needsBankAutoDetails(row.copy(note="خرید")))
        assertFalse(needsBankAutoDetails(row.copy(category="خوراک")))
        assertFalse(needsBankAutoDetails(row.copy(kind=LedgerKind.TRANSFER,toAccountId="another")))
        assertFalse(isBankAutoId("bank-auto-invalid"))
        assertFalse(isEditableBankAuto(row.copy(id="manual")))
    }
}
