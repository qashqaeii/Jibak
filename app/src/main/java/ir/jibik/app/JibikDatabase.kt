package ir.jibik.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * v1 local ledger is deliberately append-only: never silently replace a
 * financial transaction, and never use destructive migrations for user data.
 */
@Entity(
    tableName = "ledger_records",
    indices = [Index(value = ["timestampMillis"])],
)
internal data class LedgerEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val amountToman: Long,
    val fromAccountId: String?,
    val toAccountId: String?,
    val category: String,
    val note: String,
    val timestampMillis: Long,
)

@Dao
internal interface LedgerDao {
    @Query("SELECT * FROM ledger_records ORDER BY timestampMillis DESC, id DESC")
    suspend fun getAll(): List<LedgerEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: LedgerEntity)

    /** Allows idempotent legacy import after a process interruption. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLegacy(records: List<LedgerEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM ledger_records WHERE id IN (:ids)")
    suspend fun countMatchingIds(ids: List<String>): Int
}

@Database(entities = [LedgerEntity::class], version = 1, exportSchema = false)
internal abstract class JibikDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile private var instance: JibikDatabase? = null

        fun get(context: Context): JibikDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JibikDatabase::class.java,
                    "jibik_ledger_v1.db",
                ).build().also { instance = it }
            }
    }
}
