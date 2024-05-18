package de.mm20.launcher2.locations.providers.openstreetmaps

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.util.Log
import de.mm20.launcher2.ktx.orRunCatching
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.locations.OsmLocationSerializer
import de.mm20.launcher2.search.Departure
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.LocationCategory
import de.mm20.launcher2.search.OpeningHours
import de.mm20.launcher2.search.OpeningSchedule
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.search.UpdatableSearchable
import kotlinx.collections.immutable.toImmutableList
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

internal data class OsmLocation(
    internal val id: Long,
    override val label: String,
    override val category: LocationCategory?,
    override val latitude: Double,
    override val longitude: Double,
    override val street: String?,
    override val houseNumber: String?,
    override val openingSchedule: OpeningSchedule?,
    override val websiteUrl: String?,
    override val phoneNumber: String?,
    override val labelOverride: String? = null,
    override val timestamp: Long,
    override var updatedSelf: (suspend () -> UpdateResult<Location>)? = null,
) : Location, UpdatableSearchable<Location> {

    override val domain: String
        get() = DOMAIN
    override val key: String = "$domain://$id"
    override val fixMeUrl: String
        get() = FIXMEURL

    override val departures: List<Departure>? = null
    override val userRating: Float? = null

    override fun overrideLabel(label: String): OsmLocation {
        return this.copy(labelOverride = label)
    }

    override fun getSerializer(): SearchableSerializer {
        return OsmLocationSerializer()
    }

    companion object {

        internal const val DOMAIN = "osm"
        internal const val FIXMEURL = "https://www.openstreetmap.org/fixthemap"

        private val categoryTags = setOf(
            "amenity",
            "shop",
            "sport",   // "sport:soccer"
            "tourism", // "tourism:museum"
            "leisure", // "leisure:fitness_center"
        )

        fun fromOverpassResponse(
            result: OverpassResponse
        ): List<OsmLocation> = result.elements.mapNotNull {
            it.tags ?: return@mapNotNull null
            OsmLocation(
                id = it.id,
                label = it.tags["name"] ?: it.tags["brand"] ?: return@mapNotNull null,
                category = it.tags.firstNotNullOfOrNull { (tag, value) ->
                    if (tag.lowercase() in categoryTags) {
                        value
                            .split(' ', ',', '.', ';') // in case there are multiple
                            .firstNotNullOfOrNull { value ->
                                runCatching {
                                    LocationCategory.valueOf(value.uppercase(Locale.ROOT))
                                }.orRunCatching {
                                    LocationCategory.valueOf(
                                        "${tag}_${value}".uppercase(
                                            Locale.ROOT
                                        )
                                    )
                                }.getOrNull()
                            }
                    } else null
                } ?: LocationCategory.OTHER,
                latitude = it.lat ?: it.center?.lat ?: return@mapNotNull null,
                longitude = it.lon ?: it.center?.lon ?: return@mapNotNull null,
                street = it.tags["addr:street"],
                houseNumber = it.tags["addr:housenumber"],
                openingSchedule = it.tags["opening_hours"]?.let { ot -> parseOpeningSchedule(ot) },
                websiteUrl = it.tags["website"] ?: it.tags["contact:website"],
                phoneNumber = it.tags["phone"] ?: it.tags["contact:phone"],
                timestamp = System.currentTimeMillis(),
            )
        }
    }
}

// allow for 24:00 to be part of the same day
// https://stackoverflow.com/a/31113244
private val DATE_TIME_FORMATTER =
    DateTimeFormatter.ISO_LOCAL_TIME.withResolverStyle(ResolverStyle.SMART)

private val timeRegex by lazy {
    Regex(
        """^(?:\d{2}:\d{2}-?){2}$""",
        RegexOption.IGNORE_CASE
    )
}
private val singleDayRegex by lazy {
    Regex(
        """^[mtwfsp][ouehra]$""",
        RegexOption.IGNORE_CASE
    )
}
private val dayRangeRegex by lazy {
    Regex(
        """^[mtwfsp][ouehra]-[mtwfsp][ouehra]$""",
        RegexOption.IGNORE_CASE
    )
}

private val daysOfWeek = enumValues<DayOfWeek>().toList().toImmutableList()

private val twentyFourSeven = daysOfWeek.map {
    OpeningHours(
        dayOfWeek = it,
        startTime = LocalTime.MIDNIGHT,
        duration = Duration.ofDays(1)
    )
}.toImmutableList()

