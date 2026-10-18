package com.kavach.guard

/**
 * Risk score for an incoming phone call, from what Android tells us before it rings:
 * the number, whether the network verified it, and recent history on this phone.
 * Pure Kotlin so it runs in unit tests.
 */
object CallRules {

    data class Risk(val level: Level, val score: Int, val reasons: List<String>)

    private const val HOUR = 60 * 60 * 1000L

    /** 10-digit Indian number, or "+<digits>" for foreign numbers. */
    fun normalize(raw: String): String {
        val t = raw.trim().replace(" ", "").replace("-", "")
        val d = t.filter { it.isDigit() }
        if (isInternational(t)) return "+" + d.removePrefix("00")
        return when {
            d.length == 12 && d.startsWith("91") -> d.substring(2)
            d.length == 11 && d.startsWith("0") -> d.substring(1)
            else -> d
        }
    }

    fun isInternational(raw: String): Boolean {
        val t = raw.trim().replace(" ", "").replace("-", "")
        return (t.startsWith("+") && !t.startsWith("+91")) || (t.startsWith("00") && !t.startsWith("0091"))
    }

    fun evaluate(
        raw: String,
        now: Long,
        lastScamTextAt: Long,
        earlierCallsLastHour: Int,
        blocked: Set<String>,
        trusted: Set<String>,
        verificationFailed: Boolean,
    ): Risk {
        val n = normalize(raw)
        if (n.isNotEmpty() && n in trusted) return Risk(Level.SAFE, 0, listOf("trusted"))
        if (n.isNotEmpty() && n in blocked) return Risk(Level.SCAM, 100, listOf("reported"))

        var s = 15
        val r = mutableListOf("unknown")
        if (raw.isBlank()) { s += 30; r += "hidden" }
        if (isInternational(raw)) { s += 45; r += "international" }
        if (verificationFailed) { s += 50; r += "spoofed" }
        if (lastScamTextAt > 0 && now - lastScamTextAt in 0 until HOUR) { s += 45; r += "after_scam_text" }
        if (earlierCallsLastHour >= 2) { s += 20; r += "repeat" }

        s = s.coerceAtMost(100)
        val level = when {
            s >= 60 -> Level.SCAM
            s >= 30 -> Level.SUSPICIOUS
            else -> Level.SAFE
        }
        return Risk(level, s, r)
    }

    fun reasonText(reason: String): String? = when (reason) {
        "reported" -> "You reported this number as a scam"
        "hidden" -> "Hidden number"
        "international" -> "International number"
        "spoofed" -> "Network could not verify this number (may be faked)"
        "after_scam_text" -> "Came soon after a scam message"
        "repeat" -> "Called you many times in the last hour"
        "long_call" -> "Call went on for more than 2 minutes"
        else -> null
    }

    fun reasonsText(reasons: List<String>) = reasons.mapNotNull { reasonText(it) }.joinToString(" · ")
}
