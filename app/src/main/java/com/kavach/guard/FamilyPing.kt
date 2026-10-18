package com.kavach.guard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Tells family when a likely scam reaches this phone.
 * 1. Family Link: push to every family phone that entered this phone's code (main route, zero setup)
 * 2. SMS to the family number, only if Android allowed the SMS permission (optional)
 * 3. Webhook (hidden advanced option, for n8n etc.)
 * At most one alert per scam type every 10 minutes.
 */
object FamilyPing {
    private const val TAG = "KavachPing"
    private const val COOLDOWN_MS = 10 * 60 * 1000L

    fun alertText(context: Context, v: Verdict, sourceApp: String, snippet: String, now: Long): String {
        val who = Prefs(context).userName.ifBlank { "Your family member" }
        val time = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(now))
        if ("test" in v.reasons) return "Test alert at $time. Kavach on $who's phone is linked and working. No action needed."
        if (v.category == Category.SCAM_CALL)
            return "$who is on a phone call with a possible scammer since $time: ${snippet.take(120)}. Please call them now."
        return "$who's phone got a likely ${Messages.categoryName(v.category)} scam via $sourceApp at $time. " +
            "Message: \"${snippet.take(90)}\". Please call them now."
    }

    fun maybePing(context: Context, v: Verdict, sourceApp: String, snippet: String, force: Boolean = false): String {
        val prefs = Prefs(context)
        if (v.level != Level.SCAM && !force) return "not sent (only for high-risk)"
        val now = System.currentTimeMillis()
        if (!force && now - prefs.lastPingAt(v.category) < COOLDOWN_MS) return "skipped (cooldown)"

        val who = prefs.userName.ifBlank { "Family member" }
        val body = alertText(context, v, sourceApp, snippet, now)
        val results = mutableListOf<String>()

        // 1. Family Link
        val code = prefs.pairCode
        if (code.isNotBlank()) {
            val data = JSONObject()
                .put("category", v.category.name)
                .put("score", v.score)
                .put("app", sourceApp)
            Relay.publish(code, if ("test" in v.reasons) "✅ Kavach test: $who" else "🚨 Kavach alert: $who", body, data)
            results += "Family Link sent"
        }

        // 2. SMS (optional, often blocked for sideloaded apps)
        val phone = prefs.familyPhone
        if (phone.isNotBlank() && hasSmsPermission(context)) {
            try {
                val sms = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java)
                else @Suppress("DEPRECATION") SmsManager.getDefault()
                sms.sendMultipartTextMessage(phone, null, sms.divideMessage("Kavach alert: $body"), null, null)
                results += "SMS sent"
            } catch (e: Exception) {
                Log.w(TAG, "SMS failed", e)
            }
        }

        // 3. Webhook (advanced)
        val hook = prefs.webhookUrl
        if (hook.startsWith("https://")) {
            val payload = JSONObject()
                .put("event", "kavach_alert")
                .put("user", who)
                .put("family_phone", phone)
                .put("category", v.category.name)
                .put("score", v.score)
                .put("source_app", sourceApp)
                .put("snippet", snippet.take(140))
                .put("message", "Kavach alert: $body")
                .put("ts", now)
            thread(name = "kavach-webhook") {
                try {
                    val c = URL(hook).openConnection() as HttpURLConnection
                    c.requestMethod = "POST"
                    c.connectTimeout = 8000
                    c.readTimeout = 8000
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    c.outputStream.use { it.write(payload.toString().toByteArray()) }
                    Log.i(TAG, "Webhook ${c.responseCode}")
                    c.disconnect()
                } catch (e: Exception) {
                    Log.w(TAG, "Webhook failed", e)
                }
            }
            results += "webhook sent"
        }

        if (results.isEmpty()) return "no family linked yet"
        prefs.markPing(v.category, now)
        return results.joinToString(", ")
    }

    /** Opens WhatsApp to the saved family number with [msg] typed in, or null if no usable number. */
    fun whatsAppIntent(context: Context, msg: String): android.content.Intent? {
        val d = Prefs(context).familyPhone.filter { it.isDigit() }
        val num = when {
            d.length == 10 -> "91$d"
            d.length == 12 && d.startsWith("91") -> d
            else -> return null
        }
        return android.content.Intent(android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("https://wa.me/$num?text=" + android.net.Uri.encode(msg)))
    }

    fun hasSmsPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
}
