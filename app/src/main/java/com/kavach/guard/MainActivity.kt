package com.kavach.guard

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Setup screen. Meant to be done once by a son or daughter on the parent's phone:
 * grant access, pick language, add family number, run the demo. After that it works on its own.
 */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var content: LinearLayout
    private val blue = Color.parseColor("#1E5BD8")
    private val ink = Color.parseColor("#14161A")
    private val muted = Color.parseColor("#5B6270")
    private val card = Color.parseColor("#FFFFFF")
    private val page = Color.parseColor("#F2F4F8")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        VoiceAlert.warmUp(this)
        Guard.ensureChannel(this)
        window.statusBarColor = blue
    }

    override fun onResume() {
        super.onResume()
        try { FamilyListenerService.sync(this) } catch (_: Exception) { }
        build()
    }

    private fun build() {
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        roleSwitch()
        if (prefs.role == "family") {
            buildFamily()
            show(); return
        }

        // ---------- Header ----------
        val active = listenerEnabled() && prefs.guardOn
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = round(if (active) blue else Color.parseColor("#8A1C1C"), 18)
            addView(text("🛡️ Kavach Guard", 26f, Color.WHITE, bold = true))
            addView(text(
                if (active) "Protection is ON. Messages, calls and UPI alerts are checked on this phone."
                else "Protection is OFF. Finish the setup below.",
                15f, Color.WHITE, top = 6
            ))
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text("Guard switch", 15f, Color.WHITE), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(Switch(this@MainActivity).apply {
                    isChecked = prefs.guardOn
                    setOnCheckedChangeListener { _, on -> prefs.guardOn = on; build() }
                })
            }, lp(top = 10))
        })

        // ---------- Step 1: permissions ----------
        section("🆘 Women safety") { box ->
            box.addView(primary(S.t(this, "sos_btn", prefs.lang)) { startActivity(Intent(this, SosActivity::class.java)) }, lp(top = 4))
            box.addView(secondary(S.t(this, "speak", prefs.lang)) { startActivity(Intent(this, VoiceActivity::class.java)) }, lp(top = 8))
            box.addView(secondary("📞 " + S.t(this, "harass_title", prefs.lang)) { startActivity(Intent(this, HarassmentActivity::class.java)) }, lp(top = 8))
            box.addView(secondary("🚨 " + S.t(this, "report_title", prefs.lang)) { startActivity(Intent(this, ReportActivity::class.java)) }, lp(top = 8))
            box.addView(text(S.t(this, "trusted_title", prefs.lang), 13f, muted, top = 14))
            val saved = SafetyPrefs.contacts(this)
            val ins = (0..2).map { i -> input(box, "+91 …", saved.getOrElse(i) { "" }, InputType.TYPE_CLASS_PHONE) }
            box.addView(primary(S.t(this, "save", prefs.lang)) {
                SafetyPrefs.setContacts(this, ins.map { it.text.toString() }); toast("✓")
            }, lp(top = 8))
        }

        section("1. One-time setup") {
            permRow(it, "Read notifications (required)",
                "Lets Kavach check SMS, WhatsApp and UPI alerts. Nothing is uploaded.", listenerEnabled()) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            permRow(it, "Show warning over apps",
                "Opens the red warning screen even while another app is open.",
                Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            fullScreenRow(it)
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(android.app.role.RoleManager::class.java)
                permRow(it, "Screen phone calls",
                    "Set Kavach as caller ID & spam app. It sees the number before the phone rings, never the call audio.",
                    rm.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING), 10)
                }
            }
            permRow(it, "Know when a call ends", "Shows the after-call check and alerts family on long risky calls.",
                checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == 0) {
                requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 3)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                permRow(it, "Allow Kavach notifications", "Backup way to show warnings.",
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == 0) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                }
            }
            permRow(it, "Also send SMS (optional)", "Extra backup. Android blocks this on many phones, Family Link works without it.",
                FamilyPing.hasSmsPermission(this)) {
                requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 2)
            }
        }

        // ---------- Step 2: language ----------
        section("2. Warning language") { box ->
            box.addView(text("Warnings are shown and spoken in this language. 13 Indian languages.", 13f, muted))
            Messages.Lang.values().toList().chunked(3).forEach { rowLangs ->
                val row = LinearLayout(this)
                rowLangs.forEach { l ->
                    val selected = prefs.lang == l
                    row.addView(Button(this).apply {
                        text = l.label; isAllCaps = false; textSize = 16f
                        setTextColor(if (selected) Color.WHITE else ink)
                        background = round(if (selected) blue else page, 12)
                        setOnClickListener {
                            prefs.lang = l
                            VoiceAlert.speak(this@MainActivity, l) { sl -> Messages.headline(Level.SCAM, sl) }
                            build()
                        }
                    }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(8) })
                }
                repeat(3 - rowLangs.size) { row.addView(View(this), LinearLayout.LayoutParams(0, dp(52), 1f)) }
                box.addView(row, lp(top = 8))
            }
            box.addView(text("If the phone has no voice for a language, Kavach uses the closest one it has (for example Assamese to Bengali, Odia to Hindi). Add voices in Settings > Text-to-speech.", 13f, muted, top = 8))
        }

        // ---------- Step 3: family ----------
        section("3. Family Guard") { box ->
            box.addView(text("When a scam is caught, Kavach alerts family on their phone straight away. No account or setup needed.", 13f, muted))
            val name = input(box, "Name of the person using this phone (e.g. Maa)", prefs.userName, InputType.TYPE_CLASS_TEXT)
            val famName = input(box, "Family member's name (e.g. Trisha)", prefs.familyName, InputType.TYPE_CLASS_TEXT)
            val phone = input(box, "Family member's WhatsApp number", prefs.familyPhone, InputType.TYPE_CLASS_PHONE)
            fun saveFields() {
                prefs.userName = name.text.toString()
                prefs.familyName = famName.text.toString()
                prefs.familyPhone = phone.text.toString()
            }
            box.addView(primary("Save") { saveFields(); toast("Saved"); build() }, lp(top = 12))

            // Family Link code
            val code = prefs.pairCode
            if (code.isBlank()) {
                box.addView(secondary("👪 Create Family Link code") {
                    saveFields(); prefs.pairCode = Relay.newCode(); build()
                }, lp(top = 12))
            } else {
                box.addView(text("Family Link code", 14f, muted, top = 16))
                box.addView(text(Relay.pretty(code), 30f, ink, bold = true).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setTextIsSelectable(true)
                    setPadding(0, dp(8), 0, dp(8))
                    background = round(page, 12)
                }, lp(top = 6))
                box.addView(text("On the family member's phone: install Kavach, tap \"I'm family\", and enter this code.", 13f, muted, top = 6))
                box.addView(secondary("💬 Share code on WhatsApp") {
                    saveFields()
                    val who = prefs.userName.ifBlank { "me" }
                    val msg = "Please install Kavach Guard and link to $who so you get an alert if a scammer targets $who.\n\n" +
                        "Open Kavach, tap \"I'm family\" and enter this code:\n${Relay.pretty(code)}"
                    startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), "Share code"))
                }, lp(top = 8))
                box.addView(secondary("Send a test alert to family") {
                    saveFields()
                    val v = Verdict(Level.SCAM, 99, Category.GENERIC, listOf("test"))
                    toast("Test: " + FamilyPing.maybePing(this, v, "Kavach test", "This is only a test alert", force = true))
                }, lp(top = 8))
                box.addView(secondary("Make a new code (unlinks current family phones)") {
                    prefs.pairCode = Relay.newCode(); toast("New code made. Share it again."); build()
                }, lp(top = 8))
            }

            box.addView(text("Also on the red warning screen: a \"Tell ${prefs.familyName.ifBlank { "family" }} on WhatsApp\" button with the message ready to send.", 13f, muted, top = 12))

            // Advanced: webhook
            box.addView(secondary(if (showAdvanced) "Hide advanced" else "Advanced (webhook)") {
                showAdvanced = !showAdvanced; build()
            }, lp(top = 12))
            if (showAdvanced) {
                val hook = input(box, "Webhook URL (https://, for n8n)", prefs.webhookUrl, InputType.TYPE_TEXT_VARIATION_URI)
                box.addView(primary("Save webhook") { prefs.webhookUrl = hook.text.toString(); toast("Saved") }, lp(top = 8))
            }
        }

        // ---------- Calls ----------
        section("Phone calls") { box ->
            box.addView(text("Unknown callers are checked before the phone rings. Risky calls get a red banner on the call screen, an after-call check, and a family alert if the call goes past 2 minutes.", 13f, muted))
            box.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
                addView(text("Auto-block high-risk calls", 15f, ink, bold = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(Switch(this@MainActivity).apply {
                    isChecked = prefs.blockHighRisk
                    setOnCheckedChangeListener { _, on -> prefs.blockHighRisk = on }
                })
            })
            box.addView(text("Numbers you report are always blocked.", 12f, muted))
            box.addView(primary("🎙️ Live call guard (transcript)") {
                startActivity(Intent(this, ListenActivity::class.java))
            }, lp(top = 12))
            val blocked = prefs.blockedNumbers.sorted()
            if (blocked.isNotEmpty()) {
                box.addView(text("Blocked numbers", 14f, ink, bold = true, top = 14))
                blocked.forEach { n ->
                    val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
                    row.addView(text("🚫 $n", 15f, ink), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    row.addView(Button(this).apply {
                        text = "Unblock"; isAllCaps = false; setTextColor(ink); background = round(page, 10)
                        setOnClickListener { prefs.blockedNumbers = prefs.blockedNumbers - n; build() }
                    }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(42)))
                    box.addView(row)
                }
            }
        }

        // ---------- Step 4: demo ----------
        section("4. Try it (demo)") { box ->
            box.addView(text("Each button pretends a message just arrived, so you can see the warning, hear the voice and check the family alert.", 13f, muted))
            demo(box, "Fake KYC SMS", "VM-SBIBNK", "Dear customer your SBI account will be blocked today. Update KYC immediately: http://sbi-kyc.xyz/upd", false, "Messages")
            demo(box, "Electricity cut threat", "AD-BSESPW", "Dear consumer your electricity connection will be cut tonight at 9.30 pm. Call 9876543210 immediately", false, "Messages")
            demo(box, "UPI 'sent by mistake'", "+91 70012 34567", "Sir I sent ₹5000 to you by mistake please return. Enter your UPI PIN to receive the refund", false, "WhatsApp")
            demo(box, "Hindi lottery message", "+91 90000 11111", "बधाई हो! आपने KBC लॉटरी में 25 लाख जीत लिए हैं। इनाम पाने के लिए 5000 फीस जमा करें", false, "WhatsApp")
            demo(box, "Bengali lottery message", "+91 91234 55555", "অভিনন্দন! আপনি ২৫ লক্ষ টাকার লটারি জিতেছেন। পুরস্কার পেতে এখনই ৫০০০ টাকা ফি দিন", false, "WhatsApp")
            demo(box, "Tamil electricity cut", "TN-EBSMS", "உங்கள் மின் இணைப்பு இன்று இரவு துண்டிக்கப்படும். உடனே 9876543210 அழைக்கவும்", false, "Messages")
            demo(box, "Telugu OTP request", "+91 90001 33333", "నేను బ్యాంక్ నుండి మాట్లాడుతున్నాను. మీ ఖాతా బ్లాక్ అవుతుంది. వెంటనే OTP చెప్పండి", false, "WhatsApp")
            demo(box, "Odia OTP request", "+91 80000 22222", "ମୁଁ ବ୍ୟାଙ୍କରୁ କହୁଛି, ଆପଣଙ୍କ ଆକାଉଣ୍ଟ ବନ୍ଦ ହୋଇଯିବ। OTP କୁହନ୍ତୁ ତୁରନ୍ତ", false, "WhatsApp")
            box.addView(secondary("Digital arrest (message, then video call)") {
                Guard.handle(this, Guard.Incoming("com.whatsapp", "WhatsApp", "+91 98111 22333",
                    "This is Mumbai Police cyber cell. A parcel in your name has drugs. Arrest warrant issued. Stay on video call, do not tell family", false), fromTester = true)
                Handler(Looper.getMainLooper()).postDelayed({
                    Guard.handle(this, Guard.Incoming("com.whatsapp", "WhatsApp", "+91 98111 22333",
                        "Incoming video call", true), fromTester = true)
                }, 6000)
            }, lp(top = 8))
            box.addView(secondary("📞 Scam phone call from abroad (banner, then after-call check)") {
                if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) toast("Allow \"Show warning over apps\" first to see the banner.")
                CallGuard.simulate(this)
                toast("Incoming call simulated. Watch the top of the screen.")
            }, lp(top = 8))
            demo(box, "Real bank OTP (should be safe)", "AX-HDFCBK", "123456 is your OTP for txn of Rs 2,500 at Amazon. Do not share OTP with anyone. -HDFC Bank", false, "Messages")
        }

        // ---------- Step 5: check any text ----------
        section("5. Check any message") { box ->
            val et = input(box, "Paste a message here", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            et.minLines = 3
            val out = text("", 15f, ink, top = 8)
            box.addView(primary("Check") {
                val v = ScamClassifier.classify(et.text.toString())
                val badge = when (v.level) { Level.SCAM -> "🔴 Scam"; Level.SUSPICIOUS -> "🟠 Suspicious"; Level.SAFE -> "🟢 Looks safe" }
                out.text = "$badge  (score ${v.score}, ${Messages.categoryName(v.category)})\n" +
                    if (v.level != Level.SAFE) Messages.advice(v.category, prefs.lang) else ""
                if (v.level != Level.SAFE) VoiceAlert.speakVerdict(this, v, prefs.lang)
            }, lp(top = 8))
            box.addView(out)
        }

        // ---------- Log ----------
        section("Recent warnings") { box ->
            val arr = prefs.logArray()
            if (arr.length() == 0) box.addView(text("No warnings yet.", 14f, muted))
            val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val dot = if (o.optString("level") == "SCAM") "🔴" else "🟠"
                box.addView(text(
                    "$dot ${fmt.format(Date(o.optLong("t")))} · ${o.optString("app")} · " +
                        Messages.categoryName(Category.valueOf(o.optString("cat", "GENERIC"))) +
                        "\n" + o.optString("text"),
                    13f, ink, top = if (i == 0) 0 else 10
                ))
            }
            if (arr.length() > 0) box.addView(secondary("Clear log") { prefs.clearLog(); build() }, lp(top = 10))
        }

        content.addView(text("Kavach Guard prototype · Cyber fraud helpline 1930", 12f, muted, top = 16).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        show()
    }

    private var showAdvanced = false

    /** Android 14+: without this, alerts only appear as a banner, not the full red screen. */
    private fun fullScreenRow(box: LinearLayout) {
        if (Build.VERSION.SDK_INT < 34) return
        val nm = getSystemService(android.app.NotificationManager::class.java)
        permRow(box, "Full-screen alerts", "Lets the red alert take over the screen, even when the phone is locked.",
            nm.canUseFullScreenIntent()) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        }
    }

    private fun show() =
        setContentView(ScrollView(this).apply { setBackgroundColor(page); addView(content) })

    private fun roleSwitch() {
        val row = LinearLayout(this)
        listOf("protect" to "🛡️ Protect this phone", "family" to "👪 I'm family").forEach { (role, label) ->
            val selected = prefs.role == role
            row.addView(Button(this).apply {
                text = label; isAllCaps = false; textSize = 15f
                setTextColor(if (selected) Color.WHITE else ink)
                background = round(if (selected) ink else card, 12)
                setOnClickListener { prefs.role = role; build() }
            }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(8) })
        }
        content.addView(row, lp())
    }

    private fun buildFamily() {
        val links = prefs.links
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = round(if (links.isNotEmpty()) blue else Color.parseColor("#8A1C1C"), 18)
            addView(text("👪 Kavach Family", 26f, Color.WHITE, bold = true))
            addView(text(
                if (links.isNotEmpty()) "You will get an alert the moment ${links.joinToString(", ") { it.name }} is targeted by a scam."
                else "Link to a family member's phone to get scam alerts from it.",
                15f, Color.WHITE, top = 6
            ))
        }, lp(top = 14))

        section("1. Link to a phone") { box ->
            box.addView(text("Ask them for the code shown in their Kavach app under Family Guard.", 13f, muted))
            val name = input(box, "Their name (e.g. Maa)", "", InputType.TYPE_CLASS_TEXT)
            val code = input(box, "Family Link code (e.g. ABCD-EFGH-JKMN)", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
            val phone = input(box, "Their phone number (for the Call button)", "", InputType.TYPE_CLASS_PHONE)
            box.addView(primary("Link") {
                val c = Relay.normalize(code.text.toString())
                val n = name.text.toString().trim().ifBlank { "Family member" }
                if (c == null) { toast("That code doesn't look right. It has 12 letters and numbers."); return@primary }
                prefs.links = prefs.links.filter { it.code != c } + Prefs.Link(n, c, phone.text.toString().trim())
                FamilyListenerService.sync(this)
                toast("Linked to $n. You'll get their scam alerts.")
                build()
            }, lp(top = 12))
        }

        if (links.isNotEmpty()) section("Linked phones") { box ->
            links.forEach { l ->
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
                row.addView(text("✅ ${l.name}" + if (l.phone.isNotBlank()) "  ·  ${l.phone}" else "", 16f, ink, bold = true),
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                row.addView(Button(this).apply {
                    text = "Remove"; isAllCaps = false; setTextColor(ink); background = round(page, 10)
                    setOnClickListener {
                        prefs.links = prefs.links.filter { it.code != l.code }
                        FamilyListenerService.sync(this@MainActivity); build()
                    }
                }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(44)))
                box.addView(row)
            }
        }

        section("2. Make sure alerts get through") { box ->
            if (Build.VERSION.SDK_INT >= 33) {
                permRow(box, "Allow Kavach notifications (required)", "Needed to show the alert.",
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == 0) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                }
            }
            permRow(box, "Show alert over apps", "Opens the full red alert even while you use another app.",
                Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            val pm = getSystemService(android.os.PowerManager::class.java)
            permRow(box, "Don't let the phone put Kavach to sleep", "Set battery to Unrestricted so alerts arrive instantly.",
                Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    // Direct system dialog: "Let app always run in background?" -> Allow
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    toast("Tap App battery usage, then the words 'Allow background usage', then Unrestricted.")
                }
            }
            fullScreenRow(box)
            box.addView(secondary("Show a sample alert") {
                startActivity(FamilyAlertActivity.intent(this, "Maa", "",
                    "Maa's phone got a likely digital arrest scam via WhatsApp. Message: \"This is Mumbai Police cyber cell, arrest warrant issued...\""))
            }, lp(top = 8))
        }

        section("Alerts received") { box ->
            val arr = prefs.familyLog()
            if (arr.length() == 0) box.addView(text("No alerts yet.", 14f, muted))
            val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                box.addView(text("🔴 ${fmt.format(Date(o.optLong("t")))} · ${o.optString("name")}\n${o.optString("text")}",
                    13f, ink, top = if (i == 0) 0 else 10))
            }
        }

        content.addView(text("Kavach Guard prototype · Cyber fraud helpline 1930", 12f, muted, top = 16).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
    }

    @Deprecated("Activity result for the call-screening role")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 10 && resultCode != RESULT_OK) toast("Kavach was not set as caller ID & spam app. Try again, or pick it in Settings > Apps > Default apps.")
        build()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val denied = grantResults.isEmpty() || grantResults.any { it != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (denied && requestCode != 2) {
            toast("Permission not given. You can turn it on later in App info > Permissions.")
        } else if (denied) {
            // Sideloaded apps on Android 13+ get SMS silently blocked as a "restricted setting".
            toast("Android blocked this. In App info: ⋮ > Allow restricted settings, then Permissions > SMS > Allow.")
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        build()
    }

    // ---------- helpers ----------

    private fun demo(box: LinearLayout, label: String, from: String, body: String, isCall: Boolean, app: String) {
        box.addView(secondary(label) {
            val v = Guard.handle(this, Guard.Incoming("demo.$app", app, from, body, isCall), fromTester = true)
            if (v.level == Level.SAFE) toast("🟢 Safe (score ${v.score}). No warning shown.")
        }, lp(top = 8))
    }

    private fun listenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(this, GuardListenerService::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
    }

    private fun section(title: String, body: (LinearLayout) -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = round(card, 16)
        }
        box.addView(text(title, 18f, ink, bold = true))
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(inner, lp(top = 10))
        body(inner)
        content.addView(box, lp(top = 14))
    }

    private fun permRow(box: LinearLayout, title: String, sub: String, ok: Boolean, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text((if (ok) "✅ " else "⬜ ") + title, 15f, ink, bold = true))
        col.addView(text(sub, 13f, muted))
        row.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (!ok) row.addView(Button(this).apply {
            text = "Allow"; isAllCaps = false; setTextColor(Color.WHITE); background = round(blue, 10)
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(44)))
        box.addView(row)
    }

    private fun input(box: LinearLayout, hint: String, value: String, type: Int) = EditText(this).apply {
        this.hint = hint; setText(value); inputType = type; textSize = 15f
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = round(page, 10)
        box.addView(this, lp(top = 8))
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        layoutParams = lp(top = top)
    }

    private fun primary(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 16f; setTextColor(Color.WHITE)
        background = round(blue, 12); setOnClickListener { onClick() }
    }

    private fun secondary(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 15f; setTextColor(ink); gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setPadding(dp(14), 0, dp(14), 0)
        background = round(page, 12); setOnClickListener { onClick() }
    }

    private fun round(color: Int, r: Int) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(color) }
    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    @Suppress("unused")
    private fun View.gone() { visibility = View.GONE }
}
