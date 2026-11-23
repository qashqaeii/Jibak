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

    @Test fun threeLevelDrillDownConservesEveryTomanIncludingDirectParentExpenses() {
        val items = listOf(
            CategoryExpense("خوراک / خرید مواد غذایی / حبوبات", 30_000L),
            CategoryExpense("خوراک / رستوران و غذای بیرون / کافه", 20_000L),
            CategoryExpense("خوراک", 15_000L),
            CategoryExpense("مسکن / اجاره", 50_000L),
        )
        val roots = categoryReportRows(items)
        assertEquals(115_000L, roots.sumOf { it.amountToman })
        assertEquals(65_000L, roots.first { it.path == "خوراک" }.amountToman)
        assertEquals(50_000L, roots.first { it.path == "مسکن" }.amountToman)
        val children = categoryReportRows(items, "خوراک")
        assertEquals(65_000L, children.sumOf { it.amountToman })
        assertEquals(15_000L, children.single { it.direct }.amountToman)
        assertEquals(30_000L, children.single { it.path == "خوراک / خرید مواد غذایی" }.amountToman)
        val leaves = categoryReportRows(items, "خوراک / خرید مواد غذایی")
        assertEquals(1, leaves.size)
        assertEquals("حبوبات", leaves.single().title)
        assertEquals(30_000L, leaves.single().amountToman)
        assertEquals(0L, categoryReportRows(items, "سلامت").sumOf { it.amountToman })
    }

    @Test fun classifiedReceiptContributionsPreserveAmountsAndTransactionIdentity() {
        val trip = LedgerRecord("trip2", LedgerKind.EXPENSE, 70_000L,
            fromAccountId = "wallet", category = "خرید منزل", timestampMillis = 1100L)
        val groceries = mapOf("trip2" to listOf(
            PurchaseLine("میوه", "خوراک / خرید مواد غذایی / میوه", 2, 20_000L),
            PurchaseLine("لبنیات", "خوراک / خرید مواد غذایی / لبنیات", 1, 30_000L),
        ))
        val classified = classifiedExpenseLines(listOf(trip), groceries, start, end)
        assertEquals(70_000L, classified.sumOf { it.amountToman })
        assertEquals(2, classified.size)
        assertEquals(1, classified.map { it.record.id }.distinct().size)
        val fruitOnly = categoryReportContributions(classified,
            "خوراک / خرید مواد غذایی / میوه", directOnly = true)
        assertEquals(40_000L, fruitOnly.sumOf { it.amountToman })
        val allFood = categoryReportContributions(classified, "خوراک", directOnly = false)
        assertEquals(70_000L, allFood.sumOf { it.amountToman })
    }

    @Test fun incomeCategoriesGroupWithoutCountingTransfersOrExpenses() {
        val salary = LedgerRecord(
            "salary", LedgerKind.INCOME, 1_000_000L,
            toAccountId = "wallet",
            category = "حقوق و دستمزد / دریافتی شغلی / حقوق ثابت",
            timestampMillis = 1200L,
        )
        val bonus = salary.copy(
            id = "bonus", amountToman = 200_000L,
            category = "حقوق و دستمزد / دریافتی شغلی / پاداش",
        )
        val transfer = salary.copy(
            id = "transfer", kind = LedgerKind.TRANSFER,
            amountToman = 300_000L, fromAccountId = "wallet",
        )
        val income = detailedIncomeCategories(
            listOf(salary, bonus, transfer), start, end,
        )
        assertEquals(1_200_000L, income.sumOf { it.amountToman })
        val roots = categoryReportRows(income)
        assertEquals(1, roots.size)
        assertEquals(1_200_000L, roots.first().amountToman)
        assertEquals(2, categoryReportContributions(
            classifiedIncomeLines(listOf(salary, bonus, transfer), start, end),
            "حقوق و دستمزد", directOnly = false,
        ).size)
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
