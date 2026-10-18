package com.kavach.guard

/**
 * On-device, rule-based scam classifier.
 * Pure Kotlin (no Android imports) so it runs in plain JVM unit tests.
 *
 * Covers English, Hinglish (romanised Hindi), Hindi (Devanagari) and Odia script.
 * Each signal adds weight; combinations add a bonus; legit bank OTP wording subtracts.
 * Nothing leaves the phone.
 */

enum class Category {
    DIGITAL_ARREST, UPI_FRAUD, OTP_THEFT, KYC_BLOCK, LOTTERY, JOB_TASK, LOAN, PHISHING_LINK, SCAM_CALL, GENERIC
}

enum class Level { SAFE, SUSPICIOUS, SCAM }

data class Verdict(
    val level: Level,
    val score: Int,
    val category: Category,
    val reasons: List<String>,
)

object ScamClassifier {

    private class Signal(
        val id: String,
        val category: Category?,
        val weight: Int,
        patterns: List<String>,
    ) {
        val regexes: List<Regex> = (patterns + RegionalKeywords.forSignal(id)).map { p ->
            // ASCII patterns get word boundaries; Indic script patterns match as substrings.
            val ascii = p.all { it.code < 128 }
            Regex(if (ascii) "(?<![a-z0-9])(?:$p)(?![a-z0-9])" else p, RegexOption.IGNORE_CASE)
        }

        fun matches(text: String) = regexes.any { it.containsMatchIn(text) }

        /** Base weight for the first hit, half weight for up to two more distinct hits. */
        fun score(text: String): Int {
            val hits = regexes.count { it.containsMatchIn(text) }
            return if (hits == 0) 0 else weight + (minOf(hits, 3) - 1) * (weight / 2)
        }
    }

    // Authority words that digital-arrest scammers impersonate
    private val authority = Signal(
        "authority", null, 15, listOf(
            "cbi", "ed officer", "enforcement directorate", "narcotics", "ncb", "customs",
            "cyber cell", "cyber crime department", "police", "trai", "rbi officer", "income tax officer",
            "mumbai police", "delhi police", "crime branch",
            "सीबीआई", "पुलिस", "कस्टम", "नारकोटिक्स", "क्राइम ब्रांच",
            "ପୋଲିସ", "ସିବିଆଇ", "କଷ୍ଟମ୍ସ",
        )
    )

