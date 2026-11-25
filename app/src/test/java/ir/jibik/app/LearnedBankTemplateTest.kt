package ir.jibik.app

import org.junit.Assert.*
import org.junit.Test

class LearnedBankTemplateTest {
    private val sample = "بانک نمونه\nبرداشت: ۱۵۰٬۰۰۰ تومان\nکارت: 12344004\nمانده: ۵۰۰٬۰۰۰ تومان\nساعت: ۱۲:۰۹"
    private fun parse(body: String) = BankSmsParser.parse("10001", body, 1800000000000L, "local-test-salt", "custom")
    private fun key(body: String) = parse(body)?.let { strictBankAutoTemplate(body, it) }
    @Test fun structuralTemplatePreservesDirectionUnitAndHeader() {
        val trained = requireNotNull(key(sample))
        assertTrue(isLearnedBankTemplate("custom", trained))
        assertEquals(trained, key(sample.replace("۱۵۰٬۰۰۰", "۲۵۰٬۰۰۰").replace("۵۰۰٬۰۰۰", "۳۰۰٬۰۰۰")))
        assertNotEquals(trained, key(sample.replace("بانک نمونه", "بانک جدید")))
        assertNotEquals(trained, key(sample.replace("تومان", "ریال")))
        assertNull(key(sample.replace("۱۵۰٬۰۰۰", "۱۵٬۰۰")))
        assertNull(key(sample.replace("برداشت", "انتقال")))
        assertNull(key(sample + "\nرمز پویا: 12345"))
        assertNull(key(sample + "\nفیلد ناشناخته"))
        assertNull(BankSmsParser.parse("10001", sample, 1800000000000L, "local-test-salt"))
    }
    @Test fun neverStoresSampleValuesInKey() {
        val key = requireNotNull(key(sample))
        assertFalse(key.contains("12344004"))
        assertFalse(key.contains("بانک نمونه"))
        assertFalse(isSupportedAutoTemplate("melli", key))
    }
}
