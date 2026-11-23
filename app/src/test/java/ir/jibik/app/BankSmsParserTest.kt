package ir.jibik.app

import org.junit.Assert.*
import org.junit.Test

class BankSmsParserTest {
    private val salt = "local-install-random-salt"
    private val at = 1_800_000_000_000L
    private fun parse(body: String, sender: String = "BANK", time: Long = at) =
        BankSmsParser.parse(sender, body.trimIndent(), time, salt)

    @Test fun melliCreditSignedAmountIsRialNotBalance() {
        val sms = """
            بانك ملي ايران
            انتقال:45,000,000+
            حساب:04004
            مانده:57,593,152
            0901-09:28
        """
        val row = parse(sms)!!
        assertEquals("melli", row.bankId)
        assertTrue(row.income)
        assertEquals(45_000_000L, row.amountRial)
        assertEquals(4_500_000L, row.amountToman)
        assertEquals("4004", row.accountLast4)
    }

    @Test fun melliDebitAndInternetPurchaseUseTrailingMinus() {
        val debit = parse("""
            بانك ملي ايران
            انتقال:27,318,000-
            حساب:04004
            مانده:30,275,152
            0901-13:42
        """)!!
        assertFalse(debit.income)
        assertEquals(2_731_800L, debit.amountToman)
        val online = parse("""
            بانك ملي ايران
            خريداينترنتي:1,980,000-
            حساب:04004
            مانده:14,730,787
            0829-17:49
        """)!!
        assertFalse(online.income)
        assertEquals(198_000L, online.amountToman)
    }

    @Test fun bluDepositAndWithdrawalUseEventAndRial() {
        val outgoing = parse("""
            بلو
            برداشت پول
            حسین عزیز، 1,920,000ریال از حساب شما پرید.
            موجودی: 62,723,986 ریال
            ۱۲:۰۹
            ۱۴۰۵.۰۹.۰۲
        """)!!
        assertEquals("blu", outgoing.bankId)
        assertFalse(outgoing.income)
        assertEquals(192_000L, outgoing.amountToman)
        val incoming = parse("""
            بلو
            واریز پول
            حسین عزیز، 19,586,950ریال به حساب شما نشست.
            موجودی: 82,974,586 ریال
            ۴:۲۵
            ۱۴۰۵.۰۹.۰۲
        """)!!
        assertEquals(1_958_695L, incoming.amountToman)
        assertTrue(incoming.income)
    }

    @Test fun noFinancialActionForOtpUnknownBankOrBalanceOnly() {
        assertNull(parse("بانک ملی ایران\nرمز پویا: 123456\nمبلغ:20,000ریال"))
        assertNull(parse("بانک ملی ایران\nموجودی حساب:20,000,000 ریال"))
        assertNull(parse("خبرنامه\nواریز:1,000,000 ریال"))
        assertNull(parse("بلو\nواریز پول\nموجودی 123,000 ریال\nکد ورود:22"))
        assertNull(parse("بلو\nواریز پول\nآدرس: https://example.com\n1,000ریال"))
    }

    @Test fun unlabelledOtherBanksAndAmbiguousDirectionFailClosed() {
        assertNull(parse("بانک ملت\nبرداشت\nمبلغ:100,000\nمانده:50,000"))
        assertNull(parse("بانک ملت\nواریز و برداشت\n100,000 ریال"))
    }

    @Test fun balanceShownBeforeTransactionIsNeverChosenAsAmount() {
        val result = parse("""
            بانک ملت
            مانده: 2,800,000 ریال
            برداشت
            مبلغ: 150,000 ریال
            شماره پیگیری: 55
        """)!!
        assertFalse(result.income)
        assertEquals(15_000L, result.amountToman)
    }

    @Test fun balanceOnlyCannotBecomeAnExpenseWhenTitleMentionsPurchase() {
        assertNull(parse("""
            بانک سامان
            گزارش خریدهای ماه گذشته
            موجودی: 9,000,000 ریال
        """))
    }

    @Test fun remainingBalanceAmountIsNotRecordedAsTransaction() {
        assertNull(parse("""
            بانک ملت
            برداشت
            باقیمانده مبلغ: 8,500,000 ریال
        """))
    }

    @Test fun remainingBalanceBeforeExplicitAmountUsesActualTransaction() {
        val result = parse("""
            بانک ملت
            برداشت
            باقیمانده مبلغ: 8,500,000 ریال
            مبلغ: 120,000 ریال
        """)!!
        assertFalse(result.income)
        assertEquals(12_000L, result.amountToman)
    }

    @Test fun duplicateFingerprintStableButTimestampChangesIdentity() {
        val sample = "بانك ملي ايران\nانتقال:45,000,000+\nحساب:04004"
        val a = parse(sample)!!
        assertEquals(a.fingerprint, parse(sample)!!.fingerprint)
        assertNotEquals(a.fingerprint, parse(sample, time = at + 60_000L)!!.fingerprint)
        assertEquals(null, parse("بلو\nبرداشت پول\n9,001ریال")?.amountToman)
    }
}
