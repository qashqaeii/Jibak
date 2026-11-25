package ir.jibik.app

import org.junit.Assert.*
import org.junit.Test

class UserSharedBankSmsTest {
    private val body = "بانك ملي ايران\nخريداينترنتي:1,980,000-\nحساب:04004\nمانده:14,730,780\n0829-17:49"
    private val at = 1800000000000L
    @Test fun userShareHasStableMinuteIdentityAndExactAmount() {
        val a = requireNotNull(parseUserSharedBankSms(body, at, "local-test-salt"))
        val b = requireNotNull(parseUserSharedBankSms(body, at + 20_000L, "local-test-salt"))
        assertEquals(a.fingerprint, b.fingerprint)
        assertEquals(198000L, a.amountToman)
        assertFalse(a.income)
        assertEquals(USER_SHARED_BANK_SENDER, a.sender)
        assertNotEquals(a.fingerprint, parseUserSharedBankSms(body, at + 60_000L, "local-test-salt")?.fingerprint)
    }
    @Test fun rejectsOtpMalformedAndInvalidClock() {
        assertNull(parseUserSharedBankSms(body + "\nرمز پویا: 12345", at, "local-test-salt"))
        assertNull(parseUserSharedBankSms(body.replace("1,980,000", "1,98,000"), at, "local-test-salt"))
        assertEquals(at + (17 * 60 + 49) * 60_000L, manualBankTimestamp(at, "۱۷:۴۹"))
        assertNull(manualBankTimestamp(at, "24:00"))
        assertNull(manualBankTimestamp(at, "12:99"))
    }
}
