package ir.jibik.app

import androidx.room.*
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale

@Entity(tableName = "recurring_rules")
internal data class RecurringRule(@PrimaryKey val id: String, val title: String, val income: Boolean,
    val accountId: String, val amount: Long, val firstDay: Long, val stoppedAt: Long? = null)

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

internal fun RecurringRule.dueIn(month: JalaliMonthPeriod): Long? {
    val first = currentJalaliMonth(firstDay)
    val offset = (month.year - first.year) * 12 + month.month - first.month
    if (offset < 0) return null
    val day = offsetJalaliMonth(firstDay, offset)
    return day.takeIf { stoppedAt == null || it <= stoppedAt }
}

/** Count anchored monthly occurrences up to the horizon, including arrears. */
internal fun pendingOccurrenceCount(rule: RecurringRule, decisions: List<RecurringDecision>, end: Long): Int {
    val last = minOf(end - 1, rule.stoppedAt ?: Long.MAX_VALUE)
    if (last < rule.firstDay) return 0
    val first = currentJalaliMonth(rule.firstDay)
    val month = currentJalaliMonth(last)
    val offset = (month.year - first.year) * 12 + month.month - first.month
    val count = offset + if (offsetJalaliMonth(rule.firstDay, offset) <= last) 1 else 0
    return count - decisions.count { it.ruleId == rule.id && it.day in rule.firstDay..last }
}
