package ir.jibik.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
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

/** One receipt has multiple classified items, but exactly one ledger payment. */
@Entity(
    tableName = "purchase_lines",
    primaryKeys = ["transactionId", "lineIndex"],
    foreignKeys = [ForeignKey(
        entity = LedgerEntity::class,
        parentColumns = ["id"],
        childColumns = ["transactionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["transactionId"])],
)
internal data class PurchaseLineEntity(
    val transactionId: String,
    val lineIndex: Int,
    val title: String,
    val category: String,
    val quantity: Int,
    val unitPriceToman: Long,
)

@Dao
internal interface PurchaseLineDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(lines: List<PurchaseLineEntity>)

    @Query("SELECT * FROM purchase_lines WHERE transactionId = :transactionId ORDER BY lineIndex")
    suspend fun forTransaction(transactionId: String): List<PurchaseLineEntity>
}

@Database(
    entities = [LedgerEntity::class, FinancialAccountEntity::class, PurchaseLineEntity::class],
    version = 3,
    exportSchema = false,
)
internal abstract class JibikDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun accountDao(): FinancialAccountDao
    abstract fun purchaseLineDao(): PurchaseLineDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS purchase_lines (" +
                        "transactionId TEXT NOT NULL, lineIndex INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, category TEXT NOT NULL, " +
                        "quantity INTEGER NOT NULL, unitPriceToman INTEGER NOT NULL, " +
                        "PRIMARY KEY(transactionId, lineIndex), " +
                        "FOREIGN KEY(transactionId) REFERENCES ledger_records(id) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_purchase_lines_transactionId " +
                        "ON purchase_lines(transactionId)"
                )
            }
        }

        fun get(context: Context): JibikDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JibikDatabase::class.java,
                    "jibik_ledger_v1.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
