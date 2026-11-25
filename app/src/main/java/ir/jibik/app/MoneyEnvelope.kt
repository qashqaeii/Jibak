package ir.jibik.app

import androidx.room.*
import java.math.BigInteger

@Entity(tableName = "money_envelopes", foreignKeys = [ForeignKey(
    entity = FinancialAccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"],
    onDelete = ForeignKey.RESTRICT)], indices = [Index("accountId")])
internal data class MoneyEnvelope(
    @PrimaryKey val id: String, val title: String, val accountId: String,
    val targetToman: Long, val reservedToman: Long = 0, val createdAtMillis: Long,
    @ColumnInfo(defaultValue = "1") val priority: Int = 1,
    @ColumnInfo(defaultValue = "0") val monthlyPlanToman: Long = 0,
)

@Entity(tableName = "envelope_events", foreignKeys = [ForeignKey(
    entity = MoneyEnvelope::class, parentColumns = ["id"], childColumns = ["envelopeId"],
    onDelete = ForeignKey.RESTRICT)], indices = [Index("envelopeId")])
internal data class EnvelopeEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val envelopeId: String,
    val atMillis: Long, val previousToman: Long, val reservedToman: Long,
    @ColumnInfo(defaultValue = "''") val details: String = "",
)

@Dao
internal interface EnvelopeDao {
    @Query("SELECT * FROM money_envelopes ORDER BY createdAtMillis, id")
    suspend fun list(): List<MoneyEnvelope>
    @Insert suspend fun insert(item: MoneyEnvelope)
    @Update suspend fun update(item: MoneyEnvelope): Int
    @Query("UPDATE money_envelopes SET reservedToman = :amount WHERE id = :id AND reservedToman = :expected")
    suspend fun updateReserve(id: String, expected: Long, amount: Long): Int
    @Insert suspend fun addEvent(event: EnvelopeEvent)
    @Query("SELECT * FROM envelope_events WHERE envelopeId = :id ORDER BY id DESC")
    suspend fun history(id: String): List<EnvelopeEvent>
}

internal data class EnvelopeFunding(val envelope: MoneyEnvelope, val fundedToman: Long) {
    val shortfallToman: Long get() = envelope.reservedToman - fundedToman
}

/** User priority first, then older envelopes; never count a toman twice. */
internal fun envelopeFunding(balances: Map<String, Long>, goals: List<SavingsGoalEntity>,
    envelopes: List<MoneyEnvelope>): List<EnvelopeFunding> {
    val dedicated = goals.map { it.accountId }.toSet()
    val remaining = balances.mapValues { (id, balance) -> if (id in dedicated) 0L else balance.coerceAtLeast(0) }.toMutableMap()
    return envelopes.sortedWith(compareByDescending<MoneyEnvelope> { it.priority }.thenBy { it.createdAtMillis }.thenBy { it.id }).map { item ->
        require(item.targetToman > 0 && item.reservedToman in 0..item.targetToman && item.priority in 0..2 && item.monthlyPlanToman >= 0)
        val available = remaining.getOrDefault(item.accountId, 0L)
        val funded = minOf(available, item.reservedToman)
        remaining[item.accountId] = available - funded
        EnvelopeFunding(item, funded)
    }
}

internal fun envelopeFreeCapacity(accountId: String, balances: Map<String, Long>,
    envelopes: List<MoneyEnvelope>, excludingId: String? = null): Long {
    val balance = balances.getOrDefault(accountId, 0L).coerceAtLeast(0).toBigInteger()
    val reserved = envelopes.filter { it.accountId == accountId && it.id != excludingId }
        .fold(BigInteger.ZERO) { total, item -> total + item.reservedToman.toBigInteger() }
    return (balance - reserved).max(BigInteger.ZERO).longValueExact()
}

internal fun envelopePriorityLabel(priority: Int) = when (priority) {
    2 -> "ضروری"
    0 -> "کم"
    else -> "عادی"
}

/** User-entered monthly scenario; no inferred income or automatic allocation.
 * The first hypothetical contribution is one Persian month from today.
 * Very distant estimates are shown as a duration rather than an unsupported date.
 */
internal data class GoalEstimate(val remaining: Long, val months: Long?, val completionDay: Long?)

internal fun goalEstimate(target: Long, funded: Long, monthly: Long, today: Long): GoalEstimate {
    require(target > 0 && funded >= 0 && monthly >= 0)
    val remaining = target - minOf(target, funded)
    if (remaining == 0L) return GoalEstimate(0, 0, today)
    if (monthly == 0L) return GoalEstimate(remaining, null, null)
    val months = remaining / monthly + if (remaining % monthly == 0L) 0 else 1
    return GoalEstimate(remaining, months, if (months <= 1200) offsetJalaliMonth(today, months.toInt()) else null)
}
