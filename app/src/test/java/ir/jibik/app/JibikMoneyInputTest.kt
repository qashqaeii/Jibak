package ir.jibik.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JibikMoneyInputTest {
    @Test fun acceptsAsciiArabicAndPersianNumerals() {
        assertEquals("1234567", normalizeTomanDraft("۱٬۲۳۴,٥٦٧"))
        assertEquals(1_234_567L, parseTomanDraft("۱٬۲۳۴,٥٦٧"))
        assertEquals(890L, parseTomanDraft("۸۹۰"))
    }

    @Test fun moneyParserNeverTurnsNegativeOrFractionIntoValidCharge() {
        assertNull(parseTomanDraft("-1200"))
        assertNull(parseTomanDraft("1.5"))
        assertNull(parseTomanDraft(""))
        assertNull(parseTomanDraft("9223372036854775808"))
        assertNull(parseTomanDraft("NaN"))
    }

    @Test fun thousandsGroupingIsPresentationOnly() {
        assertEquals("۱٬۲۳۴٬۵۶۷", groupDigits("۱۲۳۴۵۶۷"))
        assertEquals("12", groupDigits("12"))
        assertEquals("1٬000", groupDigits("1000"))
    }

    @Test fun rejectsNegativeFractionalOrAlphabeticUserInput() {
        assertFalse(isAllowedTomanDraft("-1200"))
        assertFalse(isAllowedTomanDraft("1.5"))
        assertFalse(isAllowedTomanDraft("تومان"))
        assertTrue(isAllowedTomanDraft("۱٬۲۳۴,٥٦٧"))
    }

    @Test fun asciiDraftHasNineteenDigitMaximum() {
        assertEquals(19, normalizeTomanDraft("12345678901234567890000").length)
        assertEquals(0L, parseTomanDraft("۰"))
        assertTrue(parseTomanDraft("12345")!! > 0L)
    }
}
