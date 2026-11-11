package app.lrvdlinden.noodkoffer.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.lrvdlinden.noodkoffer.MainActivity
import app.lrvdlinden.noodkoffer.R
import app.lrvdlinden.noodkoffer.data.KitRepository
import app.lrvdlinden.noodkoffer.data.KitState
import app.lrvdlinden.noodkoffer.data.KitSummary
import app.lrvdlinden.noodkoffer.ui.displayName
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

/**
 * Checks expiry dates once a day and sends local notifications 30 and 7 days before,
 * on the day itself, once when expired, and at the half-yearly check.
 * Every threshold is remembered, so a skipped day never causes a missed or duplicate reminder.
 */
class ExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Reminders.check(applicationContext, KitRepository.read(applicationContext))
        return Result.success()
    }
}

object Reminders {
    private const val CHANNEL = "expiry"
    private const val PREFS = "reminders"
    private const val WORK = "expiry-check"
    private val THRESHOLDS = listOf(30, 7, 0)

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
        if (enabled) schedule(context) else WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.channel_expiry), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ExpiryWorker>(1, TimeUnit.DAYS).build(),
        )
        wm.enqueue(OneTimeWorkRequestBuilder<ExpiryWorker>().build())
    }

    fun check(context: Context, state: KitState, today: LocalDate = LocalDate.now()) {
        if (!isEnabled(context) || !hasPermission(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sent = prefs.getStringSet("sent", emptySet())!!.toMutableSet()
        val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
        val summary = KitSummary.of(state, today)

        for (item in summary.upcoming + summary.expired) {
            val days = item.daysLeft(today) ?: continue
            val date = item.expiryDate ?: continue
            val threshold = if (days < 0) -1 else THRESHOLDS.filter { days <= it }.minOrNull() ?: continue
            val key = "${item.id}|${item.expiresAt}|$threshold"
            if (key in sent) continue
            sent += key
            val text = when {
                days < 0 -> context.getString(R.string.notify_expired)
                days == 0 -> context.getString(R.string.notify_today)
                else -> context.getString(R.string.notify_soon, days, date.format(fmt))
            }
            post(context, item.id.hashCode(), item.displayName(context), text)
        }

        state.plan.nextCheck?.let { check ->
            val due = runCatching { LocalDate.parse(check) }.getOrNull()
            if (due != null && !due.isAfter(today) && "check|$check" !in sent) {
                sent += "check|$check"
                post(context, 1, context.getString(R.string.notify_check_title), context.getString(R.string.notify_check_text))
            }
        }
        // Keep only keys for items that still exist, so the set does not grow forever.
        val ids = state.items.map { it.id }.toSet()
        sent.retainAll { it.startsWith("check|") || it.substringBefore('|') in ids }
        prefs.edit().putStringSet("sent", sent).apply()
    }

    private fun post(context: Context, id: Int, title: String, text: String) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission was revoked in the meantime.
        }
    }
}
