package com.kavach.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyTest {
    @Test fun kokborokFallsBackToBengaliWarnings() =
        assertEquals(Messages.pack(Messages.Lang.BN).scam, Messages.pack(Messages.Lang.TRP).scam)

    @Test fun kokborokIsListedAndSelectable() = assertTrue(Messages.Lang.from("trp") == Messages.Lang.TRP)

    @Test fun manyNumbersBecomeOneCase() {
        val n = CaseStore.splitNumbers("98765 43210, 09876500000\n9876511111; 123")
        assertEquals(listOf("9876543210", "9876500000", "9876511111"), n)
    }

    @Test fun duplicatesAreMerged() = assertEquals(1, CaseStore.splitNumbers("98765 43210, 9876543210").size)

    @Test fun bengaliAndEnglishHaveSameSafetyKeys() =
        S.keys().forEach { assertTrue("missing bn: $it", S.bengali(it).isNotEmpty()) }
}
