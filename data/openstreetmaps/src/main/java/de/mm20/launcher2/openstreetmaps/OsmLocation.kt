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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
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
    internal val id: Long,
    override var label: String,
    internal var category: LocationCategory?,
    private val _latitude: Double?,
    private val _longitude: Double?,
    private var street: String?,
    private var houseNumber: String?,
    private var openingHours: ImmutableList<OpeningTime>?,
    private var websiteUrl: String?,
    private var phoneNumber: String?,
    private var isCacheUpToDate: Boolean,
    override val labelOverride: String? = null,
) : Location {

    override val domain: String
        get() = DOMAIN
    override val key: String = "$domain://$id"
    override val fixMeUrl: String
        get() = FIXMEURL

    override val latitude: Double
        get() = _latitude ?: 0.0
    override val longitude: Double
        get() = _longitude ?: 0.0

    val isWellDefined: Boolean
        get() = _latitude != null && _longitude != null

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
        if (isCacheUpToDate)
            return category
        if (category == null)
            updateCache()
        return category
    }

    override suspend fun getStreet(): String? {
        if (isCacheUpToDate)
            return street
        if (street == null)
            updateCache()
        return street
    }

    override suspend fun getHouseNumber(): String? {
        if (isCacheUpToDate)
            return houseNumber
        if (houseNumber == null)
            updateCache()
        return houseNumber
    }

    override suspend fun getOpeningHours(): ImmutableList<OpeningTime>? {
        if (isCacheUpToDate)
            return openingHours
        if (openingHours == null)
            updateCache()
        return openingHours
    }

    override suspend fun getWebsiteUrl(): String? {
        if (isCacheUpToDate)
            return websiteUrl
        if (websiteUrl == null)
            updateCache()
        return websiteUrl
    }

    override suspend fun getPhoneNumber(): String? {
        if (isCacheUpToDate)
            return phoneNumber
        if (phoneNumber == null)
            updateCache()
        return phoneNumber
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

        isCacheUpToDate = true
    }

    companion object : KoinComponent {

        const val DOMAIN = "OpenStreetMaps"
        const val FIXMEURL = "https://www.openstreetmap.org/fixthemap"

        private val dataStore: LauncherDataStore by inject()
        private val idRepository =
            BaseOsmRepository(dataStore.data.map { it.locationsSearch.customOverpassUrl })

        private val categoryTags = setOf(
            "amenity",
            "shop",
            "sport",   // "sport:soccer"
            "railway", // "railway:stop"
            "highway", // "highway:bus_stop"
            "tourism", // "tourism:museum"
            "leisure", // "leisure:fitness_center"
        )

        fun fromOverpassResponse(
            result: OverpassResponse
        ): List<OsmLocation> = result.elements.mapNotNull {
            OsmLocation(
                id = it.id,
                label = it.tags["name"] ?: it.tags["brand"] ?: return@mapNotNull null,
                category = it.tags.firstNotNullOfOrNull { (tag, value) ->
                    if (tag.lowercase() in categoryTags) {
                        value
                            .split(' ', ',', '.') // in case there are multiple
                            .firstNotNullOfOrNull { value ->
                                runCatching {
                                    LocationCategory.valueOf(value.uppercase(Locale.ROOT))
                                }.getOrElse {
                                    runCatching {
                                        LocationCategory.valueOf(
                                            // e.g. "railway:stop" -> "RAILWAY_STOP"
                                            "${tag}_$value".uppercase(
                                                Locale.ROOT
                                            )
                                        )
                                    }.getOrNull()
                                }
                            }
                    } else null
                } ?: LocationCategory.OTHER,
                _latitude = it.lat ?: it.center?.lat,
                _longitude = it.lon ?: it.center?.lon,
                street = it.tags["addr:street"],
                houseNumber = it.tags["addr:housenumber"],
                openingHours = it.tags["opening_hours"]?.let { ot -> parseOpeningTimes(ot) },
                websiteUrl = it.tags["website"],
                phoneNumber = it.tags["phone"],
                isCacheUpToDate = true,
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

private val twentyFourSeven = enumValues<DayOfWeek>().map {
    OpeningTime(
        dayOfWeek = it,
        startTime = LocalTime.MIDNIGHT,
        duration = Duration.ofDays(1)
    )
}.toImmutableList()

// If this is not sufficient, resort to implementing https://wiki.openstreetmap.org/wiki/Key:opening_hours/specification
// or port https://github.com/opening-hours/opening_hours.js
internal fun parseOpeningTimes(it: String?): ImmutableList<OpeningTime>? {
    if (it.isNullOrBlank()) return null

    val openingTimes = mutableListOf<OpeningTime>()

    // e.g.
    // "Mo-Sa 11:00-14:00, 17:00-23:00; Su 11:00-23:00"
    // "Mo-Sa 11:00-21:00; PH,Su off"
    // "Mo-Th 10:00-24:00, Fr,Sa 10:00-05:00, PH,Su 12:00-22:00"
    var blocks =
        it.split(',', ';', ' ').mapNotNull { if (it.isBlank()) null else it.trim() }

    if (blocks.first() == "24/7")
        return twentyFourSeven

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

    return openingTimes.toImmutableList()
}
