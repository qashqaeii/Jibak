package ir.jibik.app

import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionFiltersTest {
    private val expense = LedgerRecord(
        id = "receipt", kind = LedgerKind.EXPENSE, amountToman = 150_000L,
        fromAccountId = "wallet", category = "خرید منزل", note = "فروشگاه",
    )
    private val lines = mapOf("receipt" to listOf(
        PurchaseLine("سیب", "خوراک / خرید مواد غذایی / میوه", 2, 25_000L),
        PurchaseLine("شیر", "خوراک / خرید مواد غذایی / لبنیات", 2, 50_000L),
    ))

    @Test fun filtersParentAndLeafFromOneImmutableReceipt() {
        for (path in listOf(
            "خوراک", "خوراک / خرید مواد غذایی",
            "خوراک / خرید مواد غذایی / میوه",
        )) {
            assertEquals(listOf(expense), filterLocalTransactions(
                listOf(expense), lines, "هزینه", path, "",
            ))
        }
        assertEquals(emptyList<LedgerRecord>(), filterLocalTransactions(
            listOf(expense), lines, "هزینه", "مسکن", "",
        ))
        assertTrue(categoryUnderPath("خوراک / خرید مواد غذایی / میوه", "خوراک"))
        assertFalse(categoryUnderPath("خوراکی", "خوراک"))
    }

    @Test fun itemNameAndPersianAmountAreSearchable() {
        assertEquals(listOf(expense), filterLocalTransactions(
            listOf(expense), lines, "همه", "", "سیب",
        ))
        assertEquals(listOf(expense), filterLocalTransactions(
            listOf(expense), lines, "هزینه", "", "۱۵۰۰۰۰",
        ))
        assertEquals(emptyList<LedgerRecord>(), filterLocalTransactions(
            listOf(expense), lines, "درآمد", "", "",
        ))
    }

    @Test fun invalidReceiptNeverChangesCategoryMatch() {
        val corrupt = mapOf("receipt" to listOf(lines.getValue("receipt").first()))
        assertEquals(emptyList<LedgerRecord>(), filterLocalTransactions(
            listOf(expense), corrupt, "هزینه",
            "خوراک / خرید مواد غذایی / میوه", "",
        ))
        assertEquals(listOf(expense), filterLocalTransactions(
            listOf(expense), corrupt, "هزینه", "خرید منزل", "",
        ))
    }
}
