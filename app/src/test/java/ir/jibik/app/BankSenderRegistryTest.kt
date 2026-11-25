package ir.jibik.app

import org.junit.Assert.*
import org.junit.Test

class BankSenderRegistryTest {
    private val a = BankSenderProfile("1", "melli", "10001", "4004", "account1")
    private val b = BankSenderProfile("2", "melli", "10001", "4005", "account2")
    @Test fun exactSenderAndAccountAreRequired() {
        assertEquals(a, resolveBankSender(listOf(a,b), "۱۰۰۰۱", "melli", "4004"))
        assertEquals(b, resolveBankSender(listOf(a,b), "10001", "melli", "4005"))
        assertNull(resolveBankSender(listOf(a,b), "100010", "melli", "4004"))
        assertNull(resolveBankSender(listOf(a,b), "10001", "mellat", "4004"))
        assertNull(resolveBankSender(listOf(a,b), "10001", "melli", ""))
        assertNull(resolveBankSender(listOf(a.copy(enabled = false)), "10001", "melli", "4004"))
    }
    @Test fun blankAccountIsNotWildcardAndConflictsFailClosed() {
        val blank = a.copy(id = "3", accountLast4 = "")
        assertEquals(blank, resolveBankSender(listOf(blank), "10001", "melli", ""))
        assertNull(resolveBankSender(listOf(blank), "10001", "melli", "4004"))
        assertNull(resolveBankSender(listOf(a,a.copy(id = "4")), "10001", "melli", "4004"))
    }
    @Test fun preserveAddressIdentity() {
        assertEquals("00100", bankSenderKey("۰۰۱۰۰"))
        assertEquals("+98100", bankSenderKey("+۹۸۱۰۰"))
        assertEquals("blu-bank", bankSenderKey("BLU-BANK"))
        assertNull(bankSenderKey("bank test"))
        assertNull(bankSenderKey("https://example.com"))
    }
}
