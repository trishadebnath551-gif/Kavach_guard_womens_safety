package com.kavach.guard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Full-detail record of every suspicious item, kept ONLY on this phone (30 days, max 50). Never uploaded. */
object EvidenceStore {
    data class Item(val t: Long, val source: String, val sender: String, val text: String, val cat: String, val level: String)

    private fun sp(c: Context) = c.getSharedPreferences("kavach_evidence", Context.MODE_PRIVATE)
    private fun raw(c: Context) = try { JSONArray(sp(c).getString("items", "[]")) } catch (e: Exception) { JSONArray() }

    fun add(c: Context, source: String, sender: String, text: String, v: Verdict) {
        if (v.level == Level.SAFE) return
        val now = System.currentTimeMillis()
        val out = JSONArray().put(JSONObject().put("t", now).put("s", source).put("f", sender)
            .put("x", text.take(500)).put("c", v.category.name).put("l", v.level.name))
        val old = raw(c)
        for (i in 0 until old.length()) {
            val o = old.getJSONObject(i)
            if (out.length() < 50 && now - o.optLong("t") < 30L * 24 * 3600 * 1000) out.put(o)
        }
        sp(c).edit().putString("items", out.toString()).apply()
    }

    /** Newest first. */
    fun recent(c: Context, days: Int): List<Item> {
        val a = raw(c); val limit = System.currentTimeMillis() - days * 24L * 3600 * 1000
        return (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optLong("t") >= limit }.map {
            Item(it.optLong("t"), it.optString("s"), it.optString("f"), it.optString("x"), it.optString("c"), it.optString("l"))
        }
    }
}
