package ir.jibik.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.util.UUID

/** No SMS content, phone number or financial amount is put in preferences. */
internal object BankSmsSettings {
    private const val PREFS = "jibik_sms_bank_opt_in_v1"
    private const val ENABLED = "enabled"
    private const val BANKS = "bank_ids"
    private const val SALT = "fingerprint_salt"
    private const val ENABLED_SINCE = "enabled_since"
    const val EXTRA_OPEN_BANK_SMS = "jibik_open_bank_sms"
    const val EXTRA_BANK_TRANSACTION = "jibik_bank_transaction_id"
    const val CHANNEL_ID = "jibik_bank_sms_review"
    const val NOTIFICATION_ID = 18103

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(
        PREFS, Context.MODE_PRIVATE
    )

    fun permissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun isEnabled(context: Context) =
        BuildConfig.BANK_SMS_AVAILABLE && prefs(context).getBoolean(ENABLED, false) &&
            permissionGranted(context) && BankSenderRegistry.active(context).isNotEmpty()

    fun approvedBanks(context: Context): Set<String> {
        val known = BankSmsParser.banks.map { it.id }.toSet()
        return prefs(context).getStringSet(BANKS, emptySet())
            .orEmpty().filter { it in known }.toSet()
    }

    fun allowBank(context: Context, bankId: String, allowed: Boolean) {
        require(BankSmsParser.banks.any { it.id == bankId })
        val values = approvedBanks(context).toMutableSet()
        if (allowed) values.add(bankId) else values.remove(bankId)
        check(prefs(context).edit().putStringSet(BANKS, values.toSet()).commit())
    }

    /** User consent is never silently enabled by permission or inbox import. */
    @Synchronized fun setEnabled(context: Context, active: Boolean): Boolean {
        if (active && (!BuildConfig.BANK_SMS_AVAILABLE || !permissionGranted(context) ||
            BankSenderRegistry.active(context).isEmpty())) return false
        val preferences = prefs(context)
        val edit = preferences.edit().putBoolean(ENABLED, active)
        if (active && !preferences.getBoolean(ENABLED, false)) edit.putLong(ENABLED_SINCE, System.currentTimeMillis())
        if (!active) edit.remove(ENABLED_SINCE)
        check(edit.commit())
        return true
    }

    @Synchronized fun recoverySince(context: Context): Long {
        val preferences = prefs(context)
        val since = preferences.getLong(ENABLED_SINCE, 0L)
        if (since > 0L) return since
        val now = System.currentTimeMillis()
        check(preferences.edit().putLong(ENABLED_SINCE, now).commit())
        return now
    }

    @Synchronized fun fingerprintSalt(context: Context): String {
        val prefs = prefs(context)
        val existing = prefs.getString(SALT, null)
        if (existing != null && existing.length >= 8) return existing
        val generated = UUID.randomUUID().toString() + UUID.randomUUID()
        check(prefs.edit().putString(SALT, generated).commit())
        return generated
    }
}
