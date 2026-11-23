package ir.jibik.app

import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import org.junit.Assert.assertThrows
import org.junit.Test

class PurchaseLineTest {
    private val payment = LedgerRecord(
        id = "receipt-1",
        kind = LedgerKind.EXPENSE,
        amountToman = 350_000L,
        fromAccountId = "wallet",
        category = "خرید منزل",
    )

    @Test fun exactItemTotalsRepresentOnePayment() {
        validateItemizedPurchase(
            payment,
            listOf(
                PurchaseLine("برنج", "خوراک / خرید مواد غذایی / برنج، نان و ماکارونی", 1, 150_000L),
                PurchaseLine("میوه", "خوراک / خرید مواد غذایی / میوه", 2, 100_000L),
            ),
        )
    }

    @Test fun rejectsMismatchedReceiptsAndInvalidKinds() {
        val items = listOf(PurchaseLine("میوه", "خوراک", 2, 100_000L))
        assertThrows(IllegalArgumentException::class.java) {
            validateItemizedPurchase(payment, items)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateItemizedPurchase(payment.copy(kind = LedgerKind.TRANSFER), items)
        }
    }

    @Test fun rejectsMalformedItems() {
        val lines = listOf(PurchaseLine("برنج", "خوراک", 1, 350_000L))
        assertThrows(IllegalArgumentException::class.java) {
            validateItemizedPurchase(payment, lines.map { it.copy(quantity = 0) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateItemizedPurchase(payment, lines.map { it.copy(title = "") })
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateItemizedPurchase(payment, emptyList())
        }
    }

    @Test fun cannotOverflowReceiptTotals() {
        val lines = listOf(
            PurchaseLine("بسیار گران", "آزمایشی", 2, Long.MAX_VALUE),
        )
        assertThrows(ArithmeticException::class.java) {
            validateItemizedPurchase(payment, lines)
        }
    }
}
