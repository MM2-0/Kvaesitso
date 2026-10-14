package dev.intelia.launcher.ui

import org.junit.Assert.*
import org.junit.Test

class InteliaSearchPolicyTest {
    @Test fun shortcutsMustMatchTheirLabelOrParentInsteadOfUnrelatedFuzzyHit() {
        assertTrue(matchesInteliaShortcut("chrome", "New tab", "Chrome"))
        assertTrue(matchesInteliaShortcut("new chrome", "New tab", "Chrome"))
        assertFalse(matchesInteliaShortcut("chrome", "Weekend plans", "Messenger"))
        assertFalse(matchesInteliaShortcut("new train", "New tab", "Chrome"))
    }
    @Test fun accentedQueriesRemainPredictableAndShortQueriesStayEmpty() {
        assertTrue(matchesInteliaShortcut("jizdenka", "Jízdenka", "Můj vlak"))
        assertTrue(matchesInteliaShortcut("MŮJ", "Ticket", "Můj vlak"))
        assertFalse(matchesInteliaShortcut("ab", "About", "App"))
        assertFalse(matchesInteliaShortcut("  ", "Anything", null))
    }
}
