package ir.jibik.app

import org.junit.Assert.*
import org.junit.Test

class LoanPrincipalBalanceTest {
    private val now = 1700000000000L
    private val loan = FinancialObligationEntity("loan", "وام", "LOAN", 1200, now, 1, "", now)
    private val terms = LoanTerms("loan", 1000)
    private fun payment(id: String, amount: Long) = ObligationPaymentEntity(id, loan.id, amount, now)
    @Test fun missingTermsDoNotInventPrincipal() {
        assertNull(loanDetailsFor(loan, listOf(payment("a", 100)), null, emptyList()).remainingPrincipal())
    }
    @Test fun unclassifiedPaymentsGiveContractBoundedRange() {
        val d = loanDetailsFor(loan, listOf(payment("a", 300), payment("b", 100)), terms,
            listOf(LoanPaymentSplit("b", 80, 20)))
        val range = requireNotNull(d.remainingPrincipal())
        assertEquals(620.toBigInteger(), range.minimum)
        assertEquals(800.toBigInteger(), range.maximum)
        assertFalse(range.exact)
    }
    @Test fun allClassifiedOrFullySettledGivesExactPrincipal() {
        val d = loanDetailsFor(loan, listOf(payment("a", 400)), terms,
            listOf(LoanPaymentSplit("a", 300, 100)))
        assertEquals(PrincipalBalance(700.toBigInteger(), 700.toBigInteger()), d.remainingPrincipal())
        val full = loanDetailsFor(loan, listOf(payment("a", 1200)), terms, emptyList())
        assertEquals(PrincipalBalance(0.toBigInteger(), 0.toBigInteger()), full.remainingPrincipal())
    }
}
