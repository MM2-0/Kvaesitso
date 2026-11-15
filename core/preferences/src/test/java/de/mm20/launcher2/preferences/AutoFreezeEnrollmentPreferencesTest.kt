package de.mm20.launcher2.preferences

import de.mm20.launcher2.preferences.freeze.withAutoFreezeImports
import org.junit.Assert.*
import org.junit.Test

class AutoFreezeEnrollmentPreferencesTest {
    @Test fun confirmedImportEnablesAtomicallyWithoutOverridingCurrentExclusionsOrMethods() {
        val current = FreezeGroup(freezeCandidates = setOf("com.old.app"),
            freezeNeverFreezeApps = setOf("com.never.app"),
            freezeMethods = mapOf("com.old.app" to FreezeMethod.Disable))
        val next = current.withAutoFreezeImports(setOf("com.new.app", "com.never.app"))
        assertTrue(next.freezeAutoFreezeEnabled)
        assertEquals(setOf("com.old.app", "com.new.app"), next.freezeCandidates)
        assertEquals(current.freezeNeverFreezeApps, next.freezeNeverFreezeApps)
        assertEquals(current.freezeMethods, next.freezeMethods)
        assertFalse(current.freezeAutoFreezeEnabled)
    }
    @Test fun skippingImportEnablesOnlyTheExistingList() {
        val current = FreezeGroup(freezeCandidates = setOf("com.old.app"))
        val next = current.withAutoFreezeImports(emptySet())
        assertTrue(next.freezeAutoFreezeEnabled)
        assertEquals(current.freezeCandidates, next.freezeCandidates)
    }
}
