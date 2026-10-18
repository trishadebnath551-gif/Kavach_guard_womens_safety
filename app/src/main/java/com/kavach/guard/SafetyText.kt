package com.kavach.guard

import android.content.Context

/**
 * Strings for the women-safety screens. English, Bengali, Hindi are built in.
 * Kokborok has no machine translation we can trust and Android has no Kokborok voice, so text is loaded from
 * assets/kokborok.properties (key=value, written by native speakers). Missing keys fall back to Bengali.
 */
object S {
    private val en = mapOf(
        "sos_btn" to "🆘 SOS", "sos_title" to "Emergency help", "sos_countdown" to "Sending SOS in", "cancel" to "Cancel",
        "sos_sent" to "SOS sent to your trusted contacts with your live location. Updates go out every 3 minutes.",
        "sos_need_contact" to "Add a trusted contact first (Kavach home screen).", "stop_sos" to "Stop SOS",
        "call112" to "📞 Call 112 (police)", "call1091" to "👩 Women helpline 1091",
        "harass_title" to "Unwanted or threatening calls",
        "harass_hint" to "Add every number the person used. Kavach combines them into ONE complaint.",
        "add" to "Add", "notes_hint" to "What happened? (optional)", "import_log" to "Pick from my call history",
        "report_now" to "🚨 Combine everything and report", "police" to "🚓 Nearest police station",
        "speak" to "🎤 Speak", "voice_unknown" to "I did not understand. Say HELP.",
        "trusted_title" to "Trusted contacts (they get your location in an emergency)", "save" to "Save",
        "file_complaint" to "📝 File complaint (cybercrime.gov.in)", "call1930" to "📞 Money lost? Call 1930",
        "share_pdf" to "📤 Share evidence PDF",
        "evidence_ready" to "✅ Evidence ready: %d item(s) in one PDF.\n✅ Complaint summary copied. Just paste it.",
        "nothing" to "Nothing to report yet. When Kavach flags a call or message, evidence is collected here automatically.",
        "add_to_list" to "Add these numbers to Kavach's block list",
        "disclaimer" to "Kavach prepares your report. You file it. Evidence stays on your phone until you share it.",
        "report_title" to "Report to cyber crime",
    )
    private val bn = mapOf(
        "sos_btn" to "🆘 SOS", "sos_title" to "জরুরি সাহায্য", "sos_countdown" to "SOS পাঠানো হচ্ছে", "cancel" to "বাতিল করুন",
        "sos_sent" to "আপনার অবস্থানসহ SOS বিশ্বস্ত ব্যক্তিদের পাঠানো হয়েছে। প্রতি ৩ মিনিটে নতুন অবস্থান যাবে।",
        "sos_need_contact" to "আগে একজন বিশ্বস্ত ব্যক্তির নম্বর যোগ করুন।", "stop_sos" to "SOS বন্ধ করুন",
        "call112" to "📞 112 এ ফোন করুন (পুলিশ)", "call1091" to "👩 মহিলা হেল্পলাইন 1091",
        "harass_title" to "বিরক্তিকর বা হুমকির ফোন",
        "harass_hint" to "যে নম্বরগুলো থেকে ফোন এসেছে সব যোগ করুন। Kavach সব মিলিয়ে একটাই অভিযোগ তৈরি করবে।",
        "add" to "যোগ করুন", "notes_hint" to "কী হয়েছিল লিখুন (ঐচ্ছিক)", "import_log" to "কল হিস্ট্রি থেকে বেছে নিন",
        "report_now" to "🚨 সব মিলিয়ে সাইবার ক্রাইমে জানান", "police" to "🚓 নিকটতম থানা",
        "speak" to "🎤 বলুন", "voice_unknown" to "বুঝতে পারিনি। 'বাঁচাও' বলুন।",
        "trusted_title" to "বিশ্বস্ত ব্যক্তি (বিপদে আপনার অবস্থান যাবে)", "save" to "সংরক্ষণ",
        "file_complaint" to "📝 অভিযোগ করুন (cybercrime.gov.in)", "call1930" to "📞 টাকা গেলে 1930 এ ফোন করুন",
        "share_pdf" to "📤 প্রমাণের PDF পাঠান",
        "evidence_ready" to "✅ প্রমাণ তৈরি: %d টি ঘটনা একটি PDF-এ।\n✅ অভিযোগের সারাংশ কপি হয়েছে, শুধু পেস্ট করুন।",
        "nothing" to "এখনো কিছু নেই। সন্দেহজনক কল বা মেসেজ এলে প্রমাণ নিজেই জমা হবে।",
        "add_to_list" to "এই নম্বরগুলো Kavach-এর ব্লক তালিকায় যোগ করুন",
        "disclaimer" to "Kavach রিপোর্ট তৈরি করে দেয়, অভিযোগ আপনি নিজে করবেন। প্রমাণ আপনার ফোনেই থাকে।",
        "report_title" to "সাইবার ক্রাইমে জানান",
    )
    private val hi = mapOf(
        "sos_btn" to "🆘 SOS", "sos_title" to "आपातकालीन मदद", "sos_countdown" to "SOS भेजा जा रहा है", "cancel" to "रद्द करें",
        "sos_sent" to "आपकी लोकेशन के साथ SOS भरोसेमंद लोगों को भेज दिया गया। हर 3 मिनट में नई लोकेशन जाएगी।",
        "sos_need_contact" to "पहले एक भरोसेमंद व्यक्ति का नंबर जोड़ें।", "stop_sos" to "SOS बंद करें",
        "call112" to "📞 112 पर कॉल करें (पुलिस)", "call1091" to "👩 महिला हेल्पलाइन 1091",
        "harass_title" to "परेशान करने वाले या धमकी भरे कॉल",
        "harass_hint" to "जिन-जिन नंबरों से कॉल आए, सब जोड़ें। Kavach सबको मिलाकर एक ही शिकायत बनाएगा।",
        "add" to "जोड़ें", "notes_hint" to "क्या हुआ, लिखें (वैकल्पिक)", "import_log" to "कॉल हिस्ट्री से चुनें",
        "report_now" to "🚨 सब मिलाकर साइबर क्राइम में रिपोर्ट करें", "police" to "🚓 नज़दीकी थाना",
        "speak" to "🎤 बोलें", "voice_unknown" to "समझ नहीं आया। 'बचाओ' बोलें।",
        "trusted_title" to "भरोसेमंद लोग (खतरे में आपकी लोकेशन जाएगी)", "save" to "सेव करें",
        "file_complaint" to "📝 शिकायत दर्ज करें (cybercrime.gov.in)", "call1930" to "📞 पैसे गए तो 1930 पर कॉल करें",
        "share_pdf" to "📤 सबूत PDF भेजें",
        "evidence_ready" to "✅ सबूत तैयार: %d घटनाएं एक PDF में।\n✅ शिकायत का सार कॉपी हो गया, बस पेस्ट करें।",
        "nothing" to "अभी कुछ नहीं। संदिग्ध कॉल या मैसेज आने पर सबूत अपने आप जमा होंगे।",
        "add_to_list" to "इन नंबरों को Kavach की ब्लॉक सूची में जोड़ें",
        "disclaimer" to "Kavach रिपोर्ट तैयार करता है, शिकायत आप खुद दर्ज करेंगे। सबूत आपके फोन में ही रहता है।",
        "report_title" to "साइबर क्राइम में रिपोर्ट करें",
    )
    private var trp: Map<String, String>? = null

    private fun trpMap(c: Context): Map<String, String> = trp ?: try {
        c.assets.open("kokborok.properties").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
                .map { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
                .filter { it.second.isNotEmpty() }.toMap()
        }
    } catch (e: Exception) { emptyMap() }.also { trp = it }

    fun t(c: Context, key: String, lang: Messages.Lang): String = when (lang) {
        Messages.Lang.TRP -> trpMap(c)[key] ?: bn[key]
        Messages.Lang.BN, Messages.Lang.AS -> bn[key]
        Messages.Lang.HI, Messages.Lang.MR -> hi[key]
        else -> en[key]
    } ?: en[key] ?: key

    fun keys() = en.keys
    fun english(key: String) = en[key].orEmpty()
    fun bengali(key: String) = bn[key].orEmpty()
}
