package ir.jibik.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JibikFinancialDetailLayoutTest {
    @Test fun normalFontAndComfortableWidthRemainInline() {
        assertFalse(stackJibikFinancialDetail(360f, 1f, 17))
    }

    @Test fun narrowContentAvoidsCrushingRtlAmounts() {
        assertTrue(stackJibikFinancialDetail(286f, 1f, 17))
    }

    @Test fun largeFontStacksFinancialLabelAndValue() {
        assertTrue(stackJibikFinancialDetail(400f, 1.5f, 17))
    }

    @Test fun longAmountsStackEvenOnTablets() {
        assertTrue(stackJibikFinancialDetail(500f, 1f, 28))
    }
}
