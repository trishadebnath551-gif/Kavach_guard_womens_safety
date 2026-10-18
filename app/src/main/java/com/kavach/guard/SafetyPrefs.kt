package com.kavach.guard

import android.content.Context

/** Up to three trusted contacts who get the SOS SMS with the live location. Stored only on this phone. */
object SafetyPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("kavach_safety", Context.MODE_PRIVATE)
    fun contacts(c: Context): List<String> = (sp(c).getString("contacts", "") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
    fun setContacts(c: Context, list: List<String>) =
        sp(c).edit().putString("contacts", list.map { it.filter { ch -> ch.isDigit() || ch == '+' } }.filter { it.length >= 6 }.take(3).joinToString(",")).apply()
}
