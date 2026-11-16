package de.mm20.launcher2.data.store.installer

import org.junit.Assert.*
import org.junit.Test

class ApkInstallArgumentsTest {
    @Test fun currentProfileAndExplicitStdin() {
        assertEquals(listOf("pm", "install", "-r", "--user", "10", "--skip-enable", "-S", "123", "-"),
            apkInstallArguments(123, 10, true))
        assertEquals(listOf("pm", "install", "-r", "--user", "0", "-d", "-S", "8", "-"),
            apkInstallArguments(8, 0, false, allowDowngrade = true))
    }
    @Test fun legacyDoesNotRequestUnsupportedFlag() {
        assertFalse(apkInstallArguments(123, 0, false).contains("--skip-enable"))
    }
    @Test fun fallbackRequiresSpecificUnsupportedOption() {
        assertTrue(unsupportedPreserveFlag("Error: Unknown option: --skip-enable"))
        assertFalse(unsupportedPreserveFlag("INSTALL_FAILED_UPDATE_INCOMPATIBLE --skip-enable"))
        assertFalse(unsupportedPreserveFlag("Unknown option --other"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownSize() { apkInstallArguments(-1, 0, true) }
}
