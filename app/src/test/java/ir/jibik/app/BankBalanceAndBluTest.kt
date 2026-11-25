package ir.jibik.app
import org.junit.Assert.*
import org.junit.Test

class BankBalanceAndBluTest {
    private val sample = "بلو\nبرداشت پول\nکاربر عزیز، 1,920,000ریال از حساب شما پرید.\nموجودی: 62,723,980 ریال\n۱۲:۰۹\n۱۴۰۵.۰۹.۰۲"
    private fun parse(body: String) = BankSmsParser.parse("blu", body, 1800000000000L, "local-test-salt")
    @Test fun bluTemplateRequiresExactFinancialSentenceAndNoAccountId() {
        val match = requireNotNull(parse(sample))
        assertEquals("blu-rial-v1:debit", strictBankAutoTemplate(sample, match))
        assertEquals(6272398L, bankReportedBalanceToman(sample, match))
        val credit = sample.replace("برداشت پول", "واریز پول").replace("از حساب شما پرید", "به حساب شما نشست")
        assertEquals("blu-rial-v1:credit", strictBankAutoTemplate(credit, requireNotNull(parse(credit))))
        assertNull(strictBankAutoTemplate(sample + "\nخبر تبلیغاتی", match))
        assertNull(strictBankAutoTemplate(sample.replace("از حساب شما پرید", "ناموفق"), match))
    }
    @Test fun comparisonDoesNotRoundOrOverflow() {
        val match = requireNotNull(parse(sample))
        assertNull(bankReportedBalanceToman(sample.replace("62,723,980", "62,723,986"), match))
        assertNull(bankReportedBalanceToman(sample + "\nمانده: 50 ریال", match))
        assertEquals(Long.MIN_VALUE.toBigInteger() - Long.MAX_VALUE.toBigInteger(), bankBalanceDifference(Long.MIN_VALUE, Long.MAX_VALUE))
    }
}
