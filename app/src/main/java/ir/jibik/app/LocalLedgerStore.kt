package ir.jibik.app

import android.content.Context
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import org.json.JSONArray
import org.json.JSONObject

/**
 * Offline prototype store; Room + encryption + migrations replace this in the
 * financial-core milestone. No bank credentials or personal identifiers stored.
 */
internal class LocalLedgerStore(context: Context) {
    private val preferences = context.getSharedPreferences("jibik_local_v1", Context.MODE_PRIVATE)

    fun hasFinishedWelcome(): Boolean = preferences.getBoolean("welcome_finished", false)

    fun finishWelcome() {
        preferences.edit().putBoolean("welcome_finished", true).apply()
    }

    fun budgetToman(): Long = preferences.getLong("budget_toman", 5_000_000L)

    fun updateBudgetToman(value: Long) {
        require(value > 0)
        preferences.edit().putLong("budget_toman", value).apply()
    }

    fun load(): List<LedgerRecord> {
        val raw = preferences.getString("ledger", "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val data = array.getJSONObject(index)
                    add(
                        LedgerRecord(
                            id = data.getString("id"),
                            kind = LedgerKind.valueOf(data.getString("kind")),
                            amountToman = data.getLong("amount"),
                            fromAccountId = data.optString("from").takeIf { it.isNotEmpty() },
                            toAccountId = data.optString("to").takeIf { it.isNotEmpty() },
                            category = data.optString("category"),
                            note = data.optString("note"),
                            timestampMillis = data.optLong("created_at"),
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Do not overwrite malformed data; retain it for manual recovery.
            emptyList()
        }
    }

    fun append(record: LedgerRecord) {
        val json = JSONArray(preferences.getString("ledger", "[]") ?: "[]")
        val item = JSONObject().apply {
            put("id", record.id)
            put("kind", record.kind.name)
            put("amount", record.amountToman)
            put("from", record.fromAccountId ?: "")
            put("to", record.toAccountId ?: "")
            put("category", record.category)
            put("note", record.note)
            put("created_at", record.timestampMillis)
        }
        json.put(item)
        check(preferences.edit().putString("ledger", json.toString()).commit()) {
            "Unable to persist financial record"
        }
    }
}
