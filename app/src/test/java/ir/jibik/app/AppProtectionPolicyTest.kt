package ir.jibik.app

import org.junit.Assert.*
import org.junit.Test

class AppProtectionPolicyTest {
    @Test fun lockIsOptInAndUsesMonotonicElapsedTime() {
        assertFalse(appLockExpired(false, 10, 100000))
        assertFalse(appLockExpired(true, null, 100000))
        assertFalse(appLockExpired(true, 100, 30099))
        assertTrue(appLockExpired(true, 100, 30100))
        assertTrue(appLockExpired(true, 100, 99))
    }
}
