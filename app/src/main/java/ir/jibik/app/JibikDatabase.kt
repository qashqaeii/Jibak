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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

/** User-created accounts are local/private until household ACLs ship. */
@Entity(tableName = "financial_accounts")
internal data class FinancialAccountEntity(
    @PrimaryKey val id: String,
    val title: String,
    val openingBalanceToman: Long,
)

@Dao
internal interface FinancialAccountDao {
    @Query("SELECT * FROM financial_accounts ORDER BY CASE WHEN id = 'wallet' THEN 0 ELSE 1 END, title, id")
    suspend fun list(): List<FinancialAccountEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefault(account: FinancialAccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: FinancialAccountEntity)
}

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

@Database(
    entities = [LedgerEntity::class, FinancialAccountEntity::class],
    version = 2,
    exportSchema = false,
)
internal abstract class JibikDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun accountDao(): FinancialAccountDao

    companion object {
        @Volatile private var instance: JibikDatabase? = null

        /** Add accounts without modifying or deleting any existing ledger data. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS financial_accounts (" +
                        "id TEXT NOT NULL, title TEXT NOT NULL, " +
                        "openingBalanceToman INTEGER NOT NULL, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO financial_accounts " +
                        "(id, title, openingBalanceToman) VALUES ('wallet', 'کیف پول جیبیک', 0)"
                )
            }
        }

        fun get(context: Context): JibikDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JibikDatabase::class.java,
                    "jibik_ledger_v1.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
