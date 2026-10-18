package com.kavach.guard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Settings and a short local log of alerts. Message text is trimmed; nothing is uploaded. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("kavach", Context.MODE_PRIVATE)

    var lang: Messages.Lang
        get() = Messages.Lang.from(sp.getString("lang", null)) ?: Messages.Lang.fromDevice()
        set(v) = sp.edit().putString("lang", v.code).apply()

    var userName: String
        get() = sp.getString("user_name", "") ?: ""
        set(v) = sp.edit().putString("user_name", v.trim()).apply()

    var familyPhone: String
        get() = sp.getString("family_phone", "") ?: ""
        set(v) = sp.edit().putString("family_phone", v.trim()).apply()

    var webhookUrl: String
        get() = sp.getString("webhook", "") ?: ""
        set(v) = sp.edit().putString("webhook", v.trim()).apply()

    /** "protect" = this phone is guarded; "family" = this phone receives alerts. */
    var role: String
        get() = sp.getString("role", "protect") ?: "protect"
        set(v) = sp.edit().putString("role", v).apply()

    /** Family member's name, used on the WhatsApp button ("Tell Trisha"). */
    var familyName: String
        get() = sp.getString("family_name", "") ?: ""
        set(v) = sp.edit().putString("family_name", v.trim()).apply()

    /** Protected side: secret Family Link code shared with family. Empty = not created yet. */
    var pairCode: String
        get() = sp.getString("pair_code", "") ?: ""
        set(v) = sp.edit().putString("pair_code", v).apply()

    /** Family side: phones this device listens to. */
    data class Link(val name: String, val code: String, val phone: String)

    var links: List<Link>
        get() = try {
            val a = JSONArray(sp.getString("links", "[]"))
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Link(o.optString("name"), o.optString("code"), o.optString("phone"))
            }
        } catch (e: Exception) { emptyList() }
        set(v) {
            val a = JSONArray()
            v.forEach { a.put(JSONObject().put("name", it.name).put("code", it.code).put("phone", it.phone)) }
            sp.edit().putString("links", a.toString()).apply()
        }

    /** Last relay message id seen per code, so a reconnect picks up missed alerts without repeats. */
    fun lastRelayId(code: String): String = sp.getString("relay_last_$code", "") ?: ""
    fun setLastRelayId(code: String, id: String) = sp.edit().putString("relay_last_$code", id).apply()

    // ---------- Calls ----------
    var blockedNumbers: Set<String>
        get() = sp.getStringSet("blocked", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("blocked", v).apply()

    var trustedNumbers: Set<String>
        get() = sp.getStringSet("trusted", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("trusted", v).apply()

    var blockHighRisk: Boolean
        get() = sp.getBoolean("block_high_risk", false)
        set(v) = sp.edit().putBoolean("block_high_risk", v).apply()

    /** When a scam SMS / WhatsApp was last caught. A call soon after is treated as high risk. */
    var lastScamTextAt: Long
        get() = sp.getLong("last_scam_text", 0L)
        set(v) = sp.edit().putLong("last_scam_text", v).apply()

    /** Records this call and returns how many earlier calls came from the same number in the last hour. */
    fun recordCall(number: String, now: Long): Int {
        val old = try { JSONArray(sp.getString("calls", "[]")) } catch (e: Exception) { JSONArray() }
        val out = JSONArray()
        var earlier = 0
        for (i in 0 until old.length()) {
            val o = old.getJSONObject(i)
            val t = o.optLong("t")
            if (now - t > 60 * 60 * 1000L) continue
            if (o.optString("n") == number) earlier++
            out.put(o)
        }
        out.put(JSONObject().put("n", number).put("t", now))
        sp.edit().putString("calls", out.toString()).apply()
        return earlier
    }

    var guardOn: Boolean
        get() = sp.getBoolean("guard_on", true)
        set(v) = sp.edit().putBoolean("guard_on", v).apply()

    /** Time of the last digital-arrest style text, used to escalate a following video call. */
    var lastArrestHintAt: Long
        get() = sp.getLong("arrest_hint", 0L)
        set(v) = sp.edit().putLong("arrest_hint", v).apply()

    fun lastPingAt(category: Category): Long = sp.getLong("ping_${category.name}", 0L)
    fun markPing(category: Category, t: Long) = sp.edit().putLong("ping_${category.name}", t).apply()

    fun addLog(app: String, v: Verdict, snippet: String) {
        val arr = logArray()
        val o = JSONObject()
            .put("t", System.currentTimeMillis())
            .put("app", app)
            .put("level", v.level.name)
            .put("cat", v.category.name)
            .put("score", v.score)
            .put("text", snippet.take(140))
        val out = JSONArray().put(o)
        for (i in 0 until minOf(arr.length(), 29)) out.put(arr.get(i))
        sp.edit().putString("log", out.toString()).apply()
    }

    fun logArray(): JSONArray = try {
        JSONArray(sp.getString("log", "[]"))
    } catch (e: Exception) {
        JSONArray()
    }

    fun clearLog() = sp.edit().putString("log", "[]").putString("flog", "[]").apply()

    /** Family side: alerts received from linked phones. */
    fun addFamilyLog(name: String, message: String) {
        val old = familyLog()
        val out = JSONArray().put(JSONObject().put("t", System.currentTimeMillis()).put("name", name).put("text", message.take(200)))
        for (i in 0 until minOf(old.length(), 29)) out.put(old.get(i))
        sp.edit().putString("flog", out.toString()).apply()
    }

    fun familyLog(): JSONArray = try { JSONArray(sp.getString("flog", "[]")) } catch (e: Exception) { JSONArray() }
}
