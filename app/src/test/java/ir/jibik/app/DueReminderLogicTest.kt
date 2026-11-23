package ir.jibik.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DueReminderLogicTest {
    private fun obligation(id: String) = FinancialObligationEntity(
        id = id,
        title = "تعهد خصوصی محرمانه",
        kind = "LOAN",
        totalToman = 1_200L,
        dueAtMillis = 100L,
        installmentCount = 3,
        note = "نباید در اعلان نمایش داده شود",
        createdAtMillis = 10L,
    )

    @Test fun countsOnlyOutstandingInstallmentsWithinThreeCalendarDays() {
        val items = listOf("old", "today", "soon", "later", "settled").map(::obligation)
        val settled = listOf(
            ObligationPaymentEntity("p-settled", "settled", 1_200L, 300L)
        )
        val due = mapOf(
            "old" to 9L, "today" to 20L, "soon" to 35L,
            "later" to 70L, "settled" to 20L,
        )
        val summary = summarizeDueReminders(
            items, settled, todayStartMillis = 20L,
            tomorrowStartMillis = 30L, afterThreeDaysStartMillis = 60L,
            dueAt = { item, _ -> due.getValue(item.id) },
        )
        assertEquals(1, summary.overdueCount)
        assertEquals(1, summary.todayCount)
        assertEquals(1, summary.upcomingCount)
        assertEquals(3, summary.totalCount)
        // The summary deliberately cannot carry names, amounts, notes or account IDs.
        assertEquals(
            "DueReminderSummary(overdueCount=1, todayCount=1, upcomingCount=1)",
            summary.toString(),
        )
    }

    @Test fun partialPaymentAdvancesToNextInstallmentWithoutDuplicateAlert() {
        val item = obligation("debt")
        val payments = listOf(
            ObligationPaymentEntity("first", "debt", 400L, 200L),
            ObligationPaymentEntity("half-second", "debt", 100L, 300L),
        )
        val summary = summarizeDueReminders(
            listOf(item), payments, 20L, 30L, 60L,
            dueAt = { _, index ->
                assertEquals(1, index)
                27L
            },
        )
        assertEquals(1, summary.todayCount)
        assertEquals(1, summary.totalCount)
    }

    @Test fun noDataOrSettledObligationsNeverNotify() {
        val item = obligation("paid")
        val payments = listOf(ObligationPaymentEntity("all", "paid", 1_200L, 300L))
        val summary = summarizeDueReminders(
            listOf(item), payments, 20L, 30L, 60L,
            dueAt = { _, _ -> throw AssertionError("Settled items must not be scheduled") },
        )
        assertEquals(0, summary.totalCount)
        assertEquals(0, summarizeDueReminders(
            emptyList(), emptyList(), 20L, 30L, 60L
        ).totalCount)
    }

    @Test fun invalidDayWindowsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            summarizeDueReminders(emptyList(), emptyList(), 30L, 20L, 60L)
        }
    }
}
