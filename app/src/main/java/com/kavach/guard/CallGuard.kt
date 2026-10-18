package com.kavach.guard

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Live state of the current phone call and the on-screen warning banner.
 * Fed by KavachScreeningService (before ringing) and PhoneStateReceiver (answered / ended).
 */
object CallGuard {
    private const val TAG = "KavachCall"
    private const val LONG_CALL_MS = 2 * 60 * 1000L

    data class Session(
        val number: String,
        val risk: CallRules.Risk,
        val start: Long,
        var answeredAt: Long = 0L,
        val simulated: Boolean = false,
    )

    @Volatile var session: Session? = null
    private val main = Handler(Looper.getMainLooper())
    private var banner: View? = null
    private var pingTask: Runnable? = null

    /** Called before the phone rings. Returns the risk so the service can decide to block. */
    fun onIncoming(context: Context, rawNumber: String, verificationFailed: Boolean, simulated: Boolean = false): CallRules.Risk {
        val prefs = Prefs(context)
        val now = System.currentTimeMillis()
        val n = CallRules.normalize(rawNumber)
        val earlier = if (simulated) 0 else prefs.recordCall(n, now)
        val risk = CallRules.evaluate(
            rawNumber, now, prefs.lastScamTextAt, earlier,
            prefs.blockedNumbers, prefs.trustedNumbers, verificationFailed
        )
        session = Session(rawNumber.ifBlank { "Hidden number" }, risk, now, simulated = simulated)
        if (risk.level != Level.SAFE && prefs.guardOn) {
            prefs.addLog("Phone call", Verdict(risk.level, risk.score, Category.SCAM_CALL, risk.reasons),
                "Call from ${rawNumber.ifBlank { "hidden number" }}. ${CallRules.reasonsText(risk.reasons)}")
            EvidenceStore.add(context, "Phone call", rawNumber.ifBlank { "hidden number" }, CallRules.reasonsText(risk.reasons),
                Verdict(risk.level, risk.score, Category.SCAM_CALL, risk.reasons))
            main.post { showBanner(context.applicationContext, risk, rawNumber) }
        }
        Log.i(TAG, "incoming $n -> $risk")
        return risk
    }

    fun onAnswered(context: Context) {
        val s = session ?: return
        if (s.answeredAt == 0L) s.answeredAt = System.currentTimeMillis()
        if (s.risk.level == Level.SAFE || !Prefs(context).guardOn) return
        cancelPing()
        val app = context.applicationContext
        val task = Runnable {
            val cur = session ?: return@Runnable
            if (cur.answeredAt == 0L) return@Runnable
            val v = Verdict(Level.SCAM, maxOf(cur.risk.score, 80), Category.SCAM_CALL, cur.risk.reasons + "long_call")
            val why = CallRules.reasonsText(cur.risk.reasons).ifBlank { "unknown number" }
            FamilyPing.maybePing(app, v, "a phone call", "${cur.number} ($why), on the line for over 2 minutes", force = cur.simulated)
        }
        pingTask = task
        main.postDelayed(task, if (s.simulated) 6000L else LONG_CALL_MS)
    }

