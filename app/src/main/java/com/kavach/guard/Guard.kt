package com.kavach.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** One place that turns an incoming notification into: verdict, warning screen, voice, family ping. */
object Guard {
    const val CHANNEL = "kavach_alerts"
    private const val ARREST_WINDOW_MS = 30 * 60 * 1000L

    private val upiApps = setOf(
        "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm",
        "in.org.npci.upiapp", "in.amazon.mShop.android.shopping", "com.mobikwik_new",
    )

    private val recent = object : LinkedHashMap<Int, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Long>?) = size > 64
    }

    data class Incoming(
        val pkg: String,
        val appLabel: String,
        val title: String,
        val text: String,
        val isCall: Boolean,
    )

    /** Returns the verdict (also for the in-app tester). */
    fun handle(context: Context, n: Incoming, fromTester: Boolean = false): Verdict {
        val prefs = Prefs(context)
        val combined = (n.title + " " + n.text).trim()
        val now = System.currentTimeMillis()

        // De-duplicate: messaging apps repost the same notification many times.
        if (!fromTester) {
            val key = (n.pkg + combined).hashCode()
            val seen = recent[key]
            if (seen != null && now - seen < 2 * 60 * 1000L) return Verdict(Level.SAFE, 0, Category.GENERIC, listOf("dup"))
            recent[key] = now
        }

        var v = ScamClassifier.classify(combined)

        // Remember digital-arrest style text so a video call soon after gets escalated.
        if (v.category == Category.DIGITAL_ARREST && v.level != Level.SAFE) prefs.lastArrestHintAt = now

        // Video call rule: unknown-number video call, or any video call shortly after an arrest-style message.
        val isVideoCall = n.isCall && Regex("video", RegexOption.IGNORE_CASE).containsMatchIn(combined)
        if (isVideoCall) {
            val unknown = ScamClassifier.looksLikeUnknownNumber(n.title)
            val afterHint = now - prefs.lastArrestHintAt < ARREST_WINDOW_MS
            v = when {
                afterHint -> Verdict(Level.SCAM, 90, Category.DIGITAL_ARREST, v.reasons + "video_call_after_arrest_text")
                unknown -> Verdict(Level.SUSPICIOUS, 45, Category.DIGITAL_ARREST, v.reasons + "unknown_video_call")
                else -> v
            }
        }

        // UPI app "request" notifications: paying a request sends money OUT.
        if (n.pkg in upiApps && Regex("request", RegexOption.IGNORE_CASE).containsMatchIn(combined) && v.level == Level.SAFE) {
            v = Verdict(Level.SUSPICIOUS, 40, Category.UPI_FRAUD, v.reasons + "upi_app_request")
        }

        if (v.level == Level.SAFE || !prefs.guardOn) return v
        if (!n.isCall) prefs.lastScamTextAt = now

        prefs.addLog(n.appLabel, v, combined)
        EvidenceStore.add(context, if (fromTester) n.appLabel + " (demo)" else n.appLabel, n.title, combined, v)
        showAlert(context, v, n.appLabel, combined)
        VoiceAlert.speakVerdict(context, v, prefs.lang)
        FamilyPing.maybePing(context, v, n.appLabel, combined)
        return v
    }

    private fun showAlert(context: Context, v: Verdict, app: String, text: String) {
        val intent = AlertActivity.intent(context, v, app, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        // With "Display over other apps" granted we may open the red screen directly.
        if (Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(context)) {
            try {
                context.startActivity(intent); return
            } catch (_: Exception) { /* fall through to notification */ }
        }

        ensureChannel(context)
        val lang = Prefs(context).lang
        val pi = PendingIntent.getActivity(
            context, v.category.ordinal, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL)
        else @Suppress("DEPRECATION") Notification.Builder(context).setPriority(Notification.PRIORITY_MAX)
        val notif = b.setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(Messages.headline(v.level, lang))
            .setContentText(Messages.advice(v.category, lang))
            .setStyle(Notification.BigTextStyle().bigText(Messages.advice(v.category, lang)))
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(1000 + v.category.ordinal, notif)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Scam warnings", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Kavach scam alerts"
                    enableVibration(true)
                }
            )
        }
    }
}
