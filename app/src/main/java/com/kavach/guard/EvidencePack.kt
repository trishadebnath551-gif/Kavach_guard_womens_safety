package com.kavach.guard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.provider.CallLog
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One PDF + one copy-ready summary: all harassing numbers, call history, notes and flagged messages together. */
object EvidencePack {
    data class Pack(val file: File, val summary: String, val count: Int)

    private val fmt get() = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)
    private val day get() = SimpleDateFormat("dd MMM", Locale.ENGLISH)
    private fun pretty(c: String) = c.lowercase().replace('_', ' ')
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    fun dir(c: Context) = File(c.cacheDir, "evidence").apply { mkdirs() }

    fun callStats(calls: List<CaseStore.CallRow>): List<String> = calls.groupBy { it.number }.map { (n, rows) ->
        val missed = rows.count { it.type == CallLog.Calls.MISSED_TYPE }
        "$n: ${rows.size} call(s), $missed missed, ${day.format(Date(rows.minOf { it.t }))} to ${day.format(Date(rows.maxOf { it.t }))}"
    }

    fun build(c: Context, items: List<EvidenceStore.Item>, numbers: Set<String>, notes: String, calls: List<CaseStore.CallRow>): Pack {
        dir(c).listFiles()?.forEach { it.delete() }
        val now = Date()
        val stats = callStats(calls)
        val body = StringBuilder()
        if (numbers.isNotEmpty()) {
            body.append("PART A. SAME PERSON, ${numbers.size} NUMBER(S)\n")
            numbers.forEach { body.append("  - $it\n") }
            if (stats.isNotEmpty()) { body.append("\nCalls from these numbers (phone call history):\n"); stats.forEach { body.append("  $it\n") } }
            if (notes.isNotBlank()) body.append("\nVictim's note: $notes\n")
            body.append("\n")
        }
        if (items.isNotEmpty()) {
            body.append("PART B. MESSAGES AND CALLS FLAGGED BY KAVACH\n")
            items.forEachIndexed { i, it ->
                body.append("${i + 1}. ${fmt.format(Date(it.t))} | ${it.source} | ${it.level.lowercase()} risk, ${pretty(it.cat)}\n")
                body.append("   From: ${it.sender.ifBlank { "unknown" }}\n   Message: ${it.text.ifBlank { "(no text)" }}\n\n")
            }
        }
        val bodyHash = sha(body.toString().toByteArray())
        val file = File(dir(c), "Kavach-Complaint-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.ENGLISH).format(now) + ".pdf")
        val doc = PdfDocument()
        val w = Writer(doc)
        w.write("Kavach Complaint Evidence", 20f, true)
        w.write("Prepared on the user's phone: ${fmt.format(now)}\nUser: ${Prefs(c).userName.ifBlank { "Not given" }}\nEvidence text SHA-256: $bodyHash\n", 10f)
        w.write(body.toString(), 11f)
        w.write("Captured automatically from phone notifications and call history; long messages may be shortened. " +
            "File at cybercrime.gov.in or any police station (a Zero FIR can be registered at any station). " +
            "If money was lost, call 1930 immediately.", 9f, color = Color.DKGRAY)
        w.finish()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()

        val top = items.firstOrNull()
        val summary = buildString {
            append("Complaint summary (prepared by Kavach)\n")
            if (numbers.isNotEmpty()) {
                append("The same person has contacted me from ${numbers.size} number(s): ${numbers.joinToString(", ")}\n")
                stats.forEach { append("$it\n") }
                if (notes.isNotBlank()) append("Details: ${notes.take(300)}\n")
            }
            if (top != null) {
                append("Latest flagged item: ${fmt.format(Date(top.t))} via ${top.source}, from ${top.sender.ifBlank { "unknown" }}\n")
                append("Type: ${pretty(top.cat)}. Message: ${top.text.take(250)}\n")
                append("Flagged items in last 7 days: ${items.size}\n")
            }
            append("Evidence file: ${file.name} (SHA-256 ${sha(file.readBytes())})")
        }
        return Pack(file, summary, items.size + numbers.size)
    }

    private class Writer(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f
        var n = 0
        val p = Paint().apply { isAntiAlias = true }
        fun newPage() {
            page?.let { doc.finishPage(it) }
            n++
            val pg = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, n).create())
            page = pg; canvas = pg.canvas; y = 50f
        }
        fun write(text: String, size: Float, bold: Boolean = false, color: Int = Color.BLACK) {
            p.textSize = size; p.isFakeBoldText = bold; p.color = color
            if (page == null) newPage()
            for (para in text.split("\n")) {
                var s = para
                if (s.isEmpty()) { y += size * 1.4f; continue }
                while (s.isNotEmpty()) {
                    val cnt = p.breakText(s, true, 515f, null).coerceAtLeast(1)
                    if (y > 800f) newPage()
                    canvas!!.drawText(s.substring(0, cnt), 40f, y, p)
                    y += size * 1.4f
                    s = s.substring(cnt)
                }
            }
            y += size * 0.4f
        }
        fun finish() { page?.let { doc.finishPage(it) }; page = null }
    }
}