    private val signals = listOf(
        // ---- Digital arrest ----
        Signal(
            "arrest", Category.DIGITAL_ARREST, 35, listOf(
                "digital arrest", "arrest warrant", "you will be arrested", "case (has been )?registered",
                "fir (has been )?(filed|registered)", "money laundering", "drugs? (found|parcel)",
                "parcel .{0,30}(seized|illegal|drugs|passport)", "aadhaa?r .{0,20}(misuse|used in)",
                "stay on (the )?video call", "do not disconnect", "skype (call|id)",
                "डिजिटल अरेस्ट", "गिरफ्तार", "वारंट", "मनी लॉन्ड्रिंग", "पार्सल में", "पार्सल .{0,40}(ड्रग्स|जब्त|अवैध)", "ड्रग्स मिले",
                "ଡିଜିଟାଲ ଆରେଷ୍ଟ", "ଗିରଫ", "ୱାରେଣ୍ଟ", "ପାର୍ସଲ",
            )
        ),

        // ---- UPI: PIN to receive, collect requests, "sent by mistake" ----
        Signal(
            "upi_pin_receive", Category.UPI_FRAUD, 45, listOf(
                "(enter|put|type) (your )?(upi )?pin to (receive|get|claim)",
                "pin (daal|dalo|dalein|daalo).{0,20}(paise|money|cashback)",
                "scan .{0,15}qr .{0,20}(receive|get|claim)",
                "पैसे पाने के लिए .{0,20}पिन", "पिन डाल",
                "ଟଙ୍କା ପାଇବା ପାଇଁ .{0,20}ପିନ୍",
            )
        ),
        Signal(
            "upi_mistake", Category.UPI_FRAUD, 35, listOf(
                "(sent|transferred|credited) (to you )?by mistake", "wrongly (sent|transferred|credited)",
                "please (return|refund|send back)", "galti se (bhej|aa|chal)",
                "गलती से", "पैसे वापस", "ଭୁଲରେ", "ଫେରାଇ ଦିଅନ୍ତୁ",
            )
        ),
        Signal(
            "upi_collect", Category.UPI_FRAUD, 25, listOf(
                "collect request", "is requesting (rs\\.?|₹|inr)", "requested (rs\\.?|₹|inr) ?[0-9]",
                "payment request", "approve (the )?request",
            )
        ),

        // ---- OTP theft ----
        Signal(
            "otp_ask", Category.OTP_THEFT, 40, listOf(
                "(?<!(not|never|n't) )(share|tell|send|forward|give) (me |us )?(the |your )?otp",
                "otp (bata|bhej|batao|bhejo|batayein|de do)", "read (out )?the otp",
                "ओटीपी (बताएं|बताओ|भेजें|भेजो|दें)", "OTP (बताएं|बताओ|भेजें|भेजो)",
                "ଓଟିପି (କୁହନ୍ତୁ|ପଠାନ୍ତୁ)", "OTP (କୁହନ୍ତୁ|ପଠାନ୍ତୁ)",
            )
        ),

        // ---- KYC / account / SIM / electricity block ----
        Signal(
            "block", Category.KYC_BLOCK, 30, listOf(
                "kyc", "pan (card )?(update|link|expired)",
                "(account|a/c|card|sim|wallet) .{0,25}(blocked|suspended|deactivated|closed|freeze|frozen)",
                "(electricity|power|bijli) .{0,30}(disconnect|cut|cutoff|cut off)",
                "बिजली .{0,20}(कट|काट)", "खाता .{0,15}(बंद|ब्लॉक)", "केवाईसी", "सिम .{0,15}बंद",
                "ବିଦ୍ୟୁତ .{0,20}(କଟ|ବିଚ୍ଛିନ୍ନ)", "ଆକାଉଣ୍ଟ .{0,15}(ବନ୍ଦ|ବ୍ଲକ)", "କେୱାଇସି",
            )
        ),

        // ---- Lottery / prize ----
        Signal(
            "lottery", Category.LOTTERY, 35, listOf(
                "lottery", "lucky draw", "you (have )?won", "kbc", "jackpot",
                "claim your (prize|reward|gift)", "congratulations.{0,40}(won|selected|winner)",
                "लॉटरी", "इनाम", "जीत (लिया|लिए|गए|गये)", "ଲଟେରୀ", "ପୁରସ୍କାର", "ଜିତିଛନ୍ତି",
            )
        ),

        // ---- Job / task scam ----
        Signal(
            "job", Category.JOB_TASK, 30, listOf(
                "part[ -]?time job", "work from home", "earn (rs\\.?|₹) ?[0-9,]+ (per|daily|a) ?(day|hour)",
                "like (youtube )?videos", "telegram task", "prepaid task", "daily income",
                "rate (hotels|products)", "ghar baithe", "घर बैठे", "ଘରେ ବସି",
            )
        ),

        // ---- Loan app ----
        Signal(
            "loan", Category.LOAN, 25, listOf(
                "instant loan", "pre[- ]?approved loan", "loan (is )?approved", "loan .{0,20}processing fee",
                "लोन (मंजूर|स्वीकृत)", "ଋଣ ମଞ୍ଜୁର",
            )
        ),

        // ---- Links ----
        Signal(
            "short_link", Category.PHISHING_LINK, 25, listOf(
                "bit\\.ly/", "tinyurl\\.com/", "cutt\\.ly/", "is\\.gd/", "rb\\.gy/", "t\\.ly/", "shorturl\\.at/",
                "tiny\\.cc/", "ow\\.ly/",
            )
        ),
        Signal(
            "bad_link", Category.PHISHING_LINK, 35, listOf(
                "https?://[^\\s]*\\.apk", "\\.apk(?![a-z])",
                "https?://[^\\s/]+\\.(xyz|top|click|online|site|live|icu|buzz|rest|cfd|sbs)(/|\\s|$)",
                "https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}",
                "https?://[^\\s/]*(sbi|hdfc|icici|axis|paytm|phonepe|npci|uidai|incometax|epfo)[^\\s/]*\\.(?!co\\.in|com|in|gov\\.in)[a-z]+",
            )
        ),

        // ---- Pressure + money ask (weak alone, strong in combination) ----
        Signal(
            "urgency", null, 12, listOf(
                "immediately", "urgent(ly)?", "tonight", "within (24|12|2) ?(hours|hrs)", "last (warning|chance|reminder)",
                "today itself", "turant", "abhi (karo|kare|karein)",
                "तुरंत", "आज ही", "अंतिम चेतावनी", "ତୁରନ୍ତ", "ଶେଷ ଚେତାବନୀ",
            )
        ),
        Signal(
            "pay_ask", null, 10, listOf(
                "pay (rs\\.?|₹)", "(processing|registration|clearance|verification) fee", "transfer (the )?(amount|money)",
                "security deposit", "send (rs\\.?|₹)", "फीस", "जमा करें", "ଫି ଦିଅନ୍ତୁ",
            )
        ),
        Signal(
            "secrecy", null, 15, listOf(
                "do not tell (anyone|your family)", "keep (this|it) (secret|confidential)", "don'?t inform",
                "किसी को (मत|न) बताएं", "କାହାକୁ କୁହନ୍ତୁ ନାହିଁ",
            )
        ),
        Signal(
            "call_number", null, 8, listOf(
                "call (on|us|this number|immediately)", "call (on )?\\+?(91)?[6-9][0-9]{9}", "contact .{0,20}[6-9][0-9]{9}",
                "whatsapp .{0,15}[6-9][0-9]{9}",
            )
        ),
    )

