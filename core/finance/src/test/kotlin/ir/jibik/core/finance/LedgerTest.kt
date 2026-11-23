package ir.jibik.core.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LedgerTest {
    private val accounts = listOf(
        Account("personal", "شخصی", 1_000_000),
        Account("shared", "مشترک", 200_000),
    )

    @Test fun transferNeverBecomesIncomeOrExpense() {
        val entries = listOf(
            LedgerRecord("1", LedgerKind.TRANSFER, 100_000, fromAccountId = "personal", toAccountId = "shared")
        )
        assertEquals(mapOf("personal" to 900_000L, "shared" to 300_000L), FinanceEngine.balances(accounts, entries))
        assertEquals(MoneySummary(1_200_000, 0, 0), FinanceEngine.summary(accounts, entries))
    }

    @Test fun ordinaryExpenseAndIncomeAffectBalanceOnce() {
        val entries = listOf(
            LedgerRecord("1", LedgerKind.EXPENSE, 30_000, fromAccountId = "personal"),
            LedgerRecord("2", LedgerKind.INCOME, 80_000, toAccountId = "personal"),
        )
        assertEquals(MoneySummary(1_250_000, 80_000, 30_000), FinanceEngine.summary(accounts, entries))
    }

    @Test fun rejectsSameAccountTransfer() {
        val entries = listOf(
            LedgerRecord("1", LedgerKind.TRANSFER, 500, fromAccountId = "personal", toAccountId = "personal")
        )
        assertThrows(IllegalArgumentException::class.java) { FinanceEngine.balances(accounts, entries) }
    }

    @Test fun rejectsZeroOrNegativeAmounts() {
        val entries = listOf(LedgerRecord("1", LedgerKind.EXPENSE, 0, fromAccountId = "personal"))
        assertThrows(IllegalArgumentException::class.java) { FinanceEngine.summary(accounts, entries) }
    }

    @Test fun itemizedPurchaseIsOnePayment() {
        val items = listOf(ShoppingItem("برنج", 1, 120_000), ShoppingItem("میوه", 2, 40_000))
        val purchase = LedgerRecord("shop-1", LedgerKind.EXPENSE, itemizedPurchaseTotal(items), fromAccountId = "personal")
        assertEquals(200_000L, purchase.amountToman)
        assertEquals(200_000L, FinanceEngine.summary(accounts, listOf(purchase)).expenseToman)
    }

    @Test fun duplicateIdsAreRejected() {
        val record = LedgerRecord("dup", LedgerKind.INCOME, 100, toAccountId = "personal")
        assertThrows(IllegalArgumentException::class.java) {
            FinanceEngine.balances(accounts, listOf(record, record))
        }
    }
}
