package ir.jibik.app

import ir.jibik.core.finance.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class FinanceManagementTest {
    @Test fun carryUsesOnlyPreviousBaseWithoutCompounding() {
        val today = requireNotNull(parseJalaliDueDate("1403/07/05"))
        val start = reportRange("WEEK", today).start
        val prev = offsetJalaliDay(start, -7)
        val older = offsetJalaliDay(start, -14)
        val plans = listOf(WeeklyBudget(older, 500, false), WeeklyBudget(prev, 100, true), WeeklyBudget(start, 200, true))
        val records = listOf(LedgerRecord("e", LedgerKind.EXPENSE, 30, fromAccountId = "wallet", timestampMillis = prev),
            LedgerRecord("e2", LedgerKind.EXPENSE, 50, fromAccountId = "wallet", timestampMillis = start))
        val result = weeklyBudgetUsage(start, plans, records)
        assertEquals(70.toBigInteger(), result.carried)
        assertEquals(220.toBigInteger(), result.remaining)
        assertEquals(0.toBigInteger(), weeklyBudgetUsage(start, plans.map { it.copy(carryPrevious = false) }, records).carried)
        assertTrue(runCatching { validateWeeklyBudget(WeeklyBudget(today, 100, true)) }.isFailure)
    }
}
