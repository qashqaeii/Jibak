package ir.jibik.app

import org.junit.Assert.assertTrue
import org.junit.Test

class JibikBudgetVisualStatusTest {
    @Test fun noMonthlyLimitIsNotPresentedAsOverspending() {
        val status = jibikBudgetGuidance(spent = 50_000L, limit = 0L)
        assertTrue(status.contains("سقف ماهانه"))
        assertTrue(!status.contains("عبور"))
    }

    @Test fun actualOverLimitMentionsOverspending() {
        assertTrue(jibikBudgetGuidance(110_000L, 100_000L).contains("عبور"))
    }

    @Test fun nearLimitReportsApproaching() {
        assertTrue(jibikBudgetGuidance(90_000L, 100_000L).contains("نزدیک"))
    }

    @Test fun emptyLedgerEncouragesFirstExpense() {
        assertTrue(jibikBudgetGuidance(0L, 100_000L).contains("اولین هزینه"))
    }
}
