package ir.jibik.app

import androidx.room.*
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale

@Entity(tableName = "recurring_rules")
internal data class RecurringRule(@PrimaryKey val id: String, val title: String, val income: Boolean,
    val accountId: String, val amount: Long, val firstDay: Long, val stoppedAt: Long? = null,
    @ColumnInfo(defaultValue = "'MONTHLY'") val frequency: String = "MONTHLY",
    @ColumnInfo(defaultValue = "1") val interval: Int = 1,
    @ColumnInfo(defaultValue = "''") val category: String = "",
    val endDay: Long? = null)

@Entity(tableName = "recurring_decisions", primaryKeys = ["ruleId", "day"])
internal data class RecurringDecision(val ruleId: String, val day: Long, val transactionId: String?, val actual: Long?)

@Dao
internal interface RecurringDao {
    @Query("SELECT * FROM recurring_rules ORDER BY firstDay, title, id")
    suspend fun rules(): List<RecurringRule>
    @Query("SELECT * FROM recurring_decisions WHERE day >= :start AND day < :end")
    suspend fun decisions(start: Long, end: Long): List<RecurringDecision>
    @Query("SELECT EXISTS(SELECT 1 FROM recurring_decisions WHERE transactionId = :transactionId)")
    suspend fun isLinked(transactionId: String): Boolean
    @Query("SELECT * FROM recurring_decisions WHERE transactionId = :transactionId")
    suspend fun linked(transactionId: String): List<RecurringDecision>
    @Query("DELETE FROM recurring_decisions WHERE transactionId = :transactionId")
    suspend fun detach(transactionId: String): Int
    @Insert suspend fun add(rule: RecurringRule)
    @Insert suspend fun decide(row: RecurringDecision)
    @Query("UPDATE recurring_rules SET stoppedAt = :day WHERE id = :id AND stoppedAt IS NULL")
    suspend fun stop(id: String, day: Long)
}

internal fun offsetJalaliMonth(day: Long, offset: Int): Long =
    Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"), ULocale("fa_IR@calendar=persian")).apply {
        timeInMillis = day
        add(Calendar.MONTH, offset)
    }.timeInMillis

internal val RECURRING_FREQUENCIES = listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")
internal fun recurringFrequencyLabel(frequency: String) = when (frequency) {
    "DAILY" -> "روز"; "WEEKLY" -> "هفته"; "YEARLY" -> "سال"; else -> "ماه"
}

internal fun offsetJalaliDay(day: Long, offset: Int): Long =
    Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"), ULocale("fa_IR@calendar=persian")).apply {
        timeInMillis = day
        add(Calendar.DAY_OF_MONTH, offset)
    }.timeInMillis

private fun julianDay(day: Long): Int =
    Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"), ULocale("fa_IR@calendar=persian")).apply {
        timeInMillis = day
    }.get(Calendar.JULIAN_DAY)

private fun RecurringRule.occurrence(index: Int): Long = when (frequency) {
    "DAILY" -> offsetJalaliDay(firstDay, Math.multiplyExact(index, interval))
    "WEEKLY" -> offsetJalaliDay(firstDay, Math.multiplyExact(index, interval * 7))
    "YEARLY" -> offsetJalaliMonth(firstDay, Math.multiplyExact(index, interval * 12))
    else -> offsetJalaliMonth(firstDay, Math.multiplyExact(index, interval))
}

private fun RecurringRule.countThrough(timestamp: Long): Int {
    require(frequency in RECURRING_FREQUENCIES && interval in 1..365)
    val last = minOf(timestamp, stoppedAt ?: Long.MAX_VALUE, endDay ?: Long.MAX_VALUE)
    if (last < firstDay) return 0
    val index = if (frequency == "DAILY" || frequency == "WEEKLY") {
        val days = julianDay(last) - julianDay(firstDay)
        days / (interval * (if (frequency == "WEEKLY") 7 else 1))
    } else {
        val first = currentJalaliMonth(firstDay)
        val month = currentJalaliMonth(last)
        ((month.year - first.year) * 12 + month.month - first.month) /
            (interval * (if (frequency == "YEARLY") 12 else 1))
    }
    return index + if (occurrence(index) <= last) 1 else 0
}

/** Every occurrence is anchored to the first Persian day, not the previous occurrence. */
internal fun RecurringRule.dueBetween(start: Long, endExclusive: Long): List<Long> {
    if (endExclusive <= start || endExclusive <= firstDay) return emptyList()
    val from = countThrough(start - 1)
    val toExclusive = countThrough(endExclusive - 1)
    return (from until toExclusive).map { occurrence(it) }
}

internal fun RecurringRule.dueIn(month: JalaliMonthPeriod): Long? =
    dueBetween(month.startMillis, month.endExclusiveMillis).firstOrNull()

internal fun RecurringRule.isDue(day: Long): Boolean =
    day >= firstDay && dueBetween(day, day + 1).singleOrNull() == day

internal fun pendingOccurrenceCount(rule: RecurringRule, decisions: List<RecurringDecision>, end: Long): Int =
    (rule.countThrough(end - 1) - decisions.asSequence().filter {
        it.ruleId == rule.id && it.day < end && rule.isDue(it.day)
    }.map { it.day }.distinct().count()).coerceAtLeast(0)
