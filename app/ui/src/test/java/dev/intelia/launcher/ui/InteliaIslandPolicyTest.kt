package dev.intelia.launcher.ui

import org.junit.Assert.*
import org.junit.Test

class InteliaIslandPolicyTest {
    @Test fun overlayRequiresEveryConsentAndVisibleUnlockedScreen() {
        assertTrue(showInteliaIsland(true, true, true, false, false))
        assertFalse(showInteliaIsland(false, true, true, false, false))
        assertFalse(showInteliaIsland(true, false, true, false, false))
        assertFalse(showInteliaIsland(true, true, false, false, false))
        assertFalse(showInteliaIsland(true, true, true, true, false))
        assertFalse(showInteliaIsland(true, true, true, false, true))
    }
    @Test fun accentedNamesShareAnIndexLaneWithoutChangingTheirLabels() {
        assertEquals("S", indexLetter("Škola")); assertEquals("C", indexLetter("Čas")); assertEquals("#", indexLetter("1Password"))
    }
}
