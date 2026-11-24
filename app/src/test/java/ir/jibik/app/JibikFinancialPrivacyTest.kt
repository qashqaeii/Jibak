package ir.jibik.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JibikFinancialPrivacyTest {
    @Test fun concealedAmountsNeverReachVisibleText() {
        val formatted = "۵٬۲۵۰٬۰۰۰ تومان"
        val hidden = jibikPrivateAmount(formatted, visible = false)
        assertEquals("••••••", hidden)
        assertFalse(hidden.contains("۵"))
        assertFalse(hidden.contains("تومان"))
    }

    @Test fun showingAmountsDoesNotChangeFormatting() {
        val formatted = "۱۲٬۳۰۰ تومان"
        assertEquals(formatted, jibikPrivateAmount(formatted, visible = true))
    }
}
