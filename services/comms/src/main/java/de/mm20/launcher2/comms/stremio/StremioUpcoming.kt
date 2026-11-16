package de.mm20.launcher2.comms.stremio

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** Catalog episode dates from Meta.videos (`released` ISO-8601). */
object StremioUpcoming {

    data class DatedEpisode(
        val video: Video,
        val released: Instant,
    )

    fun parseReleased(raw: String?): Instant? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        try {
            return Instant.parse(s)
        } catch (_: DateTimeParseException) { }
        try {
            return OffsetDateTime.parse(s).toInstant()
        } catch (_: DateTimeParseException) { }
        val day = s.take(10)
        if (day.length == 10 && day[4] == '-' && day[7] == '-') {
            try {
                return LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant()
            } catch (_: DateTimeParseException) { }
        }
        return null
    }

    fun dated(videos: List<Video>): List<DatedEpisode> =
        videos.mapNotNull { v -> parseReleased(v.released)?.let { DatedEpisode(v, it) } }

    /** Episodes whose catalog `released` is strictly after [now], earliest first. */
    fun upcoming(videos: List<Video>, now: Instant = Instant.now()): List<DatedEpisode> =
        dated(videos).filter { it.released.isAfter(now) }.sortedBy { it.released }

    /** Episodes that aired in the last [days] days, including now, newest first. */
    fun airedInLastDays(videos: List<Video>, days: Int, now: Instant = Instant.now()): List<DatedEpisode> {
        val start = now.minus(days.toLong().coerceAtLeast(0), ChronoUnit.DAYS)
        return dated(videos).filter {
            !it.released.isAfter(now) && !it.released.isBefore(start)
        }.sortedByDescending { it.released }
    }
}
