package com.kavach.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScamClassifierTest {

    private fun check(text: String, level: Level, category: Category? = null) {
        val v = ScamClassifier.classify(text)
        assertEquals("level for: $text  -> $v", level, v.level)
        if (category != null) assertEquals("category for: $text -> $v", category, v.category)
    }

    // ---------- Scams ----------
    @Test fun kycSmsWithLink() = check(
        "Dear customer your SBI account will be blocked today. Update KYC immediately: http://sbi-kyc.xyz/upd",
        Level.SCAM, Category.KYC_BLOCK)

    @Test fun electricityCut() = check(
        "Dear consumer your electricity connection will be cut tonight at 9.30 pm. Call 9876543210 immediately",
        Level.SCAM, Category.KYC_BLOCK)

    @Test fun upiPinToReceive() = check(
        "Sir I sent ₹5000 to you by mistake please return. Enter your UPI PIN to receive the refund",
        Level.SCAM, Category.UPI_FRAUD)

    @Test fun hindiLottery() = check(
        "बधाई हो! आपने KBC लॉटरी में 25 लाख जीत लिए हैं। इनाम पाने के लिए 5000 फीस जमा करें",
        Level.SCAM, Category.LOTTERY)

    @Test fun odiaOtpAsk() = check(
        "ମୁଁ ବ୍ୟାଙ୍କରୁ କହୁଛି, ଆପଣଙ୍କ ଆକାଉଣ୍ଟ ବନ୍ଦ ହୋଇଯିବ। OTP କୁହନ୍ତୁ ତୁରନ୍ତ",
        Level.SCAM)

    @Test fun digitalArrest() = check(
        "This is Mumbai Police cyber cell. A parcel in your name has drugs. Arrest warrant issued. Stay on video call, do not tell family",
        Level.SCAM, Category.DIGITAL_ARREST)

    @Test fun hindiDigitalArrest() = check(
        "मैं सीबीआई से बोल रहा हूँ। आपके नाम पर मनी लॉन्ड्रिंग केस है, आपको डिजिटल अरेस्ट किया जाता है",
        Level.SCAM, Category.DIGITAL_ARREST)

    @Test fun hinglishOtp() = check(
        "Bhai bank se call aayega, jo OTP aaye woh mujhe turant bhejo. otp bhej do jaldi",
        Level.SCAM, Category.OTP_THEFT)

    @Test fun taskScam() = check(
        "Part time job! Earn ₹3000 per day by liking YouTube videos. Join telegram task now",
        Level.SCAM, Category.JOB_TASK)

    @Test fun apkLink() {
        val v = ScamClassifier.classify("Your PM Kisan installment is pending. Install app: https://pmkisan-update.top/pmkisan.apk")
        assertTrue("$v", v.level != Level.SAFE)
    }

    // ---------- Should stay safe ----------
    @Test fun realBankOtp() = check(
        "123456 is your OTP for txn of Rs 2,500 at Amazon. Do not share OTP with anyone. -HDFC Bank", Level.SAFE)

    @Test fun normalChat() = check("Maa, I will reach home by 7. Keep dinner ready", Level.SAFE)

    @Test fun deliveryUpdate() = check(
        "Your Amazon order has been shipped and will arrive tomorrow. Track at https://amzn.in/d/abc", Level.SAFE)

    @Test fun salaryCredit() = check(
        "Rs 25,000 credited to your A/c XX1234 on 01-10-2026. Avl bal Rs 31,200 -SBI", Level.SAFE)

    @Test fun edWordInsideOtherWords() = check("I edited the photos and posted them", Level.SAFE)

    // ---------- Helpers ----------
    @Test fun unknownNumber() {
        assertTrue(ScamClassifier.looksLikeUnknownNumber("+91 98111 22333"))
        assertTrue(!ScamClassifier.looksLikeUnknownNumber("Papa"))
    }
}

class MultilingualTest {
    private fun scamOrSuspicious(text: String) {
        val v = ScamClassifier.classify(text)
        org.junit.Assert.assertTrue("$text -> $v", v.level != Level.SAFE)
    }

