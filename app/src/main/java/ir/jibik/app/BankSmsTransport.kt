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
    suspend fun scan(context: Context, limit: Int = 400): List<BankSmsMatch> =
        withContext(Dispatchers.IO) {
            require(limit in 1..500)
            check(
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                    PackageManager.PERMISSION_GRANTED
            ) { "Permission is required to scan bank messages" }
            val matches = mutableListOf<BankSmsMatch>()
            val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
            val salt = BankSmsSettings.fingerprintSalt(context)
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, projection, null, null,
                Telephony.Sms.DATE + " DESC",
            )?.use { cursor ->
                val sender = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                var checked = 0
                while (checked < limit && cursor.moveToNext()) {
                    checked++
                    BankSmsParser.parse(
                        cursor.getString(sender).orEmpty(),
                        cursor.getString(body).orEmpty(),
                        cursor.getLong(date), salt,
                    )?.takeIf { it.amountToman != null }?.let(matches::add)
                }
            }
            matches.distinctBy { it.fingerprint }
        }
}

/** SMS_RECEIVED never changes account balances or captures OTP content. */
internal class BankSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION ||
            !BankSmsSettings.isEnabled(context)
        ) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val match = BankSmsParser.parse(
            messages.first().displayOriginatingAddress.orEmpty(),
            messages.joinToString("") { it.displayMessageBody.orEmpty() },
            messages.first().timestampMillis,
            BankSmsSettings.fingerprintSalt(context),
        )?.takeIf {
            it.bankId in BankSmsSettings.approvedBanks(context) &&
                it.amountToman != null
        } ?: return

        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (BankSmsSettings.isEnabled(context) &&
                    match.bankId in BankSmsSettings.approvedBanks(context)
                ) {
                    val count = LocalLedgerStore(context).importBankSms(listOf(match))
                    if (count > 0) BankSmsNotifications.show(context)
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
    fun show(context: Context) {
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
        val notification = NotificationCompat.Builder(context, BankSmsSettings.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jibik_due_notification)
            .setContentTitle("جیبو • پیام بانکی شناسایی شد")
            .setContentText("برای بررسی و ثبت اختیاری تراکنش وارد جیبیک شو.")
            .setContentIntent(click)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(
            BankSmsSettings.NOTIFICATION_ID, notification,
        )
    }

    fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(BankSmsSettings.NOTIFICATION_ID)
    }
}