// If this is not sufficient, resort to implementing https://wiki.openstreetmap.org/wiki/Key:opening_hours/specification
// or port https://github.com/opening-hours/opening_hours.js
internal fun parseOpeningSchedule(it: String?): OpeningSchedule? {
    if (it.isNullOrBlank()) return null

    val openingHours = mutableListOf<OpeningHours>()

    // e.g.
    // "Mo-Sa 11:00-14:00, 17:00-23:00; Su 11:00-23:00"
    // "Mo-Sa 11:00-21:00; PH,Su off"
    // "Mo-Th 10:00-24:00, Fr,Sa 10:00-05:00, PH,Su 12:00-22:00"
    var blocks =
        it.split(',', ';', ' ').mapNotNull { if (it.isBlank()) null else it.trim() }

    if (blocks.first() == "24/7")
        return OpeningSchedule(
            isTwentyFourSeven = true,
            openingHours = twentyFourSeven
        )

    fun dayOfWeekFromString(it: String): DayOfWeek? = when (it.lowercase()) {
        "mo" -> DayOfWeek.MONDAY
        "tu" -> DayOfWeek.TUESDAY
        "we" -> DayOfWeek.WEDNESDAY
        "th" -> DayOfWeek.THURSDAY
        "fr" -> DayOfWeek.FRIDAY
        "sa" -> DayOfWeek.SATURDAY
        "su" -> DayOfWeek.SUNDAY
        else -> null
    }

    var allDay = false
    var everyDay = false

    fun parseGroup(group: List<String>) {
        if (group.isEmpty())
            return

        var times = group
            .filter { timeRegex.matches(it) }
            .mapNotNull {
                try {
                    val startTime =
                        LocalTime.parse(it.substringBefore('-'), DATE_TIME_FORMATTER)
                    val endTime =
                        LocalTime.parse(it.substringAfter('-'), DATE_TIME_FORMATTER)

                    var duration = Duration.between(startTime, endTime)

                    if (duration.isNegative || duration.isZero)
                        duration += Duration.ofDays(1)

                    startTime to duration
                } catch (dtpe: DateTimeParseException) {
                    Log.e(
                        "OpeningTimeFromOverpassElement",
                        "Failed to parse opening time $it",
                        dtpe
                    )
                    null
                }
            }

        var days = group
            .filter { dayRangeRegex.matches(it) }
            .flatMap {
                val dowStart = dayOfWeekFromString(it.substringBefore('-'))
                    ?: return@flatMap emptyList()
                val dowEnd = dayOfWeekFromString(it.substringAfter('-'))
                    ?: return@flatMap emptyList()

                if (dowStart.ordinal <= dowEnd.ordinal)
                    daysOfWeek.subList(dowStart.ordinal, dowEnd.ordinal + 1)
                else // "We-Mo"
                    daysOfWeek.subList(dowStart.ordinal, daysOfWeek.size)
                        .union(daysOfWeek.subList(0, dowEnd.ordinal + 1))
            }.union(
                group.filter { singleDayRegex.matches(it) }
                    .mapNotNull { dayOfWeekFromString(it) }
            )

        // if no time specified, treat as "all day"
        if (times.isEmpty()) {
            allDay = true
            times = listOf(LocalTime.MIDNIGHT to Duration.ofDays(1))
        }

        // if no day specified, treat as "every day"
        if (days.isEmpty()) {
            everyDay = true
            days = daysOfWeek.toSet()
        }

        openingHours.addAll(days.flatMap { day ->
            times.map { (start, duration) ->
                OpeningHours(
                    dayOfWeek = day,
                    startTime = start,
                    duration = duration
                )
            }
        })
    }

    while (true) {
        if (blocks.isEmpty())
            break

        // assuming that there are blocks that only contain time
        // treating them as "every day of the week"
        if (blocks.size < 2) {
            parseGroup(blocks)
            break
        }

        val nextTimeIndex =
            blocks.indexOfFirst { timeRegex.matches(it) }

        // no time left, so probably no sensible information
        // willingly skips "off" and "closed" as they are not useful
        if (nextTimeIndex == -1)
            break

        // assuming next block to start with the first date coming after a time block
        var nextGroupIndex =
            blocks.subList(nextTimeIndex, blocks.size)
                .indexOfFirst { !timeRegex.matches(it) }

        // no day left, so we are done
        if (nextGroupIndex == -1) {
            parseGroup(blocks)
            break
        }

        // convert index from sublist context
        nextGroupIndex += nextTimeIndex

        parseGroup(blocks.subList(0, nextGroupIndex))
        blocks = blocks.subList(nextGroupIndex, blocks.size)
    }

    return OpeningSchedule(
        isTwentyFourSeven = allDay && everyDay,
        openingHours.toImmutableList()
    )
}
