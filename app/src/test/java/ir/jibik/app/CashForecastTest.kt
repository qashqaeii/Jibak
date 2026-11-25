package ir.jibik.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class CashForecastTest {
    private fun day(s: String) = requireNotNull(parseJalaliDueDate(s))
    private val today = day("1405/08/10")
    private val end = jalaliDueDayEndExclusive(day("1405/08/19"))

    @Test fun dailyLimitExcludesFutureIncomeAndAccountsForPartialPayments() {
        val due = day("1405/08/12")
        val loan = FinancialObligationEntity("loan", "وام", "LOAN", 400, due, 1, "", today)
        val paid = ObligationPaymentEntity("tx", "loan", 100, today)
        val income = RecurringRule("salary", "حقوق", true, "wallet", 10000, today)
        val expense = RecurringRule("expense", "هزینه", false, "wallet", 100, today, frequency = "WEEKLY")
        val skipped = RecurringDecision("expense", today, null, null)
        val envelope = MoneyEnvelope("env", "سفر", "wallet", 500, 200, today)
        val f = cashForecast(mapOf("wallet" to 1000L), emptyList(), listOf(loan), listOf(paid),
            listOf(income, expense), listOf(skipped), listOf(envelope), today, end)
        assertEquals(400.toBigInteger(), f.available)
        assertEquals(10, f.days)
        assertEquals(40.toBigInteger(), f.dailyLimit)
        assertNull(f.firstDeficitDay)
    }

    @Test fun firstDeficitIsExactDueDayAndArrearsAreImmediate() {
        val due = day("1405/08/15")
        val loan = FinancialObligationEntity("loan", "وام", "LOAN", 600, due, 1, "", today)
        val repeat = RecurringRule("r", "اجاره", false, "wallet", 500, day("1405/08/13"))
        val f = cashForecast(mapOf("wallet" to 1000L), emptyList(), listOf(loan), emptyList(),
            listOf(repeat), emptyList(), emptyList(), today, end)
        assertEquals((-100).toBigInteger(), f.available)
        assertEquals(due, f.firstDeficitDay)
        assertEquals(0.toBigInteger(), f.dailyLimit)
        val overdue = loan.copy(dueAtMillis = day("1405/08/01"), totalToman = 1200)
        assertEquals(today, cashForecast(mapOf("wallet" to 1000L), emptyList(), listOf(overdue), emptyList(),
            emptyList(), emptyList(), emptyList(), today, end).firstDeficitDay)
    }

    @Test fun simulationDoesNotSilentlyReleaseProtectedMoney() {
        val balances = mapOf("wallet" to 900L, "bank" to 100L)
        val envelope = MoneyEnvelope("env", "سفر", "wallet", 900, 800, today)
        val f = cashForecast(balances, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            listOf(envelope), today, end)
        val wrongAccount = simulatePurchase(f, "wallet", 150, balances, emptyList(), listOf(envelope))
        assertFalse(wrongAccount.fits)
        assertEquals(50.toBigInteger(), wrongAccount.accountShortfall)
        assertEquals(50.toBigInteger(), wrongAccount.after)
        val valid = simulatePurchase(f, "bank", 100, balances, emptyList(), listOf(envelope))
        assertTrue(valid.fits)
        assertEquals(10.toBigInteger(), valid.dailyAfter)
        val tooMuch = simulatePurchase(f, "wallet", 250, balances, emptyList(), listOf(envelope))
        assertEquals(50.toBigInteger(), tooMuch.planShortfall)
        assertFalse(tooMuch.fits)
    }

    @Test fun zeroBalanceIsNotDeficitAndSingleDayIsIncluded() {
        val f = cashForecast(mapOf("wallet" to 0L), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), today, jalaliDueDayEndExclusive(today))
        assertEquals(1, f.days)
        assertEquals(0.toBigInteger(), f.dailyLimit)
        assertNull(f.firstDeficitDay)
    }
}
