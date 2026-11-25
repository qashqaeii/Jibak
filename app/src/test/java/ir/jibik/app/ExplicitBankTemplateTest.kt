package ir.jibik.app
import org.junit.Assert.*
import org.junit.Test
class ExplicitBankTemplateTest {
    private val sample = "بانک ملت\nبرداشت\nمبلغ: ۱۲۰٬۰۰۰ ریال\nحساب: ۰۴۰۰۴\nمانده: ۵۰۰٬۰۰۰ ریال"
    private fun key(body: String): String? = BankSmsParser.parse("10001", body, 1800000000000L,
        "local-test-salt", "mellat")?.let { strictBankAutoTemplate(body, it) }
    @Test fun directionUnitAndBankAreBoundToTemplate() {
        val expected = "explicit-v1:mellat:برداشت:ریال"
        assertEquals(expected, key(sample))
        assertTrue(isSupportedAutoTemplate("mellat", expected))
        assertFalse(isSupportedAutoTemplate("melli", expected))
        assertEquals("explicit-v1:mellat:برداشت:تومان", key(sample.replace("ریال", "تومان")))
        assertNull(key(sample.replace("مانده:", "مبلغ:")))
        assertNull(key(sample + "\nرمز پویا: 123"))
        assertNull(key(sample.replace("برداشت", "انتقال")))
    }
}
