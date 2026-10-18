package com.kavach.guard

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Family Link transport. Alerts travel through a push relay (ntfy.sh for the prototype)
 * on a secret channel derived from a 12-character code. Nobody has to create an account or a server.
 *
 * Production plan: swap BASE for a self-hosted ntfy or Supabase + Firebase push so alert text
 * never passes through a third-party server.
 */
object Relay {
    private const val TAG = "KavachRelay"
    const val BASE = "https://ntfy.sh"

    // No 0/O, 1/I/L to avoid mix-ups when reading the code out over the phone.
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    /** 12 random characters (about 59 bits), shown as ABCD-EFGH-JKMN. */
    fun newCode(): String {
        val r = SecureRandom()
        return (1..12).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
    }

    fun pretty(code: String) = code.chunked(4).joinToString("-")

    /** Accepts what people type: lower case, spaces, dashes. Returns null if it isn't a valid code. */
    fun normalize(input: String): String? {
        val c = input.uppercase().filter { it.isLetterOrDigit() }
        return if (c.length == 12 && c.all { it in ALPHABET }) c else null
    }

    fun topic(code: String) = "kavach_" + code.lowercase()

    /** Fire-and-forget publish. [onDone] gets true when the relay accepted it. */
    fun publish(code: String, title: String, message: String, data: JSONObject, onDone: (Boolean) -> Unit = {}) {
        thread(name = "kavach-relay-send") {
            var ok = false
            try {
                val body = JSONObject()
                    .put("topic", topic(code))
                    .put("title", title)
                    .put("message", message)
                    .put("priority", 5)
                    .put("tags", JSONArray().put("rotating_light").put(data.optString("category", "alert").lowercase()))
                val c = URL(BASE).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 10000
                c.readTimeout = 10000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                ok = c.responseCode in 200..299
                Log.i(TAG, "publish -> ${c.responseCode}")
                c.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "publish failed", e)
            }
            onDone(ok)
        }
    }
}
