package ir.jibik.app

import org.junit.Assert.*
import org.junit.Test

class AssetFinanceTest {
    private val now = 1700000000000L
    private val holding = AssetHolding("gold", "سکه", "GOLD", 200L, now)
    private fun value(id: Long, kind: String, amount: Long) =
        AssetEvent(id, holding.id, kind, amount, now + id, "")
    private fun positions(vararg events: AssetEvent) = assetPositions(listOf(holding), events.toList())

    @Test fun netWorthDoesNotCountCashOrDebtTwice() {
        val loan = FinancialObligationEntity("loan", "وام", "LOAN", 400, now, 1, "", now)
        val receivable = FinancialObligationEntity("recv", "طلب", "RECEIVABLE", 150, now, 1, "", now)
        val payments = listOf(ObligationPaymentEntity("tx1", "loan", 100, now),
            ObligationPaymentEntity("tx2", "recv", 50, now))
        val f = netWorth(mapOf("wallet" to 1000L, "overdrawn" to -80L),
            positions(value(1, "VALUE", 300)), listOf(loan, receivable), payments)
        assertEquals(1400.toBigInteger(), f.assets)
        assertEquals(380.toBigInteger(), f.liabilities)
        assertEquals(1020.toBigInteger(), f.net)
    }

    @Test fun malformedHistoryRejectedAndZeroValueAllowed() {
        assertTrue(runCatching { positions(value(1, "ARCHIVE", 100)) }.isFailure)
        assertTrue(runCatching { positions(value(1, "VALUE", 100), value(2, "ARCHIVE", 200)) }.isFailure)
        assertTrue(runCatching { positions(value(1, "VALUE", 100), value(2, "ARCHIVE", 100), value(3, "VALUE", 200)) }.isFailure)
        assertEquals(0L, positions(value(1, "VALUE", 0)).single().valueToman)
        assertEquals(2.toBigInteger() * Long.MAX_VALUE.toBigInteger(),
            netWorth(mapOf("a" to Long.MAX_VALUE, "b" to Long.MAX_VALUE), emptyList(), emptyList(), emptyList()).net)
    }
}
