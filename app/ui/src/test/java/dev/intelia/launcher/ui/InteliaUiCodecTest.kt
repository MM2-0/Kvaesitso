package dev.intelia.launcher.ui

import org.junit.Assert.*
import org.junit.Test

class InteliaUiCodecTest {
    @Test fun appearanceRoundTripsAndOldSettingsUseSystemIcons() {
        val state = InteliaUiState(icons = 3, iconPack = "sample.pack", iconOverrides = mapOf("app://train:Main" to 2), palette = 3, typeface = 1, showDate = false)
        assertEquals(state, InteliaUiCodec.decode(InteliaUiCodec.encode(state)))
        assertEquals(0, InteliaUiCodec.decode("{}").icons)
        assertTrue(InteliaUiCodec.decode("{}").showDate)
    }
    @Test fun rejectsUnboundedOrInvalidAppearanceBeforeRestore() {
        for (state in listOf(InteliaUiState(icons = 4), InteliaUiState(palette = -1), InteliaUiState(typeface = 2),
            InteliaUiState(iconPack = "bad/pack"), InteliaUiState(iconOverrides = mapOf("intent://other" to 1)),
            InteliaUiState(iconOverrides = mapOf("app://train:Main" to 4)))) {
            try { InteliaUiCodec.decode(InteliaUiCodec.encode(state)); fail("Invalid appearance accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun spacesReachAndOptInRoundTripWhileOldBackupsStayDisabled() {
        val state = InteliaUiState(spaces = mapOf("app://train:Main" to 3), hand = 2, island = true)
        assertEquals(state, InteliaUiCodec.decode(InteliaUiCodec.encode(state)))
        assertFalse(InteliaUiCodec.decode("{}").island)
        for (state in listOf(state.copy(hand = 3), state.copy(spaces = mapOf("a" to 5)), state.copy(spaces = mapOf("" to 1)))) {
            try { InteliaUiCodec.decode(InteliaUiCodec.encode(state)); fail("Invalid extension accepted") } catch (_: IllegalArgumentException) { }
        }
    }
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
