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

@Entity(tableName = "ledger_revisions", indices = [Index("transactionId")])
internal data class LedgerRevision(
    @PrimaryKey(autoGenerate = true) val revisionId: Long = 0,
    val transactionId: String,
    val editedAt: Long,
    @androidx.room.Embedded(prefix = "old_") val previous: LedgerEntity,
)

@Entity(tableName = "financial_edit_history", indices = [Index("transactionId")])
internal data class FinancialEditHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: String,
    val atMillis: Long,
    val action: String,
    val details: String,
)

@Entity(tableName = "monthly_budget")
internal data class MonthlyBudget(@PrimaryKey val id: Int = 1, val amount: Long)

@Entity(tableName = "ledger_voids")
internal data class LedgerVoid(@PrimaryKey val transactionId: String, val voidedAt: Long)

@Dao
internal interface LedgerDao {
    @Query("SELECT * FROM ledger_records WHERE id NOT IN (SELECT transactionId FROM ledger_voids) ORDER BY timestampMillis DESC, id DESC")
    suspend fun getAll(): List<LedgerEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: LedgerEntity)

    @Query("SELECT * FROM ledger_records WHERE id = :id AND id NOT IN (SELECT transactionId FROM ledger_voids)")
    suspend fun find(id: String): LedgerEntity?

    @Query("SELECT amount FROM monthly_budget WHERE id = 1")
    suspend fun budget(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBudget(row: MonthlyBudget)

    @Query("SELECT r.* FROM ledger_records r JOIN ledger_voids v ON v.transactionId = r.id ORDER BY v.voidedAt DESC")
    suspend fun voided(): List<LedgerEntity>

    @Insert
    suspend fun markVoid(row: LedgerVoid)

    @Query("DELETE FROM ledger_voids WHERE transactionId = :id")
    suspend fun unvoid(id: String): Int

    @androidx.room.Update
    suspend fun update(record: LedgerEntity): Int

    @Insert
    suspend fun recordRevision(revision: LedgerRevision)

    @Insert
    suspend fun recordFinancialEdit(row: FinancialEditHistory)

    @Query("SELECT * FROM financial_edit_history WHERE transactionId = :id ORDER BY id DESC")
    suspend fun financialEdits(id: String): List<FinancialEditHistory>

    @Query("SELECT * FROM ledger_revisions WHERE transactionId = :id ORDER BY revisionId DESC")
    suspend fun revisions(id: String): List<LedgerRevision>

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

    @Query("DELETE FROM purchase_lines WHERE transactionId = :transactionId")
    suspend fun removeForTransaction(transactionId: String): Int

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

    @Query("DELETE FROM obligation_payments WHERE transactionId = :id")
    suspend fun detach(id: String): Int
}

/** Opt-in bank SMS inbox: only verified transaction metadata, never raw SMS bodies. */
@Entity(
    tableName = "bank_sms_candidates",
    indices = [Index(value = ["state", "receivedAtMillis"])],
)
internal data class BankSmsCandidateEntity(
    @PrimaryKey val id: String,
    val bankId: String,
    val bankTitle: String,
    val income: Boolean,
    val amountRial: Long,
    val accountLast4: String,
    val receivedAtMillis: Long,
    val state: String = "PENDING",
    val linkedTransactionId: String? = null,
)

@Dao
internal interface BankSmsCandidateDao {
    @Query("SELECT * FROM bank_sms_candidates ORDER BY receivedAtMillis DESC, id DESC LIMIT 400")
    suspend fun recent(): List<BankSmsCandidateEntity>

