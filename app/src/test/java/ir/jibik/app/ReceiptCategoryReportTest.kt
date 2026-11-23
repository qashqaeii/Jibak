package ir.jibik.app

import ir.jibik.core.finance.CategoryExpense
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import ir.jibik.core.finance.periodTotals
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptCategoryReportTest {
    private val start = 1000L
    private val end = 2000L

    @Test fun splitsOneReceiptIntoRealCategoriesWithoutMultiplyingCashOutflow() {
        val trip = LedgerRecord(
            "trip", LedgerKind.EXPENSE, 350_000L,
            fromAccountId = "wallet", category = "خرید منزل", timestampMillis = 1100L,
        )
        val rent = LedgerRecord(
            "rent", LedgerKind.EXPENSE, 600_000L,
            fromAccountId = "wallet", category = "اجاره", timestampMillis = 1200L,
        )
        val transfer = LedgerRecord(
            "transfer", LedgerKind.TRANSFER, 2_000_000L,
            fromAccountId = "wallet", toAccountId = "savings",
            timestampMillis = 1300L,
        )
        val savedItems = mapOf(
            "trip" to listOf(
                PurchaseLine("برنج", "برنج", 1, 150_000L),
                PurchaseLine("میوه", "میوه", 2, 100_000L),
            )
        )
        val rows = detailedCategoryExpenses(
            listOf(trip, rent, transfer), savedItems, start, end
        )
        assertEquals(
            listOf(
                CategoryExpense("اجاره", 600_000L),
                CategoryExpense("میوه", 200_000L),
                CategoryExpense("برنج", 150_000L),
            ),
            rows,
        )
        assertEquals(
            periodTotals(listOf(trip, rent, transfer), start, end).expenseToman,
            rows.sumOf { it.amountToman },
        )
    }

    @Test fun missingOrInvalidReceiptKeepsImmutableLedgerCategory() {
        val record = LedgerRecord(
            "receipt", LedgerKind.EXPENSE, 80_000L,
            fromAccountId = "wallet", category = "خرید منزل",
            timestampMillis = 1100L,
        )
        assertEquals(
            listOf(CategoryExpense("خرید منزل", 80_000L)),
            detailedCategoryExpenses(listOf(record), emptyMap(), start, end),
        )
        assertEquals(
            listOf(CategoryExpense("خرید منزل", 80_000L)),
            detailedCategoryExpenses(
                listOf(record),
                mapOf("receipt" to listOf(
                    PurchaseLine("کالا", "خوراک", 1, 50_000L),
                )),
                start, end,
            ),
        )
    }

    @Test fun excludesOtherMonthsAndPreventsDoubleCounting() {
        val previous = LedgerRecord(
            "old", LedgerKind.EXPENSE, 123L,
            fromAccountId = "wallet", category = "خوراک", timestampMillis = start - 1,
        )
        val next = previous.copy(id = "next", timestampMillis = end)
        assertEquals(
            emptyList<CategoryExpense>(),
            detailedCategoryExpenses(listOf(previous, next), emptyMap(), start, end),
        )
    }
}
