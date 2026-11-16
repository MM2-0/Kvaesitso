package de.mm20.launcher2.comms.stremio

import org.junit.Assert.*
import org.junit.Test

class StremioPlaybackSettingsTest {
    @Test fun orderedLanguageFallbacksAreDistinctAndOptional() {
        assertEquals(listOf("el", "en"), StremioPlaybackSettings(audio = "el", audioFallback = "en").audioLanguages)
        assertEquals(listOf("el"), StremioPlaybackSettings(audio = "el", audioFallback = "el").audioLanguages)
        assertTrue(StremioPlaybackSettings(audio = "", audioFallback = "").audioLanguages.isEmpty())
    }
    @Test fun noneSubtitlesCannotBeReenabledBySecondaryLanguage() {
        assertTrue(StremioPlaybackSettings(subtitles = "", subtitleFallback = "en").subtitleLanguages.isEmpty())
        assertEquals(listOf("el"), StremioPlaybackSettings(subtitles = "el", subtitleFallback = "").subtitleLanguages)
    }
    @Test fun cacheBudgetUsesLongBytes() {
        assertEquals(20L * 1024 * 1024 * 1024, StremioPlaybackSettings(cacheGb = 20).cacheBytes)
        assertEquals(listOf(1, 2, 5, 10, 20), StremioPlaybackSettings.cacheChoices)
    }
    @Test fun libraryEpisodeNotificationsDefaultOn() {
        assertTrue(StremioPlaybackSettings().notifyNewEpisodes)
    }
}
