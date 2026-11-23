package ir.jibik.app

import android.content.Context
import androidx.room.ColumnInfo
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
    @ColumnInfo(defaultValue = "'عدد'") val unit: String = "عدد",
)

@Dao
internal interface PurchaseLineDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(lines: List<PurchaseLineEntity>)

    @Query("SELECT * FROM purchase_lines WHERE transactionId = :transactionId ORDER BY lineIndex")
    suspend fun forTransaction(transactionId: String): List<PurchaseLineEntity>

    @Query("SELECT * FROM purchase_lines ORDER BY transactionId, lineIndex")
    suspend fun all(): List<PurchaseLineEntity>
}

/** User-defined private categories, kept even when archived for history stability. */
@Entity(
    tableName = "custom_categories",
    indices = [Index(value = ["kind", "parentPath", "title"], unique = true)],
)
internal data class CustomCategoryEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val parentPath: String,
    val title: String,
    val archived: Boolean = false,
    val createdAtMillis: Long,
) {
    val displayName: String
        get() = if (parentPath.isEmpty()) title else "$parentPath / $title"
}

@Dao
internal interface CustomCategoryDao {
    @Query("SELECT * FROM custom_categories ORDER BY createdAtMillis, id")
    suspend fun list(): List<CustomCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CustomCategoryEntity)

    @Query("UPDATE custom_categories SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)
}

/** One monthly cap per hierarchical expense path. Caps never post transactions. */
@Entity(tableName = "category_budgets")
internal data class CategoryBudgetEntity(
    @PrimaryKey val categoryPath: String,
    val limitToman: Long,
    val warningPercent: Int = 80,
)

@Dao
internal interface CategoryBudgetDao {
    @Query("SELECT * FROM category_budgets ORDER BY categoryPath")
    suspend fun list(): List<CategoryBudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: CategoryBudgetEntity)

    @Query("DELETE FROM category_budgets WHERE categoryPath = :path")
    suspend fun remove(path: String)
}

/** A liability is a memo until a real payment is posted atomically to the ledger. */
@Entity(tableName = "financial_obligations", indices = [Index(value = ["dueAtMillis"])])
internal data class FinancialObligationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    val totalToman: Long,
    val dueAtMillis: Long,
    val installmentCount: Int,
    val note: String,
    val createdAtMillis: Long,
)

@Dao
internal interface FinancialObligationDao {
    @Query("SELECT * FROM financial_obligations ORDER BY dueAtMillis, createdAtMillis, id")
    suspend fun list(): List<FinancialObligationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(obligation: FinancialObligationEntity)
}

/** Exactly one accounting entry per confirmed repayment/collection. */
@Entity(
    tableName = "obligation_payments",
    foreignKeys = [
        ForeignKey(
            entity = FinancialObligationEntity::class,
            parentColumns = ["id"], childColumns = ["obligationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = LedgerEntity::class,
            parentColumns = ["id"], childColumns = ["transactionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["obligationId"])],
)
internal data class ObligationPaymentEntity(
    @PrimaryKey val transactionId: String,
    val obligationId: String,
    val amountToman: Long,
    val paidAtMillis: Long,
)

@Dao
internal interface ObligationPaymentDao {
    @Query("SELECT * FROM obligation_payments ORDER BY paidAtMillis DESC, transactionId")
    suspend fun list(): List<ObligationPaymentEntity>

    @Query("SELECT * FROM obligation_payments WHERE obligationId = :id ORDER BY paidAtMillis DESC")
    suspend fun forObligation(id: String): List<ObligationPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: ObligationPaymentEntity)
}

/** A goal points to one real savings account; it cannot mint imaginary savings. */
@Entity(
    tableName = "savings_goals",
    foreignKeys = [ForeignKey(
        entity = FinancialAccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index(value = ["accountId"], unique = true)],
)
internal data class SavingsGoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val accountId: String,
    val targetToman: Long,
    val createdAtMillis: Long,
)

@Dao
internal interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY createdAtMillis DESC, id DESC")
    suspend fun list(): List<SavingsGoalEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(goal: SavingsGoalEntity)
}

@Database(
    entities = [
        LedgerEntity::class, FinancialAccountEntity::class,
        PurchaseLineEntity::class, SavingsGoalEntity::class,
        CustomCategoryEntity::class, CategoryBudgetEntity::class,
        FinancialObligationEntity::class, ObligationPaymentEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
internal abstract class JibikDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun accountDao(): FinancialAccountDao
    abstract fun purchaseLineDao(): PurchaseLineDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun customCategoryDao(): CustomCategoryDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao
    abstract fun obligationDao(): FinancialObligationDao
    abstract fun obligationPaymentDao(): ObligationPaymentDao

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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS savings_goals (" +
                        "id TEXT NOT NULL, title TEXT NOT NULL, accountId TEXT NOT NULL, " +
                        "targetToman INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, " +
                        "PRIMARY KEY(id), FOREIGN KEY(accountId) REFERENCES " +
                        "financial_accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_savings_goals_accountId " +
                        "ON savings_goals(accountId)"
                )
            }
        }

        /** Preserve all historical ledger and receipt rows, add only local category metadata. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS custom_categories (" +
                        "id TEXT NOT NULL, kind TEXT NOT NULL, parentPath TEXT NOT NULL, " +
                        "title TEXT NOT NULL, archived INTEGER NOT NULL, " +
                        "createdAtMillis INTEGER NOT NULL, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_custom_categories_kind_parentPath_title ON " +
                        "custom_categories(kind, parentPath, title)"
                )
            }
        }

        /** Safe receipt detail upgrade: old rows use the neutral "عدد" unit. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE purchase_lines ADD COLUMN " +
                        "unit TEXT NOT NULL DEFAULT 'عدد'"
                )
            }
        }

        /** Append-only schema upgrade. Existing accounts, records and receipts survive. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS category_budgets (" +
                        "categoryPath TEXT NOT NULL, limitToman INTEGER NOT NULL, " +
                        "warningPercent INTEGER NOT NULL, PRIMARY KEY(categoryPath))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS financial_obligations (" +
                        "id TEXT NOT NULL, title TEXT NOT NULL, kind TEXT NOT NULL, " +
                        "totalToman INTEGER NOT NULL, dueAtMillis INTEGER NOT NULL, " +
                        "installmentCount INTEGER NOT NULL, note TEXT NOT NULL, " +
                        "createdAtMillis INTEGER NOT NULL, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_financial_obligations_dueAtMillis " +
                        "ON financial_obligations(dueAtMillis)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS obligation_payments (" +
                        "transactionId TEXT NOT NULL, obligationId TEXT NOT NULL, " +
                        "amountToman INTEGER NOT NULL, paidAtMillis INTEGER NOT NULL, " +
                        "PRIMARY KEY(transactionId), " +
                        "FOREIGN KEY(obligationId) REFERENCES financial_obligations(id) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                        "FOREIGN KEY(transactionId) REFERENCES ledger_records(id) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_obligation_payments_obligationId " +
                        "ON obligation_payments(obligationId)"
                )
            }
        }

        fun get(context: Context): JibikDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JibikDatabase::class.java,
                    "jibik_ledger_v1.db",
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                ).build().also { instance = it }
            }
    }
}
