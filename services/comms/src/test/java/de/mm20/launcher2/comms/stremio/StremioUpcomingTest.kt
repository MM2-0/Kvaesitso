package de.mm20.launcher2.comms.stremio

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class StremioUpcomingTest {
    private val now = Instant.parse("2026-03-01T12:00:00Z")

    @Test fun parseInstantAndDateOnly() {
        assertEquals(Instant.parse("2026-04-01T00:00:00Z"), StremioUpcoming.parseReleased("2026-04-01T00:00:00Z"))
        assertEquals(Instant.parse("2026-04-01T00:00:00Z"), StremioUpcoming.parseReleased("2026-04-01"))
        assertNull(StremioUpcoming.parseReleased(null))
        assertNull(StremioUpcoming.parseReleased("tba"))
    }

    @Test fun upcomingIsFutureSorted() {
        val past = Video("p", "Past", released = "2026-02-01T00:00:00Z", season = 1, episode = 1)
        val soon = Video("s", "Soon", released = "2026-04-01T00:00:00Z", season = 1, episode = 2)
        val later = Video("l", "Later", released = "2026-05-01T00:00:00Z", season = 1, episode = 3)
        val none = Video("n", "None", season = 1, episode = 4)
        val ids = StremioUpcoming.upcoming(listOf(later, past, soon, none), now).map { it.video.id }
        assertEquals(listOf("s", "l"), ids)
    }

    @Test fun airedInLastDaysIncludesBoundaryAndExcludesFuture() {
        val old = Video("o", "Old", released = "2026-01-01T00:00:00Z", season = 1, episode = 1)
        val recent = Video("r", "Recent", released = "2026-02-28T00:00:00Z", season = 1, episode = 2)
        val today = Video("t", "Today", released = "2026-03-01T12:00:00Z", season = 1, episode = 3)
        val future = Video("f", "Future", released = "2026-03-02T00:00:00Z", season = 1, episode = 4)
        val ids = StremioUpcoming.airedInLastDays(listOf(old, recent, today, future), 7, now).map { it.video.id }
        assertEquals(listOf("t", "r"), ids)
    }
}
