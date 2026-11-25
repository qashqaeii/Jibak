package ir.jibik.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class SpendableBalanceTest {
    private val start = parseJalaliDueDate("۱۴۰۵/۰۸/۱۰")!!
    private val second = jalaliInstallmentDueDate(start, 1)
    private val loan = FinancialObligationEntity("loan", "وام", "LOAN", 101, start, 3, "", start)
    private val goal = SavingsGoalEntity("g", "سفر", "savings", 50, start)

    @Test fun includesOverdueAndDueTodayAndCapsFundedGoals() {
        val result = spendableBalance(mapOf("wallet" to 200L, "savings" to 70L), listOf(goal),
            listOf(loan, loan.copy(id = "receivable", kind = "RECEIVABLE")), emptyList(),
            jalaliDueDayEndExclusive(second))
        assertEquals(152.toBigInteger(), result.available)
        assertEquals(68L, result.dues.single().amount)
        assertEquals(50L, result.goals.single().amount)
    }

    @Test fun partialAndFullPaymentsReduceCashAndReserveTogether() {
        fun calculate(amount: Long) = spendableBalance(mapOf("wallet" to 100L - amount), emptyList(),
            listOf(loan), if (amount == 0L) emptyList() else listOf(
                ObligationPaymentEntity("p", loan.id, amount, start)), second)
        assertEquals(66.toBigInteger(), calculate(0).available)
        assertEquals(calculate(0).available, calculate(12).available)
        assertEquals(calculate(0).available, calculate(34).available)
        assertEquals(emptyList<CashReserve>(), calculate(34).dues)
    }

    @Test fun deficitAndUnfundedGoalAreReportedWithoutInventingIncome() {
        val result = spendableBalance(mapOf("savings" to -5L), listOf(goal), listOf(loan),
            emptyList(), jalaliDueDayEndExclusive(start))
        assertEquals((-39).toBigInteger(), result.available)
        assertEquals(emptyList<CashReserve>(), result.goals)
    }
}
