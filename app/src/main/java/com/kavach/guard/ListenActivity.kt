package com.kavach.guard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/**
 * Live call transcript with a rising scam meter.
 * Android does not let any third-party app hear a call on the same phone, so this listens through
 * the microphone: run it on a second phone next to a call on speaker, or use the scripted demo.
 */
class ListenActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var langTag = "hi-IN"
    private val finalText = StringBuilder()
    private var partial = ""
    private var alerted = false

    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var meter: ProgressBar
    private lateinit var verdictLabel: TextView
    private lateinit var transcript: TextView
    private lateinit var micBtn: Button

    private val ink = Color.parseColor("#14161A")
    private val muted = Color.parseColor("#5B6270")
    private val blue = Color.parseColor("#1E5BD8")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        langTag = Prefs(this).lang.code + "-IN"
        window.statusBarColor = blue
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(24))
            setBackgroundColor(Color.parseColor("#F2F4F8"))
        }
        root.addView(text("🎙️ Live call guard", 24f, ink, true))
        root.addView(text("Android does not let any app hear calls on this same phone. Put the call on speaker and run this on a second phone placed next to it, or play the demo.", 13f, muted, top = 6))
        status = text("Ready", 14f, muted, top = 12)
        root.addView(status)
        meter = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0 }
        root.addView(meter, lp(top = 8).apply { height = dp(14) })
        verdictLabel = text("🟢 No scam signs yet", 18f, ink, true, top = 8)
        root.addView(verdictLabel)

        micBtn = btn("🎙️ Start listening", blue, Color.WHITE) { if (listening) stopListening() else startListening() }
        root.addView(micBtn, lp(top = 14))
        root.addView(btn("▶️ Play demo scam call (Hindi)", Color.WHITE, ink) { playScript(HINDI_SCRIPT) }, lp(top = 8))
        root.addView(btn("▶️ Play demo scam call (English)", Color.WHITE, ink) { playScript(ENGLISH_SCRIPT) }, lp(top = 8))
        root.addView(btn("Clear", Color.WHITE, ink) { reset() }, lp(top = 8))

        root.addView(text("Transcript", 16f, ink, true, top = 16))
        transcript = text("", 16f, ink, top = 6).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.WHITE) }
            minHeight = dp(160)
        }
        root.addView(transcript)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    // ---------- microphone ----------

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 5); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Speech recognition is not available. Install or update the Google app."; return
        }
        listening = true
        micBtn.text = "⏹️ Stop listening"
        if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply { setRecognitionListener(listener) }
        listenOnce()
    }

    private fun listenOnce() {
        if (!listening) return
        status.text = "Listening ($langTag)…"
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        try { recognizer?.startListening(i) } catch (_: Exception) { }
    }

    private fun stopListening() {
        listening = false
        micBtn.text = "🎙️ Start listening"
        status.text = "Stopped"
        try { recognizer?.cancel() } catch (_: Exception) { }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            update()
        }

        override fun onResults(results: Bundle?) {
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            partial = ""
            if (t.isNotBlank()) finalText.append(t).append("\n")
            update()
            main.postDelayed({ listenOnce() }, 200)
        }

        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { stopListening(); return }
            // Language not supported on this phone: fall back to Hindi.
            if (error == 12 /* ERROR_LANGUAGE_NOT_SUPPORTED */ && langTag != "hi-IN") langTag = "hi-IN"
            main.postDelayed({ listenOnce() }, 300)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
        else status.text = "Microphone permission is needed for Listen mode."
    }

    // ---------- scripted demo ----------

    private fun playScript(lines: List<String>) {
        stopListening(); reset()
        status.text = "Demo: scammer is talking…"
        lines.forEachIndexed { idx, line ->
            main.postDelayed({
                finalText.append("🗣️ ").append(line).append("\n")
                update()
            }, 1500L + idx * 2500L)
        }
    }

    // ---------- scoring ----------

    private fun update() {
        transcript.text = finalText.toString() + if (partial.isNotBlank()) "… $partial" else ""
        val v = ScamClassifier.classify(finalText.toString() + " " + partial)
        meter.progress = v.score
        verdictLabel.text = when (v.level) {
            Level.SCAM -> "🔴 Scam call: ${Messages.categoryName(v.category)} (${v.score}/100)"
            Level.SUSPICIOUS -> "🟠 Suspicious (${v.score}/100)"
            Level.SAFE -> "🟢 No scam signs yet (${v.score}/100)"
        }
        verdictLabel.setTextColor(when (v.level) {
            Level.SCAM -> Color.parseColor("#B3121D"); Level.SUSPICIOUS -> Color.parseColor("#B86E00"); Level.SAFE -> ink
        })
        if (v.level == Level.SCAM && !alerted) {
            alerted = true
            stopListening()
            status.text = "Scam detected. Alert shown and family told."
            val snippet = finalText.toString().replace("🗣️ ", "").replace("\n", " ").trim()
            startActivity(AlertActivity.intent(this, v, "Phone call", snippet.take(200)))
            VoiceAlert.speakVerdict(this, v, Prefs(this).lang)
            FamilyPing.maybePing(this, v, "a phone call", snippet)
        }
    }

    private fun reset() {
        main.removeCallbacksAndMessages(null)
        finalText.clear(); partial = ""; alerted = false
        transcript.text = ""; meter.progress = 0
        verdictLabel.text = "🟢 No scam signs yet"; verdictLabel.setTextColor(ink)
        status.text = "Ready"
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        try { recognizer?.destroy() } catch (_: Exception) { }
        super.onDestroy()
    }

    // ---------- ui helpers ----------
    private fun text(t: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        layoutParams = lp(top)
    }

    private fun btn(t: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 16f; setTextColor(fg)
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(bg) }
        setOnClickListener { onClick() }
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        val HINDI_SCRIPT = listOf(
            "नमस्ते, मैं मुंबई पुलिस साइबर क्राइम ब्रांच से इंस्पेक्टर शर्मा बोल रहा हूँ।",
            "आपके आधार कार्ड से एक पार्सल बुक हुआ है जिसमें ड्रग्स मिले हैं।",
            "आपके नाम पर मनी लॉन्ड्रिंग का केस दर्ज है, गिरफ्तारी का वारंट जारी हुआ है।",
            "वीडियो कॉल पर बने रहिए, किसी को मत बताइए।",
            "जांच के लिए अपने खाते के पैसे इस खाते में तुरंत ट्रांसफर करें।",
        )
        val ENGLISH_SCRIPT = listOf(
            "Hello, I am calling from the Mumbai Police cyber cell.",
            "A parcel booked with your Aadhaar has drugs in it.",
            "A money laundering case is registered against you and an arrest warrant is issued.",
            "Stay on the video call and do not tell your family.",
            "Transfer the money in your account immediately for verification.",
        )
    }
}
