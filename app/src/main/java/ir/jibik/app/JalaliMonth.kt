package ir.jibik.app

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.text.NumberFormat
import java.util.Locale

/**
 * Jalali month calculated with the Android ICU Persian calendar (minSdk 26).
 * Boundaries are calculated in Asia/Tehran, then compared to UTC epoch millis.
 * Do not approximate month lengths as 30 days or slice Gregorian date strings.
 */
internal data class JalaliMonthPeriod(
    val year: Int,
    val month: Int,
    val startMillis: Long,
    val endExclusiveMillis: Long,
) {
    val title: String get() = "${JALALI_MONTH_NAMES[month - 1]} ${persianNumber(year)}"
}

private val JALALI_MONTH_NAMES = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
)

private fun persianNumber(value: Int): String =
    NumberFormat.getIntegerInstance(Locale("fa", "IR")).format(value)

private fun persianCalendar(timestampMillis: Long): Calendar =
    Calendar.getInstance(
        TimeZone.getTimeZone("Asia/Tehran"),
        ULocale("fa_IR@calendar=persian"),
    ).apply {
        check(type == "persian") { "The system did not load the Persian calendar" }
        timeInMillis = timestampMillis
    }

internal fun currentJalaliMonth(timestampMillis: Long = System.currentTimeMillis()): JalaliMonthPeriod {
    val calendar = persianCalendar(timestampMillis)
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1

    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startMillis = calendar.timeInMillis

    calendar.add(Calendar.MONTH, 1)
    val endExclusiveMillis = calendar.timeInMillis
    return JalaliMonthPeriod(year, month, startMillis, endExclusiveMillis)
}
