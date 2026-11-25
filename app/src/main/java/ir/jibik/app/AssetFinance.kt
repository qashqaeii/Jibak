package ir.jibik.app

import androidx.room.*
import java.math.BigInteger

internal val ASSET_KINDS = linkedMapOf(
    "GOLD" to "طلا و سکه", "CURRENCY" to "ارز", "CAR" to "خودرو",
    "PROPERTY" to "ملک", "INVESTMENT" to "سرمایه‌گذاری", "OTHER" to "سایر دارایی‌ها",
)

@Entity(tableName = "asset_holdings")
internal data class AssetHolding(
    @PrimaryKey val id: String, val title: String, val kind: String,
    val costBasisToman: Long?, val createdAtMillis: Long,
)

@Entity(tableName = "asset_events", foreignKeys = [ForeignKey(
    entity = AssetHolding::class, parentColumns = ["id"], childColumns = ["assetId"],
    onDelete = ForeignKey.RESTRICT)], indices = [Index("assetId")])
internal data class AssetEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val assetId: String,
    val kind: String, val valueToman: Long, val atMillis: Long, val note: String,
)

@Dao
internal interface AssetDao {
    @Query("SELECT * FROM asset_holdings ORDER BY createdAtMillis DESC, id")
    suspend fun holdings(): List<AssetHolding>
    @Query("SELECT * FROM asset_events ORDER BY id")
    suspend fun events(): List<AssetEvent>
    @Insert suspend fun insertHolding(holding: AssetHolding)
    @Insert suspend fun insertEvent(event: AssetEvent)
}

internal data class AssetPosition(val holding: AssetHolding, val history: List<AssetEvent>) {
    val latest get() = history.last()
    val archived get() = latest.kind == "ARCHIVE"
    val valueToman get() = latest.valueToman
    val valuationAtMillis get() = history.last { it.kind == "VALUE" }.atMillis
    val valuationChange: BigInteger? get() = holding.costBasisToman?.let { valueToman.toBigInteger() - it.toBigInteger() }
}

internal fun validateAsset(item: AssetHolding) {
    require(item.id.isNotBlank() && item.title.trim().length in 1..60 && item.kind in ASSET_KINDS)
    require(item.costBasisToman == null || item.costBasisToman >= 0)
    require(item.createdAtMillis > 0)
}

internal fun assetPositions(holdings: List<AssetHolding>, events: List<AssetEvent>): List<AssetPosition> {
    val grouped = events.groupBy { it.assetId }
    require(holdings.map { it.id }.distinct().size == holdings.size)
    require(events.map { it.id }.distinct().size == events.size)
    require(grouped.keys == holdings.map { it.id }.toSet())
    return holdings.map { item ->
        validateAsset(item)
        val history = grouped.getValue(item.id).sortedBy { it.id }
        var active = true
        var last: AssetEvent? = null
        history.forEach { event ->
            require(event.id > 0 && event.valueToman >= 0 && event.note.length <= 200 && event.atMillis >= item.createdAtMillis)
            val previous = last
            require(previous == null || event.atMillis >= previous.atMillis)
            when (event.kind) {
                "VALUE" -> require(active)
                "ARCHIVE" -> { require(active && previous != null && event.valueToman == previous.valueToman); active = false }
                "RESTORE" -> { require(!active && previous != null && event.valueToman == previous.valueToman); active = true }
                else -> error("Unknown asset event")
            }
            last = event
        }
        require(history.first().kind == "VALUE")
        AssetPosition(item, history)
    }
}

internal data class NetWorth(
    val cash: BigInteger, val nonCash: BigInteger, val receivables: BigInteger,
    val overdrafts: BigInteger, val obligations: BigInteger,
) {
    val assets get() = cash + nonCash + receivables
    val liabilities get() = overdrafts + obligations
    val net get() = assets - liabilities
}

/** Snapshot of recorded wealth, not a bank feed or a market valuation.
 * Savings goals/envelopes are designations of existing cash, not new assets.
 */
internal fun netWorth(balances: Map<String, Long>, assets: List<AssetPosition>,
    obligations: List<FinancialObligationEntity>, payments: List<ObligationPaymentEntity>): NetWorth {
    val cash = balances.values.filter { it > 0 }.fold(BigInteger.ZERO) { sum, value -> sum + value.toBigInteger() }
    val overdrafts = balances.values.filter { it < 0 }.fold(BigInteger.ZERO) { sum, value -> sum - value.toBigInteger() }
    val holdings = assets.filterNot { it.archived }.fold(BigInteger.ZERO) { sum, item -> sum + item.valueToman.toBigInteger() }
    val grouped = payments.groupBy { it.obligationId }
    var receivables = BigInteger.ZERO
    var debts = BigInteger.ZERO
    obligations.forEach { item ->
        val remaining = obligationProgress(item, grouped[item.id].orEmpty()).remainingToman.toBigInteger()
        if (item.kind == "RECEIVABLE") receivables += remaining else debts += remaining
    }
    return NetWorth(cash, holdings, receivables, overdrafts, debts)
}
