package ir.jibik.app

import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class ReportAnalyticsTest {
    private fun day(s: String) = requireNotNull(parseJalaliDueDate(s))
    private val today = day("1403/07/05")

    @Test fun weekStartsOnSaturdayAndEndsAfterToday() {
        val week = reportRange("WEEK", today)
        assertEquals(day("1403/06/31"), week.start)
        assertEquals(day("1403/07/06"), week.endExclusive)
        assertEquals(day("1403/06/25"), previousReportRange(week).start)
        assertEquals(week.start, previousReportRange(week).endExclusive)
        assertEquals(day("1403/01/01"), reportRange("YEAR", today).start)
        assertEquals(day("1403/07/01"), reportRange("MONTH", today).start)
    }

    @Test fun halfOpenRangeAndTrendNeverCountTransferOrDuplicateBoundaries() {
        val r = reportRange("MONTH", today)
        val rows = listOf(
            LedgerRecord("i", LedgerKind.INCOME, 100, toAccountId = "wallet", category = "حقوق", timestampMillis = r.start),
            LedgerRecord("e", LedgerKind.EXPENSE, 30, fromAccountId = "wallet", category = "خرید", timestampMillis = r.endExclusive - 1),
            LedgerRecord("t", LedgerKind.TRANSFER, 25, fromAccountId = "wallet", toAccountId = "bank", timestampMillis = r.start),
            LedgerRecord("next", LedgerKind.INCOME, 999, toAccountId = "wallet", timestampMillis = r.endExclusive)
        )
        val totals = reportTotals(rows, r)
        assertEquals(100.toBigInteger(), totals.income)
        assertEquals(30.toBigInteger(), totals.expense)
        assertEquals(2, totals.count)
        assertEquals("70.0", totals.savingsRate.toString())
        val buckets = reportTrend(rows, r)
        assertEquals(5, buckets.size)
        assertEquals(r.start, buckets.first().range.start)
        assertEquals(r.endExclusive, buckets.last().range.endExclusive)
        assertTrue(buckets.zipWithNext().all { (a,b) -> a.range.endExclusive == b.range.start })
        assertEquals(totals.net, buckets.fold(java.math.BigInteger.ZERO) { sum, b -> sum + b.totals.net })
        assertNull(reportTotals(emptyList(), r).savingsRate)
    }
}
