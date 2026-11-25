package ir.jibik.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/** Exact address allowlist. Do not infer identity from a substring of the SMS body. */
internal fun bankSenderKey(input: String): String? {
    val value = BankSmsParser.normalise(input).trim().lowercase(Locale.ROOT)
    // Preserve +, leading zeros and exact alphanumeric sender IDs. Never merge short codes.
    return value.takeIf { it.length in 1..48 &&
        it.all { c -> c in 'a'..'z' || c in '0'..'9' || c in "+_-." } &&
        it.any { c -> c in 'a'..'z' || c in '0'..'9' } }
}

internal data class BankSenderProfile(
    val id: String,
    val bankId: String,
    val sender: String,
    val accountLast4: String,
    val accountId: String,
    val enabled: Boolean = true,
    val autoTemplates: Set<String> = emptySet(),
) {
    init {
        require(id.isNotBlank() && accountId.isNotBlank())
        require(BankSmsParser.banks.any { it.id == bankId })
        require(bankSenderKey(sender) == sender)
        require(accountLast4.isEmpty() || (accountLast4.length == 4 && accountLast4.all { it in '0'..'9' }))
    }
}

/** Blank last4 means the user explicitly mapped a template with no account identifier.
 * Never use it as a wildcard for a message containing an account number.
 */
internal fun resolveBankSender(profiles: List<BankSenderProfile>, sender: String,
    bankId: String, last4: String): BankSenderProfile? {
    val key = bankSenderKey(sender) ?: return null
    return profiles.filter { it.enabled && it.sender == key && it.bankId == bankId &&
        it.accountLast4 == last4 }.singleOrNull()
}

internal object BankSenderRegistry {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        "jibik_bank_sender_profiles_v1", Context.MODE_PRIVATE)
    @Synchronized fun list(context: Context): List<BankSenderProfile> {
        // Corrupt configuration is not treated as permission to process all senders.
        val json = JSONArray(prefs(context).getString("profiles", "[]"))
        return List(json.length()) { i ->
            val row = json.getJSONObject(i)
            BankSenderProfile(row.getString("id"), row.getString("bank"), row.getString("sender"),
                row.getString("last4"), row.getString("account"), row.getBoolean("enabled"),
                row.optJSONArray("auto")?.let { values ->
                    (0 until values.length()).map { values.getString(it) }.toSet()
                }.orEmpty())
        }
    }
    @Synchronized fun save(context: Context, bankId: String, sender: String,
        last4: String, accountId: String): BankSenderProfile {
        val profile = BankSenderProfile(UUID.randomUUID().toString(), bankId,
            requireNotNull(bankSenderKey(sender)), last4, accountId)
        val rows = list(context)
        require(rows.none { it.sender == profile.sender && it.bankId != bankId }) {
            "Sender already belongs to another bank"
        }
        require(rows.none { it.sender == profile.sender && it.accountLast4 == last4 }) {
            "Sender/account already exists"
        }
        require(rows.size < 100)
        write(context, rows + profile)
        return profile
    }
    @Synchronized fun setEnabled(context: Context, id: String, enabled: Boolean) {
        val rows = list(context)
        require(rows.any { it.id == id })
        write(context, rows.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }
    @Synchronized fun approveAutoTemplate(context: Context, id: String, template: String) {
        val rows = list(context)
        val profile = rows.single { it.id == id && it.enabled }
        require(isSupportedAutoTemplate(profile.bankId, template))
        write(context, rows.map { if (it.id == id) it.copy(autoTemplates = it.autoTemplates + template) else it })
    }
    @Synchronized fun stopAuto(context: Context, id: String) {
        write(context, list(context).map { if (it.id == id) it.copy(autoTemplates = emptySet()) else it })
    }
    @Synchronized fun remove(context: Context, id: String) {
        write(context, list(context).filterNot { it.id == id })
    }
    private fun write(context: Context, rows: List<BankSenderProfile>) {
        val json = JSONArray()
        rows.forEach { row -> json.put(JSONObject().put("id", row.id).put("bank", row.bankId)
            .put("sender", row.sender).put("last4", row.accountLast4).put("account", row.accountId)
            .put("enabled", row.enabled).put("auto", JSONArray(row.autoTemplates.toList()))) }
        check(prefs(context).edit().putString("profiles", json.toString()).commit())
    }
    fun active(context: Context): List<BankSenderProfile> =
        runCatching { list(context).filter { it.enabled } }.getOrDefault(emptyList())
}
