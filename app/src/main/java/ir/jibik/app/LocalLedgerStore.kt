package ir.jibik.app

import android.content.Context
import androidx.room.withTransaction
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

    suspend fun load(): List<LedgerRecord> = withContext(Dispatchers.IO) {
        migrateLegacyIfNeeded()
        dao.getAll().map { it.toRecord() }
    }

    suspend fun append(record: LedgerRecord) = withContext(Dispatchers.IO) {
        require(record.id.isNotBlank())
        require(record.amountToman > 0L)
        migrateLegacyIfNeeded()
        dao.insert(record.toEntity())
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
                check(dao.countMatchingIds(legacy.map { it.id }) == legacy.size) {
                    "Legacy ledger was not completely imported"
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
