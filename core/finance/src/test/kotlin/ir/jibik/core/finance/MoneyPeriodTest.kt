package ir.jibik.core.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyPeriodTest {
    private val start = 1_800_000_000_000L
    private val end = start + 30L * 24 * 60 * 60 * 1000

    @Test fun includesStartAndExcludesNextMonthStart() {
        val records = listOf(
            LedgerRecord("1", LedgerKind.EXPENSE, 70_000, fromAccountId = "wallet", timestampMillis = start - 1),
            LedgerRecord("2", LedgerKind.EXPENSE, 120_000, fromAccountId = "wallet", timestampMillis = start),
            LedgerRecord("3", LedgerKind.INCOME, 850_000, toAccountId = "wallet", timestampMillis = end - 1),
            LedgerRecord("4", LedgerKind.EXPENSE, 999_000, fromAccountId = "wallet", timestampMillis = end),
        )
        assertEquals(PeriodTotals(850_000, 120_000), periodTotals(records, start, end))
    }

    @Test fun ignoresTransfersWithoutChangingIncomeExpense() {
        val entries = listOf(
            LedgerRecord("tr", LedgerKind.TRANSFER, 2_500_000, "wallet", "saving", timestampMillis = start + 1),
            LedgerRecord("inc", LedgerKind.INCOME, 1_400_000, toAccountId = "wallet", timestampMillis = start + 2),
            LedgerRecord("exp", LedgerKind.EXPENSE, 400_000, fromAccountId = "wallet", timestampMillis = start + 3),
        )
        assertEquals(PeriodTotals(1_400_000, 400_000), periodTotals(entries, start, end))
    }

    @Test fun rejectsBrokenBoundaries() {
        assertThrows(IllegalArgumentException::class.java) {
            periodTotals(emptyList(), end, start)
        }
        assertThrows(IllegalArgumentException::class.java) {
            periodTotals(emptyList(), start, start)
        }
    }

    @Test fun refusesLongOverflowInsteadOfShowingWrongFinancialTotals() {
        val entries = listOf(
            LedgerRecord("1", LedgerKind.INCOME, Long.MAX_VALUE, toAccountId = "wallet", timestampMillis = start),
            LedgerRecord("2", LedgerKind.INCOME, 1, toAccountId = "wallet", timestampMillis = start + 1),
        )
        assertThrows(ArithmeticException::class.java) {
            periodTotals(entries, start, end)
        }
    }
    @Test fun groupsExpenseCategoriesWithoutCountingTransfers() {
        val records = listOf(
            LedgerRecord("1", LedgerKind.EXPENSE, 40_000, fromAccountId = "wallet", category = "خوراک", timestampMillis = start),
            LedgerRecord("2", LedgerKind.EXPENSE, 65_000, fromAccountId = "wallet", category = "خوراک", timestampMillis = start + 1),
            LedgerRecord("3", LedgerKind.EXPENSE, 80_000, fromAccountId = "wallet", category = "قبوض", timestampMillis = start + 2),
            LedgerRecord("4", LedgerKind.TRANSFER, 2_000_000, "wallet", "saving", category = "قبوض", timestampMillis = start + 3),
            LedgerRecord("5", LedgerKind.EXPENSE, 10_000, fromAccountId = "wallet", category = "خوراک", timestampMillis = end),
        )
        assertEquals(
            listOf(CategoryExpense("خوراک", 105_000), CategoryExpense("قبوض", 80_000)),
            categoryExpenses(records, start, end),
        )
    }

    @Test fun emptyCategoryBecomesOtherAndDoesNotLoseExpense() {
        val records = listOf(
            LedgerRecord("uncat", LedgerKind.EXPENSE, 25_000, fromAccountId = "wallet", timestampMillis = start)
        )
        assertEquals(
            listOf(CategoryExpense("سایر هزینه‌ها", 25_000)),
            categoryExpenses(records, start, end),
        )
    }
}
