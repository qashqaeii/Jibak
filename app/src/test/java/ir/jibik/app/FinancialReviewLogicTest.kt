package ir.jibik.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class FinancialReviewLogicTest {
    private fun date(value: String) = requireNotNull(parseJalaliDueDate(value))

    @Test fun partialInstallmentShowsOnlyRemainingAndSettledItemsDisappear() {
        val due = date("1403/06/15")
        val obligation = FinancialObligationEntity("loan", "وام", "LOAN", 100, due, 2, "", due)
        val paid = ObligationPaymentEntity("tx", obligation.id, 20, due)
        val now = date("1403/06/16")
        val row = reviewDueObligations(listOf(obligation), listOf(paid), now).single()
        assertEquals(30L, row.amount)
        assertEquals(due, row.day)
        assertTrue(reviewDueObligations(listOf(obligation), listOf(paid.copy(amountToman = 50)), now).isEmpty())
        assertTrue(reviewDueObligations(listOf(obligation), listOf(paid.copy(amountToman = 100)), now).isEmpty())
    }

    @Test fun oldArrearsAreNotHiddenAndHandledOccurrencesAreExcluded() {
        val first = date("1400/01/15")
        val now = date("1403/01/15")
        val rule = RecurringRule("rent", "اجاره", false, "wallet", 100, first)
        val rows = reviewDueRecurring(listOf(rule), emptyList(), now)
        assertEquals(37, rows.size)
        assertEquals(first, rows.first().second)
        assertEquals(now, rows.last().second)
        val handled = RecurringDecision(rule.id, first, null, null)
        assertEquals(36, reviewDueRecurring(listOf(rule), listOf(handled), now).size)
        assertEquals(1, reviewDueRecurring(listOf(rule.copy(stoppedAt = first)), emptyList(), now).size)
    }
}
