package com.kavach.guard

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SOS: sends the first alert at once, then a fresh live-location SMS every 3 minutes (works without mobile data)
 * to the trusted contacts, and the same text through Family Link if paired. Runs until stopped or 60 minutes.
 */
class SosService : Service(), LocationListener {
    private val handler = Handler(Looper.getMainLooper())
    private var last: Location? = null
    private var rounds = 0
    private val tick = object : Runnable {
        override fun run() {
            sendRound()
            if (++rounds < 20) handler.postDelayed(this, 3 * 60 * 1000L) else stopSelf()
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (i?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (active) return START_STICKY
        active = true
        val n = notification()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(ID, n)
        startLocation()
        handler.postDelayed(tick, 4000)
        return START_STICKY
    }

    private fun startLocation() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (!lm.isProviderEnabled(p)) continue
                lm.requestLocationUpdates(p, 15000L, 0f, this, Looper.getMainLooper())
                lm.getLastKnownLocation(p)?.let { k -> if (last == null || k.time > last!!.time) last = k }
            }
        } catch (e: SecurityException) { Log.w(TAG, "no location", e) }
    }

    private fun sendRound() {
        val prefs = Prefs(this)
        val name = prefs.userName.ifBlank { "Someone" }
        val l = last
        val where = if (l != null) "https://maps.google.com/?q=${l.latitude},${l.longitude}" else "location not found yet"
        val time = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date())
        val msg = "🆘 $name needs help! Live location: $where (at $time). Please call her or police 112. Sent by Kavach."
        SafetyPrefs.contacts(this).forEach { sms(it, msg) }
        if (prefs.pairCode.isNotBlank()) Relay.publish(prefs.pairCode, "🆘 SOS: $name", msg, JSONObject().put("category", "SOS"))
    }

    private fun sms(to: String, msg: String) {
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return
        try {
            val m = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            m.sendMultipartTextMessage(to, null, m.divideMessage(msg), null, null)
        } catch (e: Exception) { Log.w(TAG, "SOS SMS failed", e) }
    }

    private fun notification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val stop = PendingIntent.getService(this, 0, Intent(this, SosService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH, "SOS", NotificationManager.IMPORTANCE_HIGH))
            Notification.Builder(this, CH)
        } else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setSmallIcon(R.drawable.ic_shield).setContentTitle("🆘 SOS active")
            .setContentText("Sharing your live location with trusted contacts").setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop SOS", stop).build()).build()
    }

    override fun onLocationChanged(l: Location) { last = l }
    @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    override fun onProviderEnabled(p: String) {}
    override fun onProviderDisabled(p: String) {}

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        try { (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this) } catch (_: Exception) {}
        active = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KavachSOS"
        private const val CH = "kavach_sos"
        private const val ID = 4711
        const val ACTION_STOP = "com.kavach.guard.SOS_STOP"
        @Volatile var active = false
        fun start(c: Context) {
            val i = Intent(c, SosService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }
        fun stop(c: Context) { c.startService(Intent(c, SosService::class.java).setAction(ACTION_STOP)) }
    }
}
