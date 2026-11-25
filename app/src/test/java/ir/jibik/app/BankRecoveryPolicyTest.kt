package ir.jibik.app

import org.junit.Assert.*
import org.junit.Test

class BankRecoveryPolicyTest {
    @Test fun sentTimestampMatchesBroadcastAndInvalidValuesFallBack() {
        assertEquals(1000L, canonicalBankSmsTimestamp(1000L, 2000L))
        assertEquals(2000L, canonicalBankSmsTimestamp(0L, 2000L))
        assertEquals(2000L, canonicalBankSmsTimestamp(999999L, 2000L))
    }
    @Test fun neverScanBeforeConsentOrOutsideBoundedWindow() {
        val now = 1800000000000L
        assertEquals(now - 1000L, bankRecoveryStart(now - 1000L, now))
        assertEquals(now - 604800000L, bankRecoveryStart(1L, now))
    }
}
