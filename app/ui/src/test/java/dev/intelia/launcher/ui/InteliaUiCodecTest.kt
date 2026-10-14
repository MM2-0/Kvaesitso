package dev.intelia.launcher.ui

import org.junit.Assert.*
import org.junit.Test

class InteliaUiCodecTest {
    @Test fun themeClockAndMotionRoundTripAcrossBackup() {
        val state = InteliaUiState(theme = 2, showClock = false, reduceMotion = true, wallpaper = 1, arc = listOf("first", "second"))
        assertEquals(state, InteliaUiCodec.decode(InteliaUiCodec.encode(state)))
    }

    @Test fun invalidVersionsAndThemeModesFailBeforeReplacingSettings() {
        for (payload in listOf("{\"version\":2}", "{\"theme\":-1}", "{\"theme\":3}", "{\"theme\":\"light\"}")) {
            try { InteliaUiCodec.decode(payload); fail("Invalid settings accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun currentVersionCanReadAdditionalFieldsWithoutExecutingThem() {
        val decoded = InteliaUiCodec.decode("{\"version\":1,\"theme\":0,\"showClock\":true,\"unknown\":{\"command\":\"ignored\"}}")
        assertEquals(InteliaUiState(theme = 0), decoded)
    }
    @Test fun oldBackupGetsWallpaperAndEmptyArcDefaults() {
        val decoded = InteliaUiCodec.decode("{\"version\":1,\"theme\":1}")
        assertEquals(0, decoded.wallpaper); assertTrue(decoded.arc.isEmpty())
    }
    @Test fun rejectsInvalidWallpaperAndArcWithoutChangingSavedState() {
        for (payload in listOf("{\"wallpaper\":3}", "{\"arc\":[\"a\",\"a\"]}", "{\"arc\":[\"\"]}", "{\"arc\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]}")) {
            try { InteliaUiCodec.decode(payload); fail("Invalid preferences accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
