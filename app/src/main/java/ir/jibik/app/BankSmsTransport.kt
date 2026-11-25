package ir.jibik.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** User-initiated; raw message bodies remain in the system SMS provider only. */
internal object BankSmsInboxScanner {
    suspend fun scan(context: Context, limit: Int = 400, receivedSince: Long? = null): List<BankSmsMatch> =
        withContext(Dispatchers.IO) {
            require(limit in 1..500)
            check(
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                    PackageManager.PERMISSION_GRANTED
            ) { "Permission is required to scan bank messages" }
            val profiles = BankSenderRegistry.active(context)
            if (profiles.isEmpty()) return@withContext emptyList()
            val matches = mutableListOf<BankSmsMatch>()
            val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT)
            val salt = BankSmsSettings.fingerprintSalt(context)
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, projection,
                receivedSince?.let { Telephony.Sms.DATE + " >= ?" },
                receivedSince?.let { arrayOf(it.toString()) },
                Telephony.Sms.DATE + " DESC",
            )?.use { cursor ->
                val sender = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val dateSent = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)
                var checked = 0
                while (checked < limit && cursor.moveToNext()) {
                    checked++
                    val address = cursor.getString(sender).orEmpty()
                    val key = bankSenderKey(address) ?: continue
                    val bankId = profiles.filter { it.sender == key }.map { it.bankId }.distinct().singleOrNull() ?: continue
                    // Never read the body of an unapproved address.
                    val received = cursor.getLong(date)
                    val sent = cursor.getLong(dateSent)
                    val message = cursor.getString(body).orEmpty()
                    val timestamp = canonicalBankSmsTimestamp(sent, received)
                    val parsed = BankSmsParser.parse(address, message, timestamp, salt, bankId)
                        ?.takeIf { it.amountToman != null && resolveBankSender(profiles, address, bankId, it.accountLast4) != null }
                        ?: continue
                    val alias = if (timestamp != received)
                        BankSmsParser.parse(address, message, received, salt, bankId)?.fingerprint else null
                    matches.add(parsed.copy(alternateFingerprint = alias,
                        reportedBalanceToman = bankReportedBalanceToman(message, parsed)))
                }
            }
            matches.distinctBy { it.fingerprint }
        }
}

/** Discovery reads only addresses, never message bodies. Explicit user action only. */
internal suspend fun discoverSmsSenders(context: Context): List<String> = withContext(Dispatchers.IO) {
    check(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
    val found = linkedSetOf<String>()
    context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,
        arrayOf(Telephony.Sms.ADDRESS), null, null, Telephony.Sms.DATE + " DESC")?.use { cursor ->
        var checked = 0
        while (checked < 2000 && cursor.moveToNext()) {
            checked++
            bankSenderKey(cursor.getString(0).orEmpty())?.let(found::add)
        }
    }
    found.toList()
}

/** SMS_RECEIVED may post only an explicitly trained, unambiguous transaction. */
internal class BankSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION ||
            !BankSmsSettings.isEnabled(context)
        ) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val address = messages.first().displayOriginatingAddress.orEmpty()
        val key = bankSenderKey(address) ?: return
        if (messages.any { bankSenderKey(it.displayOriginatingAddress.orEmpty()) != key }) return
        val profiles = BankSenderRegistry.active(context)
        val bankId = profiles.filter { it.sender == key }.map { it.bankId }.distinct().singleOrNull() ?: return
        val body = messages.joinToString("") { it.displayMessageBody.orEmpty() }
        val match = BankSmsParser.parse(address,
            body,
            messages.first().timestampMillis, BankSmsSettings.fingerprintSalt(context), bankId
        )?.takeIf { it.amountToman != null && resolveBankSender(profiles, address, bankId, it.accountLast4) != null } ?: return

        val template = strictBankAutoTemplate(body, match)
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (BankSmsSettings.isEnabled(context) &&
                    resolveBankSender(BankSenderRegistry.active(context), address, bankId, match.accountLast4) != null
                ) {
                    val current = resolveBankSender(BankSenderRegistry.active(context), address, bankId, match.accountLast4)
                    val store = LocalLedgerStore(context)
                    val count = store.importBankSms(listOf(match))
                    if (count > 0) {
                        val posted = if (current != null && template != null && template in current.autoTemplates)
                            runCatching { store.tryAutoPostBankSms(match, current, template, context) }.getOrNull()
                        else null
                        BankSmsUiEvents.changed()
                        BankSmsNotifications.show(context, autoTransactionId = posted?.id)
                    }
                }
            } catch (_: Exception) {
                // A malformed SMS must not break the system Messages app.
            } finally {
                result.finish()
            }
        }
    }
}

