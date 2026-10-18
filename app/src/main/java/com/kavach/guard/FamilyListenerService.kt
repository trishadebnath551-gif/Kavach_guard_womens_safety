package com.kavach.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * Runs on the FAMILY member's phone. Keeps one open connection to the relay for every linked
 * phone and raises a loud alert the moment that phone catches a scam.
 * Shows a small permanent "Watching over Maa" notification, which Android requires for this.
 */
class FamilyListenerService : Service() {

    @Volatile private var running = false
    private val workers = mutableListOf<Thread>()
    private val seenIds = LinkedHashSet<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val links = Prefs(this).links
        ensureChannels(this)
        val names = links.joinToString(", ") { it.name }.ifBlank { "family" }
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val ongoing = builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Kavach family guard is on")
            .setContentText("Watching over $names")
            .setOngoing(true)
            .setContentIntent(tap)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ONGOING_ID, ongoing, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(ONGOING_ID, ongoing)
        }

        if (links.isEmpty()) {
            stopSelf(); return START_NOT_STICKY
        }
        restartWorkers(links)
        return START_STICKY
    }

    private fun restartWorkers(links: List<Prefs.Link>) {
        running = false
        workers.forEach { it.interrupt() }
        workers.clear()
        running = true
        links.forEach { link -> workers += thread(name = "kavach-listen-${link.name}") { listenLoop(link) } }
    }

    /** Long-lived streaming connection with backoff. Missed alerts are fetched again via `since`. */
    private fun listenLoop(link: Prefs.Link) {
        val prefs = Prefs(this)
        var backoff = 2000L
        while (running) {
            var conn: HttpURLConnection? = null
            try {
                val since = prefs.lastRelayId(link.code).ifBlank { "10m" }
                val url = URL("${Relay.BASE}/${Relay.topic(link.code)}/json?since=" + URLEncoder.encode(since, "UTF-8"))
                conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 90000 // relay sends keepalives well within this
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                backoff = 2000L
                while (running) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val o = JSONObject(line)
                    if (o.optString("event") != "message") continue
                    val id = o.optString("id")
                    prefs.setLastRelayId(link.code, id)
                    val isNew = synchronized(seenIds) {
                        val added = seenIds.add(id)
                        if (seenIds.size > 200) seenIds.remove(seenIds.first())
                        added
                    }
                    if (!isNew) continue
                    onAlert(link, o.optString("title"), o.optString("message"))
                }
            } catch (e: InterruptedException) {
                return
            } catch (e: Exception) {
                Log.w(TAG, "relay connection dropped: ${e.message}")
            } finally {
                conn?.disconnect()
            }
            if (!running) return
            try { Thread.sleep(backoff) } catch (e: InterruptedException) { return }
            backoff = minOf(backoff * 2, 60000L)
        }
    }

    private fun onAlert(link: Prefs.Link, title: String, message: String) {
        Prefs(this).addFamilyLog(link.name, message)
        val intent = FamilyAlertActivity.intent(this, link.name, link.phone, message)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pi = PendingIntent.getActivity(
            this, link.code.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title.ifBlank { "🚨 Kavach alert: ${link.name}" })
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(ALERT_BASE_ID + (link.code.hashCode() and 0xFFF), n)

        if (Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(this)) {
            try { startActivity(intent) } catch (_: Exception) { }
        }
    }

    override fun onDestroy() {
        running = false
        workers.forEach { it.interrupt() }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KavachFamily"
        private const val CHANNEL_STATUS = "kavach_family_status"
        private const val CHANNEL_ALERT = "kavach_family_alerts"
        private const val ONGOING_ID = 2001
        private const val ALERT_BASE_ID = 3000

        /** Start (or refresh) listening. Safe to call any time. */
        fun sync(context: Context) {
            val i = Intent(context, FamilyListenerService::class.java)
            if (Prefs(context).links.isEmpty()) {
                context.stopService(i); return
            }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        private fun builder(context: Context, channel: String): Notification.Builder =
            if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, channel)
            else @Suppress("DEPRECATION") Notification.Builder(context).setPriority(
                if (channel == CHANNEL_ALERT) Notification.PRIORITY_MAX else Notification.PRIORITY_MIN
            )

        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_STATUS) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_STATUS, "Family guard running", NotificationManager.IMPORTANCE_MIN)
                )
            }
            if (nm.getNotificationChannel(CHANNEL_ALERT) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ALERT, "Family scam alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 800)
                    }
                )
            }
        }
    }
}
