package de.mm20.launcher2.comms.stremio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioStreamTargetsTest {
    @Test
    fun webUsesHttpsForViewAndDownload() {
        val s = StreamItem(addonName = "a", url = "https://cdn.example/file.mkv")
        assertEquals("https://cdn.example/file.mkv", StremioStreamTargets.externalViewUri(s, "Title"))
        assertEquals("https://cdn.example/file.mkv", StremioStreamTargets.downloadUrl(s, "Title"))
    }

    @Test
    fun torrentUsesMagnetNotHttpPlaceholder() {
        val s = StreamItem(addonName = "a", infoHash = "0123456789abcdef0123456789abcdef01234567")
        val magnet = StremioStreamTargets.externalViewUri(s, "Show")!!
        assertTrue(magnet.startsWith("magnet:?xt=urn:btih:"))
        assertEquals(magnet, StremioStreamTargets.downloadUrl(s, "Show"))
    }

    @Test
    fun youtubeAndExternalAreNotDownloadOrExternalPlayerTargets() {
        val yt = StreamItem(addonName = "a", ytId = "abc")
        val ext = StreamItem(addonName = "a", externalUrl = "https://example.com/watch")
        assertNull(StremioStreamTargets.externalViewUri(yt, "t"))
        assertNull(StremioStreamTargets.downloadUrl(yt, "t"))
        assertNull(StremioStreamTargets.externalViewUri(ext, "t"))
        assertNull(StremioStreamTargets.downloadUrl(ext, "t"))
    }

    @Test
    fun profileSanitizeRejectsAnonymousAndColons() {
        assertNull(StremioLocalProfiles.sanitize("anonymous"))
        assertNull(StremioLocalProfiles.sanitize("a:b"))
        assertNull(StremioLocalProfiles.sanitize("  "))
        assertEquals("Kids", StremioLocalProfiles.sanitize(" Kids "))
        assertEquals("profile:kids", StremioLocalProfiles.accountKey("Kids"))
        assertNull(StremioLocalProfiles.accountKey(null))
    }
}