internal object BankSmsNotifications {
    fun show(context: Context, autoTransactionId: String? = null) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                BankSmsSettings.CHANNEL_ID,
                "پیامک‌های بانکی برای بررسی",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "ثبت اختیاری تراکنش، بدون نمایش مبلغ و شماره حساب"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BankSmsSettings.EXTRA_OPEN_BANK_SMS, true)
        }
        val click = PendingIntent.getActivity(
            context, 281, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, BankSmsSettings.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jibik_due_notification)
            .setContentTitle("جیبو • پیام بانکی شناسایی شد")
            .setContentText(if (autoTransactionId != null) "تراکنش ثبت شد؛ بابت چه چیزی بود؟"
                else "برای بررسی و ثبت اختیاری تراکنش وارد جیبیک شو.")
            .setContentIntent(click)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (autoTransactionId != null && !context.getSharedPreferences("app_protection", Context.MODE_PRIVATE).getBoolean("enabled", false)) {
            val reply = Intent(context, BankAutoReplyReceiver::class.java).apply {
                action = BankAutoReplyReceiver.ACTION
                data = android.net.Uri.Builder().scheme("jibik").authority("bank-reply").appendPath(autoTransactionId).build()
                putExtra(BankAutoReplyReceiver.TRANSACTION, autoTransactionId)
            }
            val mutableFlag = if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, 0, reply, PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag)
            val input = androidx.core.app.RemoteInput.Builder(BankAutoReplyReceiver.NOTE)
                .setLabel("توضیح؛ دسته پیشنهادی آفلاین").build()
            builder.addAction(NotificationCompat.Action.Builder(0, "نوشتن توضیح", pending)
                .addRemoteInput(input).setAllowGeneratedReplies(false).build())
        }
        val manager = NotificationManagerCompat.from(context)
        if (autoTransactionId == null) manager.notify(BankSmsSettings.NOTIFICATION_ID, builder.build())
        else manager.notify(autoTransactionId, BankSmsSettings.NOTIFICATION_ID, builder.build())
    }

    fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(BankSmsSettings.NOTIFICATION_ID)
    }
}

/** Foreground UI observes changes from the background receiver without polling SMS. */
internal object BankSmsUiEvents {
    private val revision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val changes: kotlinx.coroutines.flow.StateFlow<Long> = revision
    @Synchronized fun changed() { revision.value += 1L }
}

/** Explicit, non-exported RemoteInput target. No financial values are editable. */
internal class BankAutoReplyReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "ir.jibik.app.BANK_AUTO_REPLY"
        const val TRANSACTION = "transaction"
        const val NOTE = "bank_auto_note"
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = intent.getStringExtra(TRANSACTION) ?: return
        if (!id.startsWith(BANK_AUTO_PREFIX) || intent.data?.lastPathSegment != id) return
        val note = androidx.core.app.RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NOTE)?.toString()?.trim() ?: return
        if (note.length !in 1..250) return
        // A stale notification cannot bypass a lock enabled after it was issued.
        if (context.getSharedPreferences("app_protection", Context.MODE_PRIVATE).getBoolean("enabled", false) ||
            context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val store = LocalLedgerStore(context)
                val row = store.load().firstOrNull { it.id == id && needsBankAutoDetails(it) } ?: return@launch
                store.completeBankAutoDetailsFromReply(id, note)
                BankSmsUiEvents.changed()
                NotificationManagerCompat.from(context).cancel(id, BankSmsSettings.NOTIFICATION_ID)
            } catch (_: Exception) {
                // Failed reply stays pending in the app; do not create a replacement transaction.
            } finally { result.finish() }
        }
    }
}

/** PDU timestamps match DATE_SENT, while old Inbox imports used DATE. */
internal fun canonicalBankSmsTimestamp(sent: Long, received: Long): Long =
    if (sent > 0L && received > 0L && sent <= received + 60_000L) sent else received

internal fun bankRecoveryStart(enabledSince: Long, now: Long): Long =
    maxOf(enabledSince, now - 7L * 24 * 60 * 60 * 1000)

/** Bounded foreground catch-up: never posts historical messages automatically.
 * The mutex prevents overlapping activity starts from duplicating work.
 */
internal object BankSmsRecovery {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    suspend fun recover(context: Context) {
        if (!BankSmsSettings.isEnabled(context) || !mutex.tryLock()) return
        try {
            val since = BankSmsSettings.recoverySince(context)
            val matches = BankSmsInboxScanner.scan(context, limit = 400,
                receivedSince = bankRecoveryStart(since, System.currentTimeMillis()))
            if (!BankSmsSettings.isEnabled(context)) return
            val active = BankSenderRegistry.active(context)
            val eligible = matches.filter { resolveBankSender(active, it.sender, it.bankId, it.accountLast4) != null }
            val count = LocalLedgerStore(context).importBankSms(eligible)
            if (count > 0) {
                BankSmsUiEvents.changed()
                BankSmsNotifications.show(context)
            }
        } finally { mutex.unlock() }
    }
}
