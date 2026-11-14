package de.mm20.launcher2.comms.youtube

import de.mm20.launcher2.comms.youtube.flow.YouTubeLink
import de.mm20.launcher2.comms.youtube.flow.parseYouTubeLink
import org.junit.Assert.*
import org.junit.Test

class YouTubeWebLinksTest {
    @Test
    fun musicAndVideoTargetsHaveDisjointHandlers() {
        for (host in YouTubeWebLinks.videoHosts) assertEquals(
            false,
            YouTubeWebLinks.musicTarget("https://$host/watch?v=dQw4w9WgXcQ"),
        )
        assertEquals(true, YouTubeWebLinks.musicTarget("https://MUSIC.YOUTUBE.COM/search?q=song"))
        for (url in
            listOf(
                "https://youtube.com.evil.test/watch",
                "https://evil.youtube.com/watch",
                "https://youtube.com@evil.test/watch",
                "https://user@youtube.com/watch",
                "file://youtube.com/watch",
                "https://youtube.com:8443/watch",
                "javascript:alert(1)",
            )) assertNull(url, YouTubeWebLinks.musicTarget(url))
    }

    @Test
    fun musicSearchDecodesArtistAndTitle() {
        assertEquals(
            YouTubeLink.Search("Μίκης & Song"),
            parseYouTubeLink(
                "https://music.youtube.com/search?q=%CE%9C%CE%AF%CE%BA%CE%B7%CF%82%20%26%20Song"
            ),
        )
    }

    @Test
    fun registrationUsesTheActualUserAndRejectsInvalidInputs() {
        val commands = YouTubeWebLinks.registrationCommands("com.dimitris.telos.release", 10)
        assertTrue(commands.all { it[it.indexOf("--user") + 1] == "10" })
        assertEquals(
            YouTubeWebLinks.allHosts,
            commands.last().takeLast(YouTubeWebLinks.allHosts.size),
        )
        assertTrue(runCatching { YouTubeWebLinks.registrationCommands("bad;package", 0) }.isFailure)
        assertTrue(runCatching { YouTubeWebLinks.registrationCommands("com.telos", -1) }.isFailure)
    }

    @Test
    fun disabledOrUnselectedDomainsNeverReportSuccess() {
        val report =
            "Verification link handling allowed: true\nSelection state:\n  Enabled:\n" +
                YouTubeWebLinks.allHosts.joinToString("\n") +
                "\n  Disabled:\nexample.com"
        assertTrue(YouTubeWebLinks.registrationVerified(report))
        assertFalse(
            YouTubeWebLinks.registrationVerified(report.replace("allowed: true", "allowed: false"))
        )
        assertFalse(
            YouTubeWebLinks.registrationVerified(report.replace("  Enabled:", "  Disabled:"))
        )
        assertFalse(
            YouTubeWebLinks.registrationVerified(report.replace("youtu.be", "lookalike.youtu.be"))
        )
    }
}
