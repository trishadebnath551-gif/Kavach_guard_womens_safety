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
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Full-screen red (scam) or amber (suspicious) warning with one-tap help. */
class AlertActivity : Activity() {

    private lateinit var verdict: Verdict

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        render(intent)
    }

    private fun render(i: Intent) {
        val level = Level.valueOf(i.getStringExtra(EXTRA_LEVEL) ?: Level.SUSPICIOUS.name)
        val cat = Category.valueOf(i.getStringExtra(EXTRA_CAT) ?: Category.GENERIC.name)
        verdict = Verdict(level, i.getIntExtra(EXTRA_SCORE, 0), cat, emptyList())
        val app = i.getStringExtra(EXTRA_APP).orEmpty()
        val text = i.getStringExtra(EXTRA_TEXT).orEmpty()
        val lang = Prefs(this).lang
        val scam = level == Level.SCAM
        val bg = if (scam) Color.parseColor("#B3121D") else Color.parseColor("#B86E00")

        window.statusBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(24), dp(40), dp(24), dp(24))
        }

        root.addView(label(if (scam) "⛔" else "⚠️", 64f, bold = false).apply { gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(label(Messages.headline(level, lang), 26f, bold = true))
        root.addView(label(Messages.advice(cat, lang), 20f, bold = false, top = 16))
        if (scam) root.addView(label(Messages.helpline(lang), 18f, bold = true, top = 16))

        if (text.isNotBlank()) {
            val box = TextView(this).apply {
                this.text = (if (app.isNotBlank()) "$app\n" else "") + "“" + text.take(220) + "”"
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat(); setColor(Color.argb(60, 0, 0, 0))
                }
            }
            root.addView(box, lp(top = 20))
        }

        root.addView(button(Messages.understood(lang), Color.WHITE, bg) {
            VoiceAlert.stop(); finish()
        }, lp(top = 28))
        root.addView(button(Messages.call1930(lang), Color.parseColor("#FFE6E6"), Color.BLACK) {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930")))
        }, lp(top = 12))
        root.addView(button(Messages.reportChakshu(lang), Color.parseColor("#FFE6E6"), Color.BLACK) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sancharsaathi.gov.in/sfc/")))
        }, lp(top = 12))
        val prefs = Prefs(this)
        val waNumber = prefs.familyPhone.filter { it.isDigit() }.let { d ->
            when {
                d.length == 10 -> "91$d"
                d.length == 12 && d.startsWith("91") -> d
                else -> ""
            }
        }
        if (waNumber.isNotBlank()) {
            val who = prefs.familyName.ifBlank { "family" }
            root.addView(button("💬 Tell $who on WhatsApp", Color.parseColor("#25D366"), Color.WHITE) {
                val msg = "Kavach alert: " + FamilyPing.alertText(this, verdict, app.ifBlank { "my phone" }, text, System.currentTimeMillis())
                val uri = Uri.parse("https://wa.me/$waNumber?text=" + Uri.encode(msg))
                try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { }
            }, lp(top = 12))
        }
        root.addView(button("🔊", Color.argb(60, 255, 255, 255), Color.WHITE) {
            VoiceAlert.speakVerdict(this, verdict, lang)
        }, lp(top = 12))

        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(root) })
        vibrate()
    }

    private fun vibrate() {
        val vib = getSystemService(Vibrator::class.java) ?: return
        val pattern = longArrayOf(0, 400, 200, 400, 200, 700)
        if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
        else @Suppress("DEPRECATION") vib.vibrate(pattern, -1)
    }

    private fun label(t: String, size: Float, bold: Boolean, top: Int = 0) = TextView(this).apply {
        text = t; textSize = size; setTextColor(Color.WHITE)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.15f)
        layoutParams = lp(top = top)
    }

    private fun button(t: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 17f; setTextColor(fg)
        minHeight = dp(56)
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(bg) }
        setOnClickListener { onClick() }
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_LEVEL = "level"
        private const val EXTRA_CAT = "cat"
        private const val EXTRA_SCORE = "score"
        private const val EXTRA_APP = "app"
        private const val EXTRA_TEXT = "text"

        fun intent(context: Context, v: Verdict, app: String, text: String) =
            Intent(context, AlertActivity::class.java)
                .putExtra(EXTRA_LEVEL, v.level.name)
                .putExtra(EXTRA_CAT, v.category.name)
                .putExtra(EXTRA_SCORE, v.score)
                .putExtra(EXTRA_APP, app)
                .putExtra(EXTRA_TEXT, text)
    }
}
