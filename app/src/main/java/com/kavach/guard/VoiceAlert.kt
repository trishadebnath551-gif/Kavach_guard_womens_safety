package com.kavach.guard

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log

/**
 * Speaks warnings with the phone's built-in Text-to-Speech.
 * If the chosen language voice is missing (Odia and Assamese often are), it walks a fallback chain,
 * and speaks the text in the fallback language so the words always match the voice.
 */
object VoiceAlert {
    private const val TAG = "KavachVoice"
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<Messages.Lang, (Messages.Lang) -> String>? = null


    fun warmUp(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts?.setSpeechRate(0.9f)
                pending?.let { (l, f) -> speakNow(l, f) }
                pending = null
            } else {
                Log.w(TAG, "TTS init failed: $status")
            }
        }
    }

    /** [textFor] builds the sentence in whichever language ends up being spoken. */
    fun speak(context: Context, preferred: Messages.Lang, textFor: (Messages.Lang) -> String) {
        warmUp(context)
        if (!ready) {
            pending = preferred to textFor
            return
        }
        speakNow(preferred, textFor)
    }

    fun speakVerdict(context: Context, v: Verdict, lang: Messages.Lang) =
        speak(context, lang) { l -> Messages.spoken(v, l) }

    private fun speakNow(preferred: Messages.Lang, textFor: (Messages.Lang) -> String) {
        val engine = tts ?: return
        // e.g. Assamese -> Bengali -> Hindi -> English; Tamil -> English
        val order = listOfNotNull(
            preferred, Messages.Lang.from(preferred.ttsFallback), Messages.Lang.HI, Messages.Lang.EN
        ).distinct()
        val lang = order.firstOrNull { l ->
            engine.isLanguageAvailable(l.locale) >= TextToSpeech.LANG_AVAILABLE
        } ?: Messages.Lang.EN
        engine.language = lang.locale
        engine.speak(textFor(lang), TextToSpeech.QUEUE_FLUSH, null, "kavach-${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }
}