    fun onEnded(context: Context) {
        cancelPing()
        main.post { hideBanner(context.applicationContext) }
        val s = session ?: return
        session = null
        if (s.risk.level == Level.SAFE || !Prefs(context).guardOn) return
        val dur = if (s.answeredAt > 0) (System.currentTimeMillis() - s.answeredAt) / 1000 else 0
        val i = PostCallActivity.intent(context, s.number, s.risk, s.answeredAt > 0, dur)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(context)) {
            try { context.startActivity(i); return } catch (_: Exception) { }
        }
        notify(context, "Kavach: was that call a scam?", "Tap to check the call from ${s.number}", i)
    }

    /** Demo: a scam call from abroad rings, gets answered, and ends. */
    fun simulate(context: Context) {
        val app = context.applicationContext
        onIncoming(app, "+92 300 1234567", verificationFailed = false, simulated = true)
        main.postDelayed({ onAnswered(app) }, 4000)
        main.postDelayed({ onEnded(app) }, 12000)
    }

    private fun cancelPing() {
        pingTask?.let { main.removeCallbacks(it) }
        pingTask = null
    }

    // ---------- banner over the call screen ----------

    private fun showBanner(context: Context, risk: CallRules.Risk, number: String) {
        hideBanner(context)
        val lang = Prefs(context).lang
        val scam = risk.level == Level.SCAM
        val title = (if (scam) "⛔ " else "⚠️ ") + Messages.headline(risk.level, lang)
        val body = Messages.pack(lang).callWarn
        val why = CallRules.reasonsText(risk.reasons)

        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) {
            notify(context, title, "$body\n$why", null); return
        }
        val dp = context.resources.displayMetrics.density
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 16 * dp
                setColor(if (scam) Color.parseColor("#E6B3121D") else Color.parseColor("#E6B86E00"))
            }
            addView(TextView(context).apply {
                text = title; textSize = 18f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply { text = body; textSize = 15f; setTextColor(Color.WHITE) })
            if (why.isNotBlank()) addView(TextView(context).apply {
                text = "$number · $why"; textSize = 13f; setTextColor(Color.parseColor("#FFE0E0"))
            })
            addView(TextView(context).apply {
                text = "✕ close"; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.END
                setOnClickListener { hideBanner(context) }
            })
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        @Suppress("DEPRECATION")
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            x = 0; y = (36 * dp).toInt()
            horizontalMargin = 0.03f
        }
        try {
            context.getSystemService(WindowManager::class.java).addView(box, lp)
            banner = box
        } catch (e: Exception) {
            Log.w(TAG, "banner failed", e)
            notify(context, title, "$body\n$why", null)
        }
    }

    private fun hideBanner(context: Context) {
        val b = banner ?: return
        banner = null
        try { context.getSystemService(WindowManager::class.java).removeView(b) } catch (_: Exception) { }
    }

    private fun notify(context: Context, title: String, text: String, open: Intent?) {
        Guard.ensureChannel(context)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, Guard.CHANNEL)
        else @Suppress("DEPRECATION") Notification.Builder(context).setPriority(Notification.PRIORITY_MAX)
        b.setSmallIcon(R.drawable.ic_shield).setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text)).setAutoCancel(true)
            .setCategory(Notification.CATEGORY_CALL)
        if (open != null) b.setContentIntent(
            PendingIntent.getActivity(context, 77, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        context.getSystemService(NotificationManager::class.java).notify(4100, b.build())
    }
}

/**
 * Android's official hook for "caller ID & spam" apps. Runs before the phone rings,
 * sees only the number and network verification, never the call audio.
 */
class KavachScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val incoming = Build.VERSION.SDK_INT < 29 || details.callDirection == Call.Details.DIRECTION_INCOMING
        if (!incoming) { respondToCall(details, CallResponse.Builder().build()); return }

        val number = details.handle?.schemeSpecificPart.orEmpty()
        val failed = Build.VERSION.SDK_INT >= 30 &&
            details.callerNumberVerificationStatus == android.telecom.Connection.VERIFICATION_STATUS_FAILED
        val risk = CallGuard.onIncoming(this, number, failed)
        val prefs = Prefs(this)

        val block = prefs.guardOn && ("reported" in risk.reasons || (prefs.blockHighRisk && risk.level == Level.SCAM))
        val resp = CallResponse.Builder()
        if (block) {
            resp.setDisallowCall(true).setRejectCall(true).setSkipNotification(false).setSkipCallLog(false)
            CallGuard.session = null
        }
        respondToCall(details, resp.build())
    }
}

/** Tells CallGuard when the call is answered and when it ends. */
class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> CallGuard.onAnswered(context)
            TelephonyManager.EXTRA_STATE_IDLE -> CallGuard.onEnded(context)
        }
    }
}
