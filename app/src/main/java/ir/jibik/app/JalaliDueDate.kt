package ir.jibik.app

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.util.Locale

/** Strict Jalali day input: epoch millis always point to Tehran midnight. */
internal fun parseJalaliDueDate(raw: String): Long? {
    // Normalize with separators intact; no guessing if input is ambiguous.
    val parts = raw.trim()
        .map { c ->
            when (c) {
                in '۰'..'۹' -> ('0'.code + c.code - '۰'.code).toChar()
                in '٠'..'٩' -> ('0'.code + c.code - '٠'.code).toChar()
                else -> c
            }
        }.joinToString("").split("/")
    if (parts.size != 3 || parts.any { it.isBlank() || it.length > 4 }) return null
    val (year, month, day) = parts.map { it.toIntOrNull() ?: return null }
    if (year !in 1300..1600 || month !in 1..12 || day !in 1..31) return null
    return try {
        val cal = Calendar.getInstance(
            TimeZone.getTimeZone("Asia/Tehran"),
            ULocale("fa_IR@calendar=persian"),
        )
        cal.isLenient = false
        cal.clear()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val timestamp = cal.timeInMillis
        if (cal.get(Calendar.YEAR) == year &&
            cal.get(Calendar.MONTH) == month - 1 &&
            cal.get(Calendar.DAY_OF_MONTH) == day) timestamp else null
    } catch (_: Exception) { null }
}

internal fun formatJalaliDueDate(millis: Long): String {
    val cal = Calendar.getInstance(
        TimeZone.getTimeZone("Asia/Tehran"),
        ULocale("fa_IR@calendar=persian"),
    )
    cal.timeInMillis = millis
    return String.format(
        Locale.US, "%04d/%02d/%02d",
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
    ).map { digit ->
        if (digit in '0'..'9') ('۰'.code + digit.code - '0'.code).toChar()
        else digit
    }.joinToString("")
}

/** nth monthly due date in the Persian calendar, anchored to the first due date. */
internal fun jalaliInstallmentDueDate(firstDueMillis: Long, index: Int): Long {
    require(firstDueMillis > 0L && index in 0..119)
    val calendar = Calendar.getInstance(
        TimeZone.getTimeZone("Asia/Tehran"),
        ULocale("fa_IR@calendar=persian"),
    )
    calendar.timeInMillis = firstDueMillis
    calendar.add(Calendar.MONTH, index)
    return calendar.timeInMillis
}

/** A due date is overdue only *after* its complete Tehran calendar day has ended. */
internal fun jalaliDueDayEndExclusive(dueAtMillis: Long): Long {
    require(dueAtMillis > 0L)
    val calendar = Calendar.getInstance(
        TimeZone.getTimeZone("Asia/Tehran"),
        ULocale("fa_IR@calendar=persian"),
    )
    calendar.timeInMillis = dueAtMillis
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    calendar.add(Calendar.DAY_OF_MONTH, 1)
    return calendar.timeInMillis
}