    private val urlRegex = Regex("(https?://|www\\.)[^\\s]+", RegexOption.IGNORE_CASE)

    // Genuine bank OTP SMS says "do not share". That must not trigger OTP_THEFT.
    private val legitOtpRegex = Regex(
        "(otp|one time password|ओटीपी).{0,80}(do not share|never share|don'?t share|किसी के साथ साझा न|ସେୟାର କରନ୍ତୁ ନାହିଁ)" +
            "|(do not share|never share).{0,60}(otp|one time password)",
        RegexOption.IGNORE_CASE
    )

    fun classify(raw: String): Verdict {
        val text = raw.replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return Verdict(Level.SAFE, 0, Category.GENERIC, emptyList())

        var score = 0
        val reasons = mutableListOf<String>()
        val catScore = mutableMapOf<Category, Int>()

        for (s in signals) {
            val w = s.score(text)
            if (w > 0) {
                score += w
                reasons += s.id
                s.category?.let { catScore[it] = (catScore[it] ?: 0) + w }
            }
        }

        val hasAuthority = authority.matches(text)
        if (hasAuthority) {
            score += authority.weight
            reasons += authority.id
        }

        val hasLink = urlRegex.containsMatchIn(text)
        if (hasLink) reasons += "has_link"

        // ---- Combination bonuses ----
        val cats = catScore.keys
        if (hasAuthority && Category.DIGITAL_ARREST in cats) {
            score += 25; catScore[Category.DIGITAL_ARREST] = catScore.getValue(Category.DIGITAL_ARREST) + 25
            reasons += "combo_authority_arrest"
        }
        if (hasLink && (Category.KYC_BLOCK in cats || Category.LOTTERY in cats || Category.LOAN in cats || Category.JOB_TASK in cats)) {
            score += 20; reasons += "combo_link_bait"
        }
        if ("pay_ask" in reasons && (Category.LOTTERY in cats || Category.JOB_TASK in cats || Category.LOAN in cats)) {
            score += 15; reasons += "combo_fee_bait"
        }
        if ("urgency" in reasons && cats.isNotEmpty()) {
            score += 10; reasons += "combo_urgency"
        }

        // ---- Legit OTP message dampener ----
        if (legitOtpRegex.containsMatchIn(text) && "otp_ask" !in reasons && !hasLink) {
            score -= 40
            reasons += "legit_otp_wording"
        }

        score = score.coerceIn(0, 100)
        // A specific scam type (KYC, UPI, arrest...) explains the message better than "has a bad link".
        val category = catScore.filterKeys { it != Category.PHISHING_LINK }.maxByOrNull { it.value }?.key
            ?: if (Category.PHISHING_LINK in catScore) Category.PHISHING_LINK else Category.GENERIC

        val level = when {
            score >= 60 -> Level.SCAM
            score >= 30 -> Level.SUSPICIOUS
            else -> Level.SAFE
        }
        return Verdict(level, score, category, reasons)
    }

    /** Looks like a bare phone number rather than a saved contact name. */
    fun looksLikeUnknownNumber(title: String): Boolean =
        Regex("^\\+?[0-9][0-9 ()-]{8,}$").matches(title.trim())
}
