package com.kavach.guard

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast

/**
 * Speak instead of typing. Uses the phone's own speech dialog (no extra permission). Android has no Kokborok
 * recognizer, so Kokborok users speak Bengali or Hindi here; "help" words start SOS straight away.
 */
class VoiceActivity : Activity() {
    private val sosWords = listOf("help", "sos", "bachao", "bachaao", "বাঁচাও", "বাঁচান", "সাহায্য", "बचाओ", "मदद", "सहायता", "emergency", "জরুরি", "आपातकाल")
    private val callWords = listOf("call", "phone", "threat", "harass", "disturb", "কল", "ফোন", "হুমকি", "উত্যক্ত", "कॉल", "फोन", "धमकी", "परेशान")
    private val reportWords = listOf("report", "complaint", "police", "cyber", "রিপোর্ট", "অভিযোগ", "পুলিশ", "रिपोर्ट", "शिकायत", "पुलिस")

    override fun onCreate(b: android.os.Bundle?) {
        super.onCreate(b)
        val lang = Prefs(this).lang
        val tag = when (lang) { Messages.Lang.TRP, Messages.Lang.AS -> "bn-IN"; else -> lang.code + "-IN" }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, S.t(this@VoiceActivity, "speak", lang))
        }
        try { startActivityForResult(i, 1) } catch (e: Exception) {
            Toast.makeText(this, "Speech input not available on this phone", Toast.LENGTH_LONG).show(); finish()
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val said = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.joinToString(" ")?.lowercase().orEmpty()
        val next = when {
            res != RESULT_OK || said.isBlank() -> null
            sosWords.any { it in said } -> SosActivity::class.java
            callWords.any { it in said } -> HarassmentActivity::class.java
            reportWords.any { it in said } -> ReportActivity::class.java
            else -> null
        }
        if (next != null) startActivity(Intent(this, next))
        else Toast.makeText(this, S.t(this, "voice_unknown", Prefs(this).lang), Toast.LENGTH_LONG).show()
        finish()
    }
}
