package ir.jibik.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Opt-in offline background reminder: never displays account, title, or amount. */
internal class JibikDueReminderWorker(
    context: Context, parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!LocalDueReminders.isEnabled(app) || !LocalDueReminders.permissionAvailable(app)) {
            return Result.success()
        }
        return try {
            val now = System.currentTimeMillis()
            val calendar = Calendar.getInstance(
                TimeZone.getTimeZone("Asia/Tehran"), ULocale("fa_IR@calendar=persian"),
            )
            calendar.timeInMillis = now
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val today = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            val tomorrow = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, 3)
            val afterThirdDay = calendar.timeInMillis
            val db = JibikDatabase.get(app)
            val summary = summarizeDueReminders(
                db.obligationDao().list(), db.obligationPaymentDao().list(),
                today, tomorrow, afterThirdDay,
            )
            if (summary.totalCount == 0) return Result.success()
            val dayKey = formatJalaliDueDate(now)
            val prefs = app.getSharedPreferences(LocalDueReminders.PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(LocalDueReminders.LAST_DAY, null) == dayKey) {
                return Result.success()
            }

            val manager = app.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    LocalDueReminders.CHANNEL_ID, "یادآوری مالی جیبو",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "یادآوری سررسید مالی بدون نمایش داده حساس"
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )
            val open = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(LocalDueReminders.OPEN_OBLIGATIONS, true)
            }
            val click = PendingIntent.getActivity(
                app, 271, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val body = listOfNotNull(
                summary.overdueCount.takeIf { it > 0 }?.let { it.toString() + " تعهد سررسیدگذشته" },
                summary.todayCount.takeIf { it > 0 }?.let { it.toString() + " تعهد امروز" },
                summary.upcomingCount.takeIf { it > 0 }?.let { it.toString() + " تعهد نزدیک به سررسید" },
            ).joinToString(" · ")
            val notification = NotificationCompat.Builder(app, LocalDueReminders.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_jibik_due_notification)
                .setContentTitle("جیبو • یادآوری تعهدات")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(click)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(app).notify(
                LocalDueReminders.NOTIFICATION_ID, notification,
            )
            check(prefs.edit().putString(LocalDueReminders.LAST_DAY, dayKey).commit())
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

internal object LocalDueReminders {
    const val PREFS = "jibik_private_due_reminders_v1"
    const val LAST_DAY = "last_notified_jalali_day"
    const val OPEN_OBLIGATIONS = "jibik_open_obligations"
    const val CHANNEL_ID = "jibik_due_reminders_v1"
    const val NOTIFICATION_ID = 1704
    private const val OPTED_IN = "user_opted_in"
    private const val UNIQUE_WORK = "jibik_due_daily"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(OPTED_IN, false)

    fun permissionAvailable(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Roughly 9 AM Tehran; WorkManager is battery-aware and not an exact alarm. */
    fun enable(context: Context): Boolean {
        if (!permissionAvailable(context)) return false
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance(
            TimeZone.getTimeZone("Asia/Tehran"), ULocale("fa_IR@calendar=persian"),
        )
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, 9)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_MONTH, 1)
        val delay = calendar.timeInMillis - now
        val work = PeriodicWorkRequestBuilder<JibikDueReminderWorker>(
            24, TimeUnit.HOURS,
        ).setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, work,
        )
        // Write consent after scheduling succeeds; an error must not show
        // a falsely enabled state in the app.
        check(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(OPTED_IN, true).commit())
        return true
    }

    fun disable(context: Context) {
        val app = context.applicationContext
        check(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(OPTED_IN, false).commit())
        WorkManager.getInstance(app).cancelUniqueWork(UNIQUE_WORK)
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
    }
}
