package com.kavach.guard

import android.app.Activity
import android.content.BroadcastReceiver
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
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** What the family member sees: who is being targeted, the scam message, and a big Call button. */
class FamilyAlertActivity : Activity() {

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
        super.onNewIntent(intent); render(intent)
    }

    private fun render(i: Intent) {
        val name = i.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "Your family member" }
        val phone = i.getStringExtra(EXTRA_PHONE).orEmpty()
        val message = i.getStringExtra(EXTRA_MSG).orEmpty()
        val bg = Color.parseColor("#B3121D")
        window.statusBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
        }
        root.addView(label("🚨", 60f, false))
        root.addView(label("$name may be talking to a scammer right now", 26f, true))
        root.addView(label("Kavach caught this on $name's phone:", 16f, false, top = 16))
        root.addView(TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.argb(60, 0, 0, 0)) }
        }, lp(top = 8))
        root.addView(label("Call them now and tell them not to share any OTP or PIN, and not to pay anything.", 16f, false, top = 16))

        if (phone.isNotBlank()) {
            root.addView(button("📞 Call $name", Color.WHITE, bg) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            }, lp(top = 24))
        }
        root.addView(button("OK", Color.argb(60, 255, 255, 255), Color.WHITE) { finish() }, lp(top = 12))

        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(root) })

        getSystemService(Vibrator::class.java)?.let { v ->
            val p = longArrayOf(0, 500, 250, 500, 250, 800)
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(p, -1))
            else @Suppress("DEPRECATION") v.vibrate(p, -1)
        }
    }

    private fun label(t: String, size: Float, bold: Boolean, top: Int = 0) = TextView(this).apply {
        text = t; textSize = size; setTextColor(Color.WHITE)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        layoutParams = lp(top = top)
    }

    private fun button(t: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 18f; setTextColor(fg); minHeight = dp(58)
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(bg) }
        setOnClickListener { onClick() }
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_NAME = "name"
        private const val EXTRA_PHONE = "phone"
        private const val EXTRA_MSG = "msg"

        fun intent(context: Context, name: String, phone: String, message: String) =
            Intent(context, FamilyAlertActivity::class.java)
                .putExtra(EXTRA_NAME, name).putExtra(EXTRA_PHONE, phone).putExtra(EXTRA_MSG, message)
    }
}

/** Restarts family listening after the phone reboots or the app updates. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            try { FamilyListenerService.sync(context) } catch (_: Exception) { }
        }
    }
}
