package ir.jibik.app

import androidx.room.*
import java.math.BigInteger

@Entity(tableName = "money_envelopes", foreignKeys = [ForeignKey(
    entity = FinancialAccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"],
    onDelete = ForeignKey.RESTRICT)], indices = [Index("accountId")])
internal data class MoneyEnvelope(
    @PrimaryKey val id: String, val title: String, val accountId: String,
    val targetToman: Long, val reservedToman: Long = 0, val createdAtMillis: Long,
)

@Entity(tableName = "envelope_events", foreignKeys = [ForeignKey(
    entity = MoneyEnvelope::class, parentColumns = ["id"], childColumns = ["envelopeId"],
    onDelete = ForeignKey.RESTRICT)], indices = [Index("envelopeId")])
internal data class EnvelopeEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val envelopeId: String,
    val atMillis: Long, val previousToman: Long, val reservedToman: Long,
)

@Dao
internal interface EnvelopeDao {
    @Query("SELECT * FROM money_envelopes ORDER BY createdAtMillis, id")
    suspend fun list(): List<MoneyEnvelope>
    @Insert suspend fun insert(item: MoneyEnvelope)
    @Query("UPDATE money_envelopes SET reservedToman = :amount WHERE id = :id AND reservedToman = :expected")
    suspend fun updateReserve(id: String, expected: Long, amount: Long): Int
    @Insert suspend fun addEvent(event: EnvelopeEvent)
    @Query("SELECT * FROM envelope_events WHERE envelopeId = :id ORDER BY id DESC")
    suspend fun history(id: String): List<EnvelopeEvent>
}

internal data class EnvelopeFunding(val envelope: MoneyEnvelope, val fundedToman: Long) {
    val shortfallToman: Long get() = envelope.reservedToman - fundedToman
}

/** Older reservations take priority if actual cash falls. Never count a toman twice. */
internal fun envelopeFunding(balances: Map<String, Long>, goals: List<SavingsGoalEntity>,
    envelopes: List<MoneyEnvelope>): List<EnvelopeFunding> {
    val dedicated = goals.map { it.accountId }.toSet()
    val remaining = balances.mapValues { (id, balance) -> if (id in dedicated) 0L else balance.coerceAtLeast(0) }.toMutableMap()
    return envelopes.sortedWith(compareBy<MoneyEnvelope> { it.createdAtMillis }.thenBy { it.id }).map { item ->
        require(item.targetToman > 0 && item.reservedToman in 0..item.targetToman)
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