    @Query("SELECT * FROM bank_sms_candidates WHERE id = :id LIMIT 1")
    suspend fun find(id: String): BankSmsCandidateEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: BankSmsCandidateEntity): Long

    @Query("UPDATE bank_sms_candidates SET state = 'IGNORED' WHERE id = :id AND state = 'PENDING'")
    suspend fun ignore(id: String): Int

    @Query("UPDATE bank_sms_candidates SET state = 'REGISTERED', linkedTransactionId = :transactionId WHERE id = :id AND state = 'PENDING'")
    suspend fun markRegistered(id: String, transactionId: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM bank_sms_candidates WHERE linkedTransactionId = :id)")
    suspend fun isLinked(id: String): Boolean

    @Query("SELECT * FROM bank_sms_candidates WHERE linkedTransactionId = :id")
    suspend fun linked(id: String): List<BankSmsCandidateEntity>

    @Query("UPDATE bank_sms_candidates SET state = 'PENDING', linkedTransactionId = NULL WHERE linkedTransactionId = :id")
    suspend fun detach(id: String): Int

    @Query("DELETE FROM bank_sms_candidates WHERE state != 'PENDING' AND receivedAtMillis < :olderThan")
    suspend fun purgeOlderHandled(olderThan: Long): Int
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
        BankSmsCandidateEntity::class, LedgerRevision::class, MonthlyBudget::class, LedgerVoid::class, RecurringRule::class, RecurringDecision::class, FinancialEditHistory::class,
    ],
    version = 13,
    exportSchema = false,
)
internal abstract class JibikDatabase : RoomDatabase() {
    abstract fun recurringDao(): RecurringDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun accountDao(): FinancialAccountDao
    abstract fun purchaseLineDao(): PurchaseLineDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun customCategoryDao(): CustomCategoryDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao
    abstract fun obligationDao(): FinancialObligationDao
    abstract fun obligationPaymentDao(): ObligationPaymentDao
    abstract fun bankSmsCandidateDao(): BankSmsCandidateDao

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

        /** Adds only bank transaction suggestions; all prior ledger rows survive. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bank_sms_candidates (" +
                        "id TEXT NOT NULL, bankId TEXT NOT NULL, " +
                        "bankTitle TEXT NOT NULL, income INTEGER NOT NULL, " +
                        "amountRial INTEGER NOT NULL, accountLast4 TEXT NOT NULL, " +
                        "receivedAtMillis INTEGER NOT NULL, state TEXT NOT NULL, " +
                        "linkedTransactionId TEXT, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_bank_sms_candidates_state_receivedAtMillis " +
                        "ON bank_sms_candidates(state, receivedAtMillis)"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE ledger_revisions (" +
                    "revisionId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, transactionId TEXT NOT NULL, " +
                    "editedAt INTEGER NOT NULL, old_id TEXT NOT NULL, old_kind TEXT NOT NULL, " +
                    "old_amountToman INTEGER NOT NULL, old_fromAccountId TEXT, old_toAccountId TEXT, " +
                    "old_category TEXT NOT NULL, old_note TEXT NOT NULL, old_timestampMillis INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX index_ledger_revisions_transactionId ON ledger_revisions(transactionId)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE monthly_budget (id INTEGER NOT NULL PRIMARY KEY, amount INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE ledger_voids (transactionId TEXT NOT NULL PRIMARY KEY, voidedAt INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE recurring_rules (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, income INTEGER NOT NULL, accountId TEXT NOT NULL, amount INTEGER NOT NULL, firstDay INTEGER NOT NULL, stoppedAt INTEGER)")
                db.execSQL("CREATE TABLE recurring_decisions (ruleId TEXT NOT NULL, day INTEGER NOT NULL, transactionId TEXT, actual INTEGER, PRIMARY KEY(ruleId, day))")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE financial_edit_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, transactionId TEXT NOT NULL, atMillis INTEGER NOT NULL, action TEXT NOT NULL, details TEXT NOT NULL)")
                db.execSQL("CREATE INDEX index_financial_edit_history_transactionId ON financial_edit_history(transactionId)")
            }
        }

        fun get(context: Context): JibikDatabase =
            instance ?: synchronized(this) {
                instance ?: open(context, "jibik_ledger_v1.db").also { instance = it }
            }

        internal fun open(context: Context, name: String): JibikDatabase =
                Room.databaseBuilder(
                    context.applicationContext,
                    JibikDatabase::class.java,
                    name,
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                ).build()
    }
}
