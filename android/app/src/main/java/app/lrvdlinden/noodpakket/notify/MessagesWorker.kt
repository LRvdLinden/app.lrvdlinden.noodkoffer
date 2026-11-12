package app.lrvdlinden.noodpakket.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.lrvdlinden.noodpakket.MainActivity
import app.lrvdlinden.noodpakket.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * "Messages from Noodpakket" on Android: every few hours the app asks the central function for new messages and shows
 * them as a notification. Opt-in; only a random device number, the app language and the app version are sent
 * (no account, no Google services needed).
 */
class MessagesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (Messages.fetch(applicationContext)) Result.success() else Result.retry()
}

object Messages {
    private const val CHANNEL = "messages"
    private const val PREFS = "messages"
    private const val WORK = "messages-fetch"
    private const val ENDPOINT = "https://vbhkewgyujzgbukkbtml.supabase.co/functions/v1/lrbeheer"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Message(val id: Long, val title: String, val body: String = "", val url: String = "")

    @Serializable
    private data class Reply(val ok: Boolean = false, val messages: List<Message> = emptyList(), val latest: Long = 0)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled", enabled).apply()
        val wm = WorkManager.getInstance(context)
        if (enabled) schedule(context) else {
            wm.cancelUniqueWork(WORK)
            // Nothing to remove on the server for Android: a device that stops asking is cleaned up automatically.
        }
    }

    private fun deviceId(context: Context): String =
        prefs(context).getString("device", null) ?: UUID.randomUUID().toString().also { prefs(context).edit().putString("device", it).apply() }

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.channel_messages), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context) {
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MessagesWorker>(3, TimeUnit.HOURS).setConstraints(network).build(),
        )
        wm.enqueue(OneTimeWorkRequestBuilder<MessagesWorker>().setConstraints(network).build())
    }

    /** Asks for messages newer than the last one seen. The very first time it only remembers where to start. */
    suspend fun fetch(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isEnabled(context)) return@withContext true
        val p = prefs(context)
        val after = p.getLong("after", 0)
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
        val body = buildJsonObject {
            put("action", "noodpakket_messages")
            put("device_id", deviceId(context))
            put("platform", "android")
            put("lang", if (Locale.getDefault().language == "nl") "nl" else "en")
            put("app_version", version)
            put("after", after)
        }.toString()
        val reply = runCatching {
            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            try {
                if (conn.responseCode !in 200..299) null
                else json.decodeFromString<Reply>(conn.inputStream.bufferedReader().use { it.readText() })
            } finally { conn.disconnect() }
        }.getOrNull() ?: return@withContext false
        for (m in reply.messages) post(context, m)
        p.edit().putLong("after", maxOf(after, reply.latest)).apply()
        true
    }

    private fun post(context: Context, m: Message) {
        if (!Reminders.hasPermission(context)) return
        val intent = if (m.url.startsWith("https://")) Intent(Intent.ACTION_VIEW, Uri.parse(m.url))
        else Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(context, m.id.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(m.title)
            .setContentText(m.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(m.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(100_000 + (m.id % 100_000).toInt(), n)
        } catch (_: SecurityException) {
            // Permission was revoked in the meantime.
        }
    }
}
