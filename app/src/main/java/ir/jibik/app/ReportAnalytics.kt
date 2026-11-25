package ir.jibik.app

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import java.math.BigInteger
import java.math.BigDecimal
import java.math.RoundingMode

internal data class ReportRange(val start: Long, val endExclusive: Long) {
    init { require(start < endExclusive) }
    val label get() = formatJalaliDueDate(start) + " تا " + formatJalaliDueDate(endExclusive - 1)
}
private fun reportCalendar(day: Long) = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"),
    ULocale("fa_IR@calendar=persian")).apply { timeInMillis = day }
private fun reportDays(range: ReportRange): Int =
    reportCalendar(range.endExclusive).get(Calendar.JULIAN_DAY) - reportCalendar(range.start).get(Calendar.JULIAN_DAY)

internal fun reportRange(preset: String, today: Long): ReportRange {
    val cal = reportCalendar(today)
    val start = when (preset) {
        "DAY" -> today
        "WEEK" -> offsetJalaliDay(today, -(cal.get(Calendar.DAY_OF_WEEK) % 7))
        "YEAR" -> currentJalaliMonth(offsetJalaliMonth(today, -cal.get(Calendar.MONTH))).startMillis
        else -> currentJalaliMonth(today).startMillis
    }
    return ReportRange(start, jalaliDueDayEndExclusive(today))
}
internal fun previousReportRange(range: ReportRange): ReportRange =
    ReportRange(offsetJalaliDay(range.start, -reportDays(range)), range.start)

internal data class ReportTotals(val income: BigInteger, val expense: BigInteger, val count: Int) {
    val net get() = income - expense
    val savingsRate: BigDecimal? get() = if (income.signum() == 0) null
        else BigDecimal(net * 100.toBigInteger()).divide(BigDecimal(income), 1, RoundingMode.HALF_UP)
}
internal fun reportTotals(records: List<LedgerRecord>, range: ReportRange): ReportTotals {
    var income = BigInteger.ZERO; var expense = BigInteger.ZERO; var count = 0
    records.forEach { row ->
        if (row.timestampMillis >= range.start && row.timestampMillis < range.endExclusive) {
            when (row.kind) {
                LedgerKind.INCOME -> { income += row.amountToman.toBigInteger(); count++ }
                LedgerKind.EXPENSE -> { expense += row.amountToman.toBigInteger(); count++ }
                else -> Unit
            }
        }
    }
    return ReportTotals(income, expense, count)
}
internal data class ReportBucket(val range: ReportRange, val totals: ReportTotals)
internal fun reportTrend(records: List<LedgerRecord>, range: ReportRange): List<ReportBucket> {
    val days = reportDays(range)
    require(days > 0)
    val count = minOf(12, days)
    return (0 until count).map { i ->
        val part = ReportRange(offsetJalaliDay(range.start, (days.toLong() * i / count).toInt()),
            offsetJalaliDay(range.start, (days.toLong() * (i+1) / count).toInt()))
        ReportBucket(part, reportTotals(records, part))
    }
}
internal fun reportRateText(value: BigDecimal?): String = value?.let {
    java.text.NumberFormat.getNumberInstance(java.util.Locale("fa","IR")).apply { maximumFractionDigits = 1 }.format(it) + "٪"
} ?: "نامشخص"
