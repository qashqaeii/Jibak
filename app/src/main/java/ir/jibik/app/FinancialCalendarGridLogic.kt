package ir.jibik.app

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale

/** Saturday-first calendar cells including leading and trailing empty positions. */
internal fun financialCalendarCells(month: JalaliMonthPeriod): List<Long?> {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"), ULocale("fa_IR@calendar=persian"))
    cal.timeInMillis = month.startMillis
    val leading = cal.get(Calendar.DAY_OF_WEEK) % 7
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val trailing = (7 - (leading + days) % 7) % 7
    return List(leading) { null } + (0 until days).map { offsetJalaliDay(month.startMillis, it) } + List(trailing) { null }
}
