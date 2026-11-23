package ir.jibik.app

import android.content.Context
import androidx.room.withTransaction
import ir.jibik.core.finance.Account
import ir.jibik.core.finance.FinanceEngine
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Room is the source of truth for transactions. Legacy SharedPreferences data
 * stays untouched until migration is verified and explicitly marked complete.
 * Preferences still hold onboarding and the budget setting for this milestone.
 */
internal class LocalLedgerStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "jibik_local_v1", Context.MODE_PRIVATE
    )
    private val database = JibikDatabase.get(context)
    private val dao = database.ledgerDao()
    private val accountDao = database.accountDao()

    fun hasFinishedWelcome(): Boolean = preferences.getBoolean("welcome_finished", false)

    fun finishWelcome() {
        check(preferences.edit().putBoolean("welcome_finished", true).commit()) {
            "Unable to persist welcome state"
        }
    }

    fun budgetToman(): Long = preferences.getLong("budget_toman", 5_000_000L)

    fun updateBudgetToman(value: Long) {
        require(value > 0)
        check(preferences.edit().putLong("budget_toman", value).commit()) {
            "Unable to persist budget"
        }
    }

    private suspend fun ensureDefaultWallet() {
        accountDao.insertDefault(FinancialAccountEntity("wallet", "کیف پول جیبیک", 0L))
    }

    suspend fun loadAccounts(): List<Account> = withContext(Dispatchers.IO) {
        ensureDefaultWallet()
        accountDao.list().map { it.toAccount() }
    }

    suspend fun addAccount(account: Account) = withContext(Dispatchers.IO) {
        require(account.id.isNotBlank() && account.id != "wallet")
        require(account.title.isNotBlank() && account.title.length <= 48)
        require(account.openingBalanceToman >= 0L)
        database.withTransaction {
            val current = accountDao.list()
            check(current.none { it.id == account.id || it.title == account.title.trim() }) {
                "Duplicate financial account"
            }
            // Validates the new opening balance against all existing transactions.
            val updated = current.map { it.toAccount() } + account
            FinanceEngine.balances(updated, dao.getAll().map { it.toRecord() })
            accountDao.insert(account.toEntity())
        }
    }

    suspend fun load(): List<LedgerRecord> = withContext(Dispatchers.IO) {
        ensureDefaultWallet()
        migrateLegacyIfNeeded()
        dao.getAll().map { it.toRecord() }
    }

    suspend fun append(record: LedgerRecord) = withContext(Dispatchers.IO) {
        require(record.id.isNotBlank() && record.amountToman > 0L)
        ensureDefaultWallet()
        migrateLegacyIfNeeded()
        database.withTransaction {
            val accounts = accountDao.list().map { it.toAccount() }
            val allRecords = dao.getAll().map { it.toRecord() } + record
            FinanceEngine.balances(accounts, allRecords)
            dao.insert(record.toEntity())
        }
    }

    /**
     * One ledger entry + many receipt lines in the SAME SQLite transaction.
     * Rejected/failed receipts cannot leave partial deductions behind.
     */
    suspend fun appendItemizedPurchase(record: LedgerRecord, lines: List<PurchaseLine>) =
        withContext(Dispatchers.IO) {
            validateItemizedPurchase(record, lines)
            ensureDefaultWallet()
            migrateLegacyIfNeeded()
            database.withTransaction {
                val accounts = accountDao.list().map { it.toAccount() }
                val existing = dao.getAll().map { it.toRecord() }
                FinanceEngine.balances(accounts, existing + record)
                dao.insert(record.toEntity())
                database.purchaseLineDao().insertAll(lines.mapIndexed { index, line ->
                    PurchaseLineEntity(
                        transactionId = record.id, lineIndex = index,
                        title = line.title.trim(), category = line.category.trim(),
                        quantity = line.quantity, unitPriceToman = line.unitPriceToman,
                    )
                })
            }
        }

    suspend fun loadPurchaseLines(transactionId: String): List<PurchaseLine> =
        withContext(Dispatchers.IO) {
            database.purchaseLineDao().forTransaction(transactionId).map {
                PurchaseLine(it.title, it.category, it.quantity, it.unitPriceToman)
            }
        }

    suspend fun loadSavingsGoals(): List<SavingsGoalEntity> =
        withContext(Dispatchers.IO) { database.savingsGoalDao().list() }

    suspend fun addSavingsGoal(goal: SavingsGoalEntity) = withContext(Dispatchers.IO) {
        require(goal.id.isNotBlank())
        require(goal.title.isNotBlank() && goal.title.length <= 60)
        require(goal.targetToman > 0L)
        require(goal.accountId != "wallet") {
            "Choose a dedicated savings account so no funds are counted twice"
        }
        database.withTransaction {
            val accounts = accountDao.list()
            require(accounts.any { it.id == goal.accountId }) { "Account not found" }
            require(database.savingsGoalDao().list().none {
                it.accountId == goal.accountId || it.id == goal.id
            }) { "Savings account already assigned to a goal" }
            database.savingsGoalDao().insert(goal)
        }
    }

    private suspend fun migrateLegacyIfNeeded() {
        if (preferences.getBoolean("room_ledger_imported_v1", false)) return

        // A malformed legacy file is NEVER interpreted as an empty transaction
        // list: fail loudly, retain the original bytes, and allow later recovery.
        val legacyRaw = preferences.getString("ledger", "[]") ?: "[]"
        val parsed = JSONArray(legacyRaw)
        val legacy = buildList {
            for (index in 0 until parsed.length()) {
                val data = parsed.getJSONObject(index)
                val record = LedgerRecord(
                    id = data.getString("id"),
                    kind = LedgerKind.valueOf(data.getString("kind")),
                    amountToman = data.getLong("amount"),
                    fromAccountId = data.optString("from").takeIf { it.isNotEmpty() },
                    toAccountId = data.optString("to").takeIf { it.isNotEmpty() },
                    category = data.optString("category"),
                    note = data.optString("note"),
                    timestampMillis = data.optLong("created_at"),
                )
                require(record.id.isNotBlank() && record.amountToman > 0L) {
                    "Invalid legacy financial record"
                }
                add(record.toEntity())
            }
        }
        check(legacy.map { it.id }.distinct().size == legacy.size) {
            "Duplicate legacy record identifiers"
        }

        database.withTransaction {
            if (legacy.isNotEmpty()) {
                dao.insertLegacy(legacy)
                // IGNORE makes retry idempotent, but an existing row with the
                // same ID and different financial values must never pass as a
                // successful import. Compare full rows before setting the marker.
                val importedById = dao.getAll().associateBy { it.id }
                check(legacy.all { importedById[it.id] == it }) {
                    "Legacy ledger conflicts with existing Room transactions"
                }
            }
        }
        // Mark after DB commit. A crash before this point is safe to retry:
        // insertLegacy uses IGNORE on identical primary keys.
        check(preferences.edit().putBoolean("room_ledger_imported_v1", true).commit()) {
            "Legacy data imported but migration marker could not be persisted"
        }
    }
}

private fun LedgerRecord.toEntity() = LedgerEntity(
    id = id,
    kind = kind.name,
    amountToman = amountToman,
    fromAccountId = fromAccountId,
    toAccountId = toAccountId,
    category = category,
    note = note,
    timestampMillis = timestampMillis,
)

private fun LedgerEntity.toRecord() = LedgerRecord(
    id = id,
    kind = LedgerKind.valueOf(kind),
    amountToman = amountToman,
    fromAccountId = fromAccountId,
    toAccountId = toAccountId,
    category = category,
    note = note,
    timestampMillis = timestampMillis,
)

private fun FinancialAccountEntity.toAccount() =
    Account(id, title, openingBalanceToman)

private fun Account.toEntity() =
    FinancialAccountEntity(id, title.trim(), openingBalanceToman)
