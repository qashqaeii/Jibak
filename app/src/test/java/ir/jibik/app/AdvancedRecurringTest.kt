package ir.jibik.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class AdvancedRecurringTest {
    private fun date(value: String) = requireNotNull(parseJalaliDueDate(value))

    @Test fun dailyAndWeeklyIntervalsAndInclusiveEnd() {
        val first = date("1403/07/01")
        val end = date("1403/07/15")
        val daily = RecurringRule("d", "خرید", false, "wallet", 100, first,
            frequency = "DAILY", interval = 2, endDay = end)
        val expected = (1..15 step 2).map { date("1403/07/" + it.toString().padStart(2, '0')) }
        assertEquals(expected, daily.dueBetween(first, date("1403/08/01")))
        assertEquals(8, pendingOccurrenceCount(daily, emptyList(), end + 1))
        assertEquals(7, pendingOccurrenceCount(daily, listOf(RecurringDecision("d", first, null, null)), end + 1))
        assertFalse(daily.isDue(date("1403/07/02")))
        val weekly = daily.copy(frequency = "WEEKLY", interval = 1)
        assertEquals(listOf(first, date("1403/07/08"), end), weekly.dueBetween(first, end + 1))
    }

    @Test fun anchoredMonthAndYearDoNotDrift() {
        val first = date("1403/06/31")
        val monthly = RecurringRule("m", "اجاره", false, "wallet", 100, first)
        assertEquals(date("1403/07/30"), monthly.dueIn(currentJalaliMonth(date("1403/07/01"))))
        assertEquals(date("1404/01/31"), monthly.dueIn(currentJalaliMonth(date("1404/01/01"))))
        val yearly = monthly.copy(frequency = "YEARLY")
        assertEquals(listOf(first, date("1404/06/31")), yearly.dueBetween(first, date("1404/07/01")))
    }

    @Test fun gridHasSaturdayFirstAndAllDaysExactlyOnce() {
        val month = currentJalaliMonth(date("1403/07/01"))
        val cells = financialCalendarCells(month)
        assertEquals(0, cells.size % 7)
        assertEquals(30, cells.filterNotNull().size)
        assertEquals(month.startMillis, cells.filterNotNull().first())
        assertEquals(date("1403/07/30"), cells.filterNotNull().last())
        assertEquals(1, cells.indexOf(month.startMillis))
    }
}
