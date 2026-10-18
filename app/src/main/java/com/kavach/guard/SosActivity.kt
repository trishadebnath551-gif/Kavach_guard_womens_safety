package com.kavach.guard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.LinearLayout
import android.widget.TextView

/** Big red SOS screen: 5 second countdown (cancel if pressed by mistake), then SMS + live location + Family Link. */
class SosActivity : Activity() {
    private var timer: CountDownTimer? = null
    private lateinit var status: TextView
    private lateinit var actions: LinearLayout
    private val lang get() = Prefs(this).lang
    private fun s(k: String) = S.t(this, k, lang)
    private val bg = Color.parseColor("#B3121D")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        val root = Ui.page(this)
        root.addView(Ui.tv(this, "🆘", 64f, Color.WHITE))
        root.addView(Ui.tv(this, s("sos_title"), 28f, Color.WHITE, true))
        status = Ui.tv(this, "", 22f, Color.WHITE, true, top = 16)
        root.addView(status)
        actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(actions, Ui.lp(this, 20))
        setContentView(Ui.scroll(this, root, bg))

        if (SosService.active) { sentState(); return }
        actions.addView(Ui.btn(this, s("cancel"), "#FFFFFF", bg) { timer?.cancel(); finish() }, Ui.lp(this))
        val need = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.SEND_SMS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isEmpty()) countdown() else requestPermissions(need.toTypedArray(), 7)
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) { countdown() }

    private fun countdown() {
        timer?.cancel()
        timer = object : CountDownTimer(5000, 1000) {
            override fun onTick(ms: Long) { status.text = s("sos_countdown") + " " + (ms / 1000 + 1) }
            override fun onFinish() { fire() }
        }.start()
    }

    private fun fire() {
        SosService.start(this)
        sentState()
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
    }

    private fun sentState() {
        actions.removeAllViews()
        val noContact = SafetyPrefs.contacts(this).isEmpty() && Prefs(this).pairCode.isBlank()
        status.text = if (noContact) s("sos_need_contact") else s("sos_sent")
        actions.addView(Ui.btn(this, s("call112"), "#FFFFFF", bg) { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }, Ui.lp(this))
        actions.addView(Ui.btn(this, s("call1091"), "#FFE6E6", Color.BLACK) { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1091"))) }, Ui.lp(this, 12))
        actions.addView(Ui.btn(this, s("police"), "#FFE6E6", Color.BLACK) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode("police station")))) } catch (_: Exception) {}
        }, Ui.lp(this, 12))
        actions.addView(Ui.btn(this, s("stop_sos"), "#7A0B12") { SosService.stop(this); finish() }, Ui.lp(this, 12))
    }

    override fun onDestroy() { timer?.cancel(); super.onDestroy() }
}
