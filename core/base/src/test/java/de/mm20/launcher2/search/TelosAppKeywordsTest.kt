package de.mm20.launcher2.search

import org.junit.Assert.*
import org.junit.Test

class TelosAppKeywordsTest {
    @Test fun everyCatalogFeatureRetainsTelosAndItsEnglishNameAsAliases() {
        val domains = listOf("phone", "messages", "media", "radio", "music", "video", "tv", "youtube", "youtube_music", "viewer", "files", "calculator", "notes", "calendar", "downloads", "network", "voice_recorder", "screen_recorder", "screenshot", "store")
        for (domain in domains) {
            val aliases = TelosAppKeywords.forDomain("telos_${domain}_app").map { it.lowercase() }
            assertTrue(domain, "telos" in aliases)
            assertTrue(domain, domain.replace('_', ' ') in aliases)
            assertTrue(domain, "telos " + domain.replace('_', ' ') in aliases)
        }
    }
    @Test fun greekNamesRemainSearchableWhenDisplayLanguageIsDifferent() {
        assertTrue("τηλέφωνο" in TelosAppKeywords.forDomain("telos_phone_app"))
        assertTrue("μηνύματα" in TelosAppKeywords.forDomain("telos_messages_app"))
        assertTrue("αρχεία" in TelosAppKeywords.forDomain("telos_files_app"))
        assertTrue("μουσική" in TelosAppKeywords.forDomain("telos_music_app"))
    }
    @Test fun tvIsSearchableInEnglishGreekAndGreeklishNotMedia() {
        val tv = TelosAppKeywords.forDomain("telos_tv_app")
        for (name in listOf("tv", "television", "τηλεόραση", "τηλεοραση", "tileorasi", "tileorash", "kanalia"))
            assertTrue(name, name in tv)
        val media = TelosAppKeywords.forDomain("telos_media_app")
        assertFalse("tv" in media)
        assertFalse("τηλεόραση" in media)
        assertFalse("tileorasi" in media)
    }
    @Test fun stremioForkIsSearchableAndDoesNotOpenAsMediaKeyword() {
        val stremio = TelosAppKeywords.forDomain("telos_stremio_app")
        for (name in listOf("stremio", "stremio fork", "addons", "στρέμιο"))
            assertTrue(name, name in stremio)
        val media = TelosAppKeywords.forDomain("telos_media_app")
        assertFalse("stremio" in media)
        assertFalse("stremio fork" in media)
    }
    @Test fun futureVirtualAppsRetainTheTelosAliasWithoutCatalogRegistration() {
        val aliases = TelosAppKeywords.forKey("telos_future_feature_app://home")
        assertTrue("telos" in aliases)
        assertTrue("future feature" in aliases)
        assertTrue(TelosAppKeywords.forDomain("third_party_app").isEmpty())
    }
    @Test fun clockIsSearchableInGreekAndGreeklish() {
        val aliases = TelosAppKeywords.forDomain("telos_clock_app")
        for (name in listOf("clock", "ρολόι", "ρολοι", "roloi", "ξυπνητήρι", "ora"))
            assertTrue(name, name in aliases)
    }
    @Test fun aliasesResolveFromStableSavedKeys() {
        assertEquals(TelosAppKeywords.forDomain("telos_files_app"), TelosAppKeywords.forKey("telos_files_app://files"))
        assertTrue(TelosAppKeywords.forDomain("some_other_app").isEmpty())
    }
}
