package ir.jibik.app

import org.junit.Assert.assertEquals
import org.junit.Test

class JibikBackNavigationTest {
    private fun resolve(
        section: Section,
        obligations: Boolean = false,
        categories: Boolean = false,
        goals: Boolean = false,
        sms: Boolean = false,
    ) = resolveJibikBackRoute(section, obligations, categories, goals, sms)

    @Test fun homeKeepsNativeExitBehavior() {
        assertEquals(JibikBackRoute.NONE, resolve(Section.HOME))
        // A nested page in a different tab should not steal the Back gesture.
        assertEquals(JibikBackRoute.NONE, resolve(Section.HOME, goals = true))
    }

    @Test fun allTopLevelTabsReturnHome() {
        for (section in Section.entries.filter { it != Section.HOME }) {
            assertEquals(JibikBackRoute.HOME, resolve(section))
        }
    }

    @Test fun accountsNestedScreensGoBackToAccountsBeforeHome() {
        assertEquals(JibikBackRoute.GOALS, resolve(Section.ACCOUNTS, goals = true))
        assertEquals(JibikBackRoute.CATEGORIES, resolve(Section.ACCOUNTS, categories = true))
        assertEquals(JibikBackRoute.OBLIGATIONS, resolve(Section.ACCOUNTS, obligations = true))
    }

    @Test fun transactionsBankSmsReturnsToTransactions() {
        assertEquals(JibikBackRoute.BANK_SMS, resolve(Section.TRANSACTIONS, sms = true))
        assertEquals(JibikBackRoute.HOME, resolve(Section.REPORTS, sms = true))
    }

    @Test fun visibleNestedPrecedenceMatchesAccountsRouter() {
        assertEquals(JibikBackRoute.OBLIGATIONS,
            resolve(Section.ACCOUNTS, obligations = true, categories = true, goals = true))
    }
}
