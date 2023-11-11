package de.mm20.launcher2.openstreetmaps

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.util.Log
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.LocationCategory
import de.mm20.launcher2.search.OpeningTime
import de.mm20.launcher2.search.SearchableSerializer
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

internal data class OsmLocation(
    val id: Long,
    override var label: String,
    var category: LocationCategory?,
    override val latitude: Double,
    override val longitude: Double,
    var street: String?,
    var houseNumber: String?,
    var openingHours: List<OpeningTime>?,
    var websiteUrl: String?,
    override val labelOverride: String? = null,
) : Location {

    override val domain: String = DOMAIN
    override val key: String = "$domain://$id"

    override fun overrideLabel(label: String): OsmLocation {
        return this.copy(labelOverride = label)
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:$latitude,$longitude?q=${Uri.encode(label)}")
            ),
            options
        )
    }

    override suspend fun getCategory(): LocationCategory? {
        if (category == null)
            updateCache()
        return category
    }

    override suspend fun getStreet(): String? {
        if (street == null)
            updateCache()
        return street
    }

    override suspend fun getHouseNumber(): String? {
        if (houseNumber == null)
            updateCache()
        return houseNumber
    }

    override suspend fun getOpeningHours(): List<OpeningTime>? {
        if (openingHours == null)
            updateCache()
        return openingHours
    }

    override suspend fun getWebsiteUrl(): String? {
        if (websiteUrl == null)
            updateCache()
        return websiteUrl
    }

    override fun getSerializer(): SearchableSerializer {
        return OsmLocationSerializer()
    }

    private suspend fun updateCache() {
        val upToDateEntry = idRepository.searchForId(id) ?: return

        label = upToDateEntry.label
        category = upToDateEntry.category
        street = upToDateEntry.street
        houseNumber = upToDateEntry.houseNumber
        openingHours = upToDateEntry.openingHours
        websiteUrl = upToDateEntry.websiteUrl
    }

    companion object : KoinComponent {

        const val DOMAIN = "OpenStreetMaps"

        private val dataStore: LauncherDataStore by inject()
        private val idRepository =
            BaseOsmRepository(dataStore.data.map { it.locationsSearch.customOverpassUrl })

        fun fromOverpassResponse(
            result: OverpassResponse
        ): List<OsmLocation> = result.elements.mapNotNull {
            OsmLocation(
                id = it.id,
                label = it.tags["name"] ?: it.tags["brand"] ?: return@mapNotNull null,
                category = try {
                    it.tags["amenity"]?.let { LocationCategory.valueOf(it.uppercase(Locale.ROOT)) }
                } catch (_: Exception) {
                    LocationCategory.OTHER
                },
                latitude = it.lat,
                longitude = it.lon,
                street = it.tags["addr:street"],
                houseNumber = it.tags["addr:housenumber"],
                openingHours = it.tags["opening_hours"]?.let { ot -> parseOpeningTimes(ot) },
                websiteUrl = it.tags["website"],
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

internal fun parseOpeningTimes(it: String?): List<OpeningTime>? {
    if (it.isNullOrBlank()) return null

    val openingTimes = mutableListOf<OpeningTime>()

    // e.g.
    // "Mo-Sa 11:00-14:00, 17:00-23:00; Su 11:00-23:00"
    // "Mo-Sa 11:00-21:00; PH,Su off"
    // "Mo-Th 10:00-24:00, Fr,Sa 10:00-05:00, PH,Su 12:00-22:00"
    var blocks =
        it.split(',', ';', ' ').mapNotNull { if (it.isBlank()) null else it.trim() }

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

                val daysOfWeek = enumValues<DayOfWeek>().toList()

                if (dowStart.ordinal <= dowEnd.ordinal)
                    daysOfWeek.subList(dowStart.ordinal, dowEnd.ordinal + 1)
                else // "We-Mo"
                    daysOfWeek.subList(dowStart.ordinal, daysOfWeek.size)
                        .union(daysOfWeek.subList(0, dowEnd.ordinal + 1))
            }.union(
                group.filter { singleDayRegex.matches(it) }
                    .mapNotNull { dayOfWeekFromString(it) }
            )

        // if no time specified, treat as "all day" TODO really?
        if (times.isEmpty())
            times = listOf(LocalTime.MIDNIGHT to Duration.ofDays(1))

        // if no day specified, treat as "every day"
        if (days.isEmpty())
            days = enumValues<DayOfWeek>().toSet()

        openingTimes.addAll(days.flatMap { day ->
            times.map { (start, duration) ->
                OpeningTime(
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

    return openingTimes
}
