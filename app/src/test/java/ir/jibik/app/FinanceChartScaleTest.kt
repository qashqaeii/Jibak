package ir.jibik.app

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class FinanceChartScaleTest {
    @Test fun negativeOnlyHasZeroAtTop() {
        val scale = financeChartScale(listOf((-100).toBigInteger(), (-50).toBigInteger()))
        assertEquals(1f, scale.fraction(BigInteger.ZERO), .0001f)
        assertEquals(0f, scale.fraction((-100).toBigInteger()), .0001f)
    }
    @Test fun mixedRangeAndWideAmountsStayFinite() {
        val large = Long.MAX_VALUE.toBigInteger() * 100.toBigInteger()
        val scale = financeChartScale(listOf(large.negate(), large))
        assertEquals(.5f, scale.fraction(BigInteger.ZERO), .0001f)
        assertEquals(1f, scale.fraction(large), .0001f)
        assertEquals(0f, scale.fraction(large.negate()), .0001f)
    }
    @Test fun emptyAndZeroChartsHaveNonzeroScale() {
        val scale = financeChartScale(emptyList())
        assertEquals(0f, scale.fraction(BigInteger.ZERO), .0001f)
        assertTrue(scale.high > scale.low)
    }
}