    @Test fun bengaliLottery() = scamOrSuspicious("অভিনন্দন! আপনি ২৫ লক্ষ টাকার লটারি জিতেছেন। পুরস্কার পেতে এখনই ৫০০০ টাকা ফি দিন")
    @Test fun tamilElectricity() = scamOrSuspicious("உங்கள் மின் இணைப்பு இன்று இரவு துண்டிக்கப்படும். உடனே 9876543210 அழைக்கவும்")
    @Test fun teluguOtp() = scamOrSuspicious("నేను బ్యాంక్ నుండి మాట్లాడుతున్నాను. మీ ఖాతా బ్లాక్ అవుతుంది. వెంటనే OTP చెప్పండి")
    @Test fun marathiArrest() = scamOrSuspicious("मी पोलीस अधिकारी बोलतोय. तुमच्यावर गुन्हा दाखल आहे, तुम्हाला अटक होईल")
    @Test fun gujaratiMistake() = scamOrSuspicious("મેં ભૂલથી તમને 5000 મોકલ્યા છે, તરત પાછા મોકલો")
    @Test fun kannadaOtp() = scamOrSuspicious("ನಿಮ್ಮ ಖಾತೆ ಬ್ಲಾಕ್ ಆಗುತ್ತದೆ, ತಕ್ಷಣ OTP ಹೇಳಿ")
    @Test fun malayalamLottery() = scamOrSuspicious("അഭിനന്ദനങ്ങൾ! നിങ്ങൾക്ക് ലോട്ടറി സമ്മാനം ലഭിച്ചു, ഉടൻ ഫീസ് അടയ്ക്കുക")
    @Test fun punjabiArrest() = scamOrSuspicious("ਮੈਂ ਪੁਲਿਸ ਤੋਂ ਬੋਲ ਰਿਹਾ ਹਾਂ, ਤੁਹਾਨੂੰ ਗ੍ਰਿਫ਼ਤਾਰ ਕੀਤਾ ਜਾਵੇਗਾ")
    @Test fun urduOtp() = scamOrSuspicious("آپ کا اکاؤنٹ بند ہو جائے گا، فوراً OTP بتائیں")
    @Test fun assameseArrest() = scamOrSuspicious("মই আৰক্ষীৰ পৰা কৈছোঁ, আপোনাক গ্ৰেপ্তাৰ কৰা হ'ব")
    @Test fun bengaliNormalChat() = org.junit.Assert.assertEquals(Level.SAFE, ScamClassifier.classify("মা, আমি সন্ধ্যায় বাড়ি ফিরব").level)
    @Test fun allLanguagesHaveWarnings() = org.junit.Assert.assertTrue(Messages.allLangsHaveText())
}

class CallRulesTest {
    private val now = 10_000_000_000L
    private fun eval(n: String, scamText: Long = 0, earlier: Int = 0, blocked: Set<String> = emptySet(),
                     trusted: Set<String> = emptySet(), spoof: Boolean = false) =
        CallRules.evaluate(n, now, scamText, earlier, blocked, trusted, spoof)

    @Test fun plainUnknownIsSafe() = org.junit.Assert.assertEquals(Level.SAFE, eval("+91 98765 43210").level)
    @Test fun internationalIsScam() = org.junit.Assert.assertEquals(Level.SCAM, eval("+92 300 1234567").level)
    @Test fun hiddenIsSuspicious() = org.junit.Assert.assertEquals(Level.SUSPICIOUS, eval("").level)
    @Test fun spoofedIsScam() = org.junit.Assert.assertEquals(Level.SCAM, eval("9876543210", spoof = true).level)
    @Test fun afterScamSmsIsScam() = org.junit.Assert.assertEquals(Level.SCAM, eval("9876543210", scamText = now - 10 * 60_000).level)
    @Test fun oldScamSmsIgnored() = org.junit.Assert.assertEquals(Level.SAFE, eval("9876543210", scamText = now - 3 * 3_600_000).level)
    @Test fun repeatCallerSuspicious() = org.junit.Assert.assertEquals(Level.SUSPICIOUS, eval("9876543210", earlier = 3).level)
    @Test fun reportedBlocked() {
        val r = eval("+91 98765 43210", blocked = setOf("9876543210"))
        org.junit.Assert.assertTrue("reported" in r.reasons)
    }
    @Test fun trustedWins() = org.junit.Assert.assertEquals(Level.SAFE, eval("+92 300 1234567", trusted = setOf("+923001234567")).level)
    @Test fun normalizeForms() {
        org.junit.Assert.assertEquals("9876543210", CallRules.normalize("+91 98765-43210"))
        org.junit.Assert.assertEquals("9876543210", CallRules.normalize("09876543210"))
        org.junit.Assert.assertEquals("+923001234567", CallRules.normalize("+92 300 1234567"))
    }
}

class CallTranscriptTest {
    @Test fun hindiScriptReachesScam() {
        val v = ScamClassifier.classify(ListenActivity.HINDI_SCRIPT.joinToString(" "))
        org.junit.Assert.assertEquals("$v", Level.SCAM, v.level)
    }
    @Test fun englishScriptReachesScam() {
        val v = ScamClassifier.classify(ListenActivity.ENGLISH_SCRIPT.joinToString(" "))
        org.junit.Assert.assertEquals("$v", Level.SCAM, v.level)
    }
    @Test fun hindiFirstLineAloneNotScam() {
        val v = ScamClassifier.classify(ListenActivity.HINDI_SCRIPT[0])
        org.junit.Assert.assertTrue("$v", v.level != Level.SCAM)
    }
}
