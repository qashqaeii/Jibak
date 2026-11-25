package ir.jibik.app

import org.junit.Assert.*
import org.junit.Test

class BankAutoTemplateTest {
    private val body = "بانك ملي ايران\nخريداينترنتي:1,980,000-\nحساب:04004\nمانده:14,730,787\n0829-17:49"
    private fun key(text: String): String? {
        val match = BankSmsParser.parse("10001", text, 1800000000000L, "local-test-salt", "melli") ?: return null
        return strictBankAutoTemplate(text, match)
    }
    @Test fun onlyExplicitConfirmedTemplateIsEligible() {
        assertEquals("melli-signed-rial-v1:خریداینترنتی", key(body))
        assertEquals("melli-signed-rial-v1:خریداینترنتی", key(body.replace("1,980,000", "2,500,000")))
        assertNull(key(body.replace("خريداينترنتي", "انتقال")))
        assertNull(key(body.replace("1,980,000-", "1,980,000+")))
        assertNull(key(body.replace("1,980,000", "1,98,000")))
        assertNull(key(body + "\nرمز پویا: 12345"))
        assertNull(key(body + "\nتراکنش ناموفق"))
    }
}
