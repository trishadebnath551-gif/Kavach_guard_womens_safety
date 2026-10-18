package com.kavach.guard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog

/**
 * One harassment "case": every number the same person used, plus notes. Kept only on this phone.
 * Combined with the call history and flagged messages into a single complaint by EvidencePack.
 */
object CaseStore {
    data class CallRow(val number: String, val t: Long, val type: Int, val seconds: Int)

    private fun sp(c: Context) = c.getSharedPreferences("kavach_case", Context.MODE_PRIVATE)

    /** Splits "98765 43210, 09876500000\n9876511111" into normalized numbers. Pure, unit-testable. */
    fun splitNumbers(raw: String): List<String> =
        raw.split(Regex("[,;\\n]+")).map { CallRules.normalize(it) }.filter { n -> n.count { it.isDigit() } >= 6 }.distinct()

    fun numbers(c: Context): Set<String> = sp(c).getStringSet("numbers", emptySet())?.toSet() ?: emptySet()
    fun add(c: Context, raw: String): Int {
        val before = numbers(c); val all = before + splitNumbers(raw)
        sp(c).edit().putStringSet("numbers", all).apply(); return all.size - before.size
    }
    fun addAll(c: Context, normalized: Collection<String>) = sp(c).edit().putStringSet("numbers", numbers(c) + normalized).apply()
    fun remove(c: Context, n: String) = sp(c).edit().putStringSet("numbers", numbers(c) - n).apply()
    fun notes(c: Context) = sp(c).getString("notes", "") ?: ""
    fun setNotes(c: Context, s: String) = sp(c).edit().putString("notes", s.take(1000)).apply()
    fun clear(c: Context) = sp(c).edit().clear().apply()

    fun hasCallLogAccess(c: Context) = c.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    private fun query(c: Context, days: Int, where: String? = null): List<CallRow> {
        if (!hasCallLogAccess(c)) return emptyList()
        val since = System.currentTimeMillis() - days * 24L * 3600 * 1000
        return try {
            c.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE, CallLog.Calls.DURATION),
                "${CallLog.Calls.DATE} > ?" + (where?.let { " AND $it" } ?: ""), arrayOf(since.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { cur ->
                val out = ArrayList<CallRow>()
                while (cur.moveToNext() && out.size < 500)
                    out += CallRow(CallRules.normalize(cur.getString(0) ?: ""), cur.getLong(1), cur.getInt(2), cur.getInt(3))
                out
            } ?: emptyList()
        } catch (e: SecurityException) { emptyList() }
    }

    /** Calls (last 90 days) made from the numbers in this case. */
    fun callsFromCase(c: Context): List<CallRow> { val n = numbers(c); return query(c, 90).filter { it.number in n } }

    /** Unsaved numbers that called in the last 30 days (incoming or missed), most frequent first. */
    fun unsavedCallers(c: Context): List<Pair<String, Int>> =
        query(c, 30, "${CallLog.Calls.CACHED_NAME} IS NULL AND ${CallLog.Calls.TYPE} IN (${CallLog.Calls.INCOMING_TYPE},${CallLog.Calls.MISSED_TYPE})")
            .filter { it.number.isNotBlank() }.groupingBy { it.number }.eachCount().toList().sortedByDescending { it.second }.take(25)
}
