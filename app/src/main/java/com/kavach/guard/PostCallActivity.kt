package com.kavach.guard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** After a risky call ends: one question, then clear next steps. */
class PostCallActivity : Activity() {

    private lateinit var number: String
    private lateinit var reasons: List<String>
    private var level = Level.SUSPICIOUS
    private var score = 0
    private var answered = false
    private var durationSec = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        read(intent); ask()
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); read(intent); ask() }

    private fun read(i: Intent) {
        number = i.getStringExtra("number").orEmpty()
        reasons = i.getStringArrayListExtra("reasons").orEmpty()
        level = Level.valueOf(i.getStringExtra("level") ?: Level.SUSPICIOUS.name)
        score = i.getIntExtra("score", 0)
        answered = i.getBooleanExtra("answered", false)
        durationSec = i.getLongExtra("dur", 0)
    }

    private val lang get() = Prefs(this).lang
    private val p get() = Messages.pack(lang)

    /** Screen 1: was money, OTP or a video call asked for? */
    private fun ask() {
        val bg = Color.parseColor("#B86E00")
        val root = page(bg)
        root.addView(label("📞 $number", 22f, true))
        val why = CallRules.reasonsText(reasons)
        if (why.isNotBlank()) root.addView(label(why, 15f, false, 6))
        if (answered) root.addView(label("Call length: ${durationSec / 60} min ${durationSec % 60} sec", 14f, false, 4))
        root.addView(label(p.callAsked, 22f, true, 24))
        root.addView(button("✅ ${p.yes}", Color.WHITE, Color.parseColor("#B3121D")) { scam() }, lp(20))
        root.addView(button("❌ ${p.no}", Color.argb(70, 255, 255, 255), Color.WHITE) { safe() }, lp(12))
        root.addView(button("🚫 Block this number", Color.argb(70, 255, 255, 255), Color.WHITE) { block(); finish() }, lp(12))
        show(root, bg)
        VoiceAlert.speak(this, lang) { l -> Messages.pack(l).callAsked }
    }

    /** Screen 2a: it was a scam. */
    private fun scam() {
        block()
        val v = Verdict(Level.SCAM, maxOf(score, 90), Category.SCAM_CALL, reasons)
        val snippet = "$number asked for OTP, PIN, money or a video call"
        val sent = FamilyPing.maybePing(this, v, "a phone call", snippet)

        val bg = Color.parseColor("#B3121D")
        val root = page(bg)
        root.addView(label("⛔ " + p.scam, 24f, true))
        root.addView(label("Do not call back. Do not share OTP or PIN. Do not send money, even if they threaten arrest.", 18f, false, 12))
        root.addView(label(p.helpline, 18f, true, 12))
        root.addView(label("Number blocked. Family: $sent", 13f, false, 8))
        root.addView(button(p.call1930, Color.WHITE, bg) {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930")))
        }, lp(20))
        root.addView(button(p.chakshu, Color.parseColor("#FFE6E6"), Color.BLACK) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sancharsaathi.gov.in/sfc/")))
        }, lp(12))
        val who = Prefs(this).familyName.ifBlank { "family" }
        FamilyPing.whatsAppIntent(this, "Kavach alert: I just got a scam call from $number. They asked for OTP, PIN, money or a video call.")?.let { wa ->
            root.addView(button("💬 Tell $who on WhatsApp", Color.parseColor("#25D366"), Color.WHITE) {
                try { startActivity(wa) } catch (_: Exception) { }
            }, lp(12))
        }
        root.addView(button("OK", Color.argb(70, 255, 255, 255), Color.WHITE) { finish() }, lp(12))
        show(root, bg)
        VoiceAlert.speakVerdict(this, Verdict(Level.SCAM, 90, Category.SCAM_CALL, emptyList()), lang)
    }

    /** Screen 2b: nothing was asked. */
    private fun safe() {
        val bg = Color.parseColor("#1E5BD8")
        val root = page(bg)
        root.addView(label("👍 Good.", 26f, true))
        root.addView(label("If this number calls again and asks for OTP, PIN, money or a video call, cut the call.", 18f, false, 12))
        root.addView(button("This number is safe (don't warn again)", Color.WHITE, bg) {
            val n = CallRules.normalize(number)
            val prefs = Prefs(this)
            prefs.trustedNumbers = prefs.trustedNumbers + n
            prefs.blockedNumbers = prefs.blockedNumbers - n
            finish()
        }, lp(20))
        root.addView(button("OK", Color.argb(70, 255, 255, 255), Color.WHITE) { finish() }, lp(12))
        show(root, bg)
    }

    private fun block() {
        val n = CallRules.normalize(number)
        if (n.isBlank()) return
        val prefs = Prefs(this)
        prefs.blockedNumbers = prefs.blockedNumbers + n
        prefs.trustedNumbers = prefs.trustedNumbers - n
    }

    // ---------- ui helpers ----------
    private fun page(bg: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(40), dp(24), dp(24))
        setBackgroundColor(bg)
    }

    private fun show(root: LinearLayout, bg: Int) {
        window.statusBarColor = bg
        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(root) })
    }

    private fun label(t: String, size: Float, bold: Boolean, top: Int = 0) = TextView(this).apply {
        text = t; textSize = size; setTextColor(Color.WHITE)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.15f)
        layoutParams = lp(top)
    }

    private fun button(t: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 18f; setTextColor(fg); minHeight = dp(58)
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(bg) }
        setOnClickListener { onClick() }
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        fun intent(context: Context, number: String, risk: CallRules.Risk, answered: Boolean, durSec: Long) =
            Intent(context, PostCallActivity::class.java)
                .putExtra("number", number)
                .putStringArrayListExtra("reasons", ArrayList(risk.reasons))
                .putExtra("level", risk.level.name)
                .putExtra("score", risk.score)
                .putExtra("answered", answered)
                .putExtra("dur", durSec)
    }
}
