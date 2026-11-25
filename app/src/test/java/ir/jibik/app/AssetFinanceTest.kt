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

    @Test fun salesSummarySeparatesUnknownCostAndUnrealizedChanges() {
        fun sold(id: String, cost: Long?, proceeds: Long): AssetPosition {
            val asset = holding.copy(id = id, costBasisToman = cost)
            return assetPositions(listOf(asset), listOf(
                AssetEvent(1, id, "VALUE", 100, now + 1, ""),
                AssetEvent(2, id, "SALE", proceeds, now + 2, "", "tx-$id")
            )).single()
        }
        val summary = summarizeAssetSales(listOf(sold("profit", 200, 350),
            sold("loss", 500, 300), sold("unknown", null, 900),
            positions(value(1, "VALUE", 1000)).single()))
        assertEquals(1550.toBigInteger(), summary.proceeds)
        assertEquals(700.toBigInteger(), summary.knownCost)
        assertEquals((-50).toBigInteger(), summary.realizedDifference)
        assertEquals(2, summary.pricedSales)
        assertEquals(1, summary.unpricedSales)
        val wide = summarizeAssetSales(listOf(sold("a", 0, Long.MAX_VALUE), sold("b", 0, Long.MAX_VALUE)))
        assertEquals(Long.MAX_VALUE.toBigInteger() * 2.toBigInteger(), wide.realizedDifference)
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
