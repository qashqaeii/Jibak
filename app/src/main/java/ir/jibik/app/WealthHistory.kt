package ir.jibik.app

import androidx.room.*
import java.math.BigInteger

/** Big integers stored as canonical decimal strings: SQLite INTEGER cannot hold
 * the sum of multiple maximum-sized accounts. No floating-point monetary fields. */
@Entity(tableName = "wealth_snapshots")
internal data class WealthSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMillis: Long, val cash: String, val nonCash: String, val receivables: String,
    val overdrafts: String, val obligations: String,
) {
    fun worth(): NetWorth {
        require(atMillis > 0 && id >= 0)
        fun amount(raw: String): BigInteger {
            require(raw.length in 1..40)
            return raw.toBigInteger().also { require(it.signum() >= 0 && it.toString() == raw) }
        }
        return NetWorth(amount(cash), amount(nonCash), amount(receivables), amount(overdrafts), amount(obligations))
    }
    companion object {
        fun from(worth: NetWorth, at: Long) = WealthSnapshot(atMillis = at, cash = worth.cash.toString(),
            nonCash = worth.nonCash.toString(), receivables = worth.receivables.toString(),
            overdrafts = worth.overdrafts.toString(), obligations = worth.obligations.toString())
    }
}

@Dao
internal interface WealthDao {
    @Query("SELECT * FROM wealth_snapshots ORDER BY atMillis DESC, id DESC")
    suspend fun list(): List<WealthSnapshot>
    @Insert suspend fun insert(item: WealthSnapshot)
}

internal fun dailyWealthHistory(history: List<WealthSnapshot>): List<WealthSnapshot> =
    history.sortedWith(compareByDescending<WealthSnapshot> { it.atMillis }.thenByDescending { it.id })
        .distinctBy { formatJalaliDueDate(it.atMillis) }.reversed()
