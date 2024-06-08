package de.mm20.launcher2.locations.providers.openstreetmaps

import android.content.Context
import android.util.Log
import de.mm20.launcher2.locations.OsmLocationSerializer
import de.mm20.launcher2.openstreetmaps.R
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.UpdatableSearchable
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.search.location.Address
import de.mm20.launcher2.search.location.Departure
import de.mm20.launcher2.search.location.LocationIcon
import de.mm20.launcher2.search.location.OpeningHours
import de.mm20.launcher2.search.location.OpeningSchedule
import kotlinx.collections.immutable.toImmutableList
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import kotlin.math.min

internal data class OsmLocation(
    internal val id: Long,
    override val label: String,
    override val icon: LocationIcon?,
    override val category: String?,
    override val latitude: Double,
    override val longitude: Double,
    override val address: Address?,
    override val openingSchedule: OpeningSchedule?,
    override val websiteUrl: String?,
    override val phoneNumber: String?,
    override val emailAddress: String? = null,
    override val labelOverride: String? = null,
    override val timestamp: Long,
    override var updatedSelf: (suspend (SavableSearchable) -> UpdateResult<Location>)? = null,
    override val userRating: Float?
) : Location, UpdatableSearchable<Location> {

    override val domain: String
        get() = DOMAIN
    override val key: String = "$domain://$id"
    override val fixMeUrl: String
        get() = FIXMEURL

    override val userRatingCount: Int? = null
    override val departures: List<Departure>? = null

    override fun overrideLabel(label: String): OsmLocation {
        return this.copy(labelOverride = label)
    }

    override fun getSerializer(): SearchableSerializer {
        return OsmLocationSerializer()
    }

    companion object {

        internal const val DOMAIN = "osm"
        internal const val FIXMEURL = "https://www.openstreetmap.org/fixthemap"

        fun fromOverpassResponse(
            result: OverpassResponse,
            context: Context
        ): List<OsmLocation> = result.elements.mapNotNull {
            it.tags ?: return@mapNotNull null
            val (category, icon) = it.tags.categorize(context)
            icon ?: return@mapNotNull null
            OsmLocation(
                id = it.id,
                label = it.tags["name"] ?: it.tags["brand"] ?: return@mapNotNull null,
                icon = icon,
                category = category,
                latitude = it.lat ?: it.center?.lat ?: return@mapNotNull null,
                longitude = it.lon ?: it.center?.lon ?: return@mapNotNull null,
                address = null,
                openingSchedule = it.tags["opening_hours"]?.let { ot -> parseOpeningSchedule(ot) },
                websiteUrl = it.tags["website"] ?: it.tags["contact:website"],
                phoneNumber = it.tags["phone"] ?: it.tags["contact:phone"],
                emailAddress = it.tags["email"] ?: it.tags["contact:email"],
                timestamp = System.currentTimeMillis(),
                userRating = it.tags["stars"]?.runCatching { this.toInt() }?.getOrNull()
                    ?.let { min(it, 5) / 5.0f }
            )
        }
    }
}

private class MatchAnyReceiverScope<T, A, B> {
    private val pairs = mutableMapOf<T, Pair<A, B>>()
    operator fun get(key: T): Pair<A, B>? = pairs[key]
    infix fun T.with(pair: Pair<A, B>) = pairs.put(this, pair)
}

private fun <A, B> Map<String, String>.matchAnyTag(
    key: String,
    block: MatchAnyReceiverScope<String, A, B>.() -> Unit
): Pair<A, B>? {
    val scope = MatchAnyReceiverScope<String, A, B>()
    scope.block()
    return this[key]?.split(' ', ',', '.', ';')?.map { it.trim() }
        ?.firstNotNullOfOrNull { scope[it] }
}

private fun Map<String, String>.categorize(context: Context): Pair<String, LocationIcon?> {
    val category = this.firstNotNullOfOrNull { (tag, value) ->
        val values = value.split(' ', ',', '.', ';').map { it.trim() }.toSet()
        when (tag.lowercase()) {

            "shop" -> values.firstNotNullOfOrNull {
                when (it) {
                    "florist" -> R.string.poi_category_florist to LocationIcon.Florist
                    "kiosk" -> R.string.poi_category_kiosk to LocationIcon.Kiosk
                    "furniture" -> R.string.poi_category_furniture to LocationIcon.FurnitureStore
                    "cell_phones", "mobile_phone" -> R.string.poi_category_mobile_phone to LocationIcon.CellPhoneStore
                    "books" -> R.string.poi_category_books to LocationIcon.BookStore
                    "clothes" -> R.string.poi_category_clothes to LocationIcon.ClothingStore
                    "convenience" -> R.string.poi_category_convenience to LocationIcon.ConvenienceStore
                    "discount" -> R.string.poi_category_discount_store to LocationIcon.DiscountStore
                    "jewelry" -> R.string.poi_category_jewelry to LocationIcon.JewelryStore
                    "alcohol" -> R.string.poi_category_alcohol to LocationIcon.LiquorStore
                    "pet", "pet_grooming" -> R.string.poi_category_pet to LocationIcon.PetStore
                    "mall", "shopping_centre", "department_store" -> R.string.poi_category_mall to LocationIcon.ShoppingMall
                    "supermarket" -> R.string.poi_category_supermarket to LocationIcon.Supermarket
                    "bakery" -> R.string.poi_category_bakery to LocationIcon.Bakery
                    "optician" -> R.string.poi_category_optician to LocationIcon.Optician
                    "hairdresser" -> R.string.poi_category_hairdresser to LocationIcon.HairSalon
                    "laundry" -> R.string.poi_category_laundry to LocationIcon.Laundromat

                    else -> R.string.poi_category_shopping to LocationIcon.Shopping
                }
            }

            "amenity" -> values.firstNotNullOfOrNull {
                when (it) {
                    "place_of_worship" -> matchAnyTag<Int, LocationIcon>("religion") {
                        "christian" with (R.string.poi_category_church to LocationIcon.Church)
                        "muslim" with (R.string.poi_category_mosque to LocationIcon.Mosque)
                        "buddhist" with (R.string.poi_category_buddhist_temple to LocationIcon.BuddhistTemple)
                        "hindu" with (R.string.poi_category_hindu_temple to LocationIcon.HinduTemple)
                        "jewish" with (R.string.poi_category_synagogue to LocationIcon.Synagogue)
                    } ?: (R.string.poi_category_place_of_worship to LocationIcon.Candle)

                    "fast_food" -> R.string.poi_category_fast_food to LocationIcon.FastFood
                    "cafe" -> R.string.poi_category_cafe to LocationIcon.Cafe
                    "ice_cream" -> R.string.poi_category_ice_cream to LocationIcon.IceCream
                    "bar" -> R.string.poi_category_bar to LocationIcon.Bar
                    "pub" -> R.string.poi_category_pub to LocationIcon.Pub
                    "restaurant" -> matchAnyTag<Int, LocationIcon>("cuisine") {
                        "pizza" with (R.string.poi_category_pizza_restaurant to LocationIcon.Pizza)
                        "burger" with (R.string.poi_category_burger_restaurant to LocationIcon.Burger)
                        "chinese" with (R.string.poi_category_chinese_restaurant to LocationIcon.Ramen)
                        "ramen" with (R.string.poi_category_ramen_restaurant to LocationIcon.Ramen)
                        "japanese" with (R.string.poi_category_japanese_restaurant to LocationIcon.JapaneseCuisine)
                        "kebab" with (R.string.poi_category_kebab_restaurant to LocationIcon.Kebab)
                        "asian" with (R.string.poi_category_asian_restaurant to LocationIcon.AsianCuisine)
                        "soup" with (R.string.poi_category_soup_restaurant to LocationIcon.Soup)
                        "coffee_shop" with (R.string.poi_category_cafe to LocationIcon.Cafe)
                        "brunch" with (R.string.poi_category_brunch_restaurant to LocationIcon.Brunch)
                        "breakfast" with (R.string.poi_category_breakfast_restaurant to LocationIcon.Breakfast)
                    } ?: (R.string.poi_category_restaurant to LocationIcon.Restaurant)

                    "fuel" -> R.string.poi_category_fuel to LocationIcon.GasStation
                    "car_rental", "car_sharing" -> R.string.poi_category_car to LocationIcon.CarRental
                    "car_wash" -> R.string.poi_category_car_wash to LocationIcon.CarWash
                    "charging_station" -> R.string.poi_category_charging_station to LocationIcon.ChargingStation
                    "parking", "parking_space", "motorcycle_parking" -> R.string.poi_category_parking to LocationIcon.Parking
                    "motorcycle_rental" -> R.string.poi_category_motorcycle_rental to LocationIcon.Motorcycle

                    "theatre" -> R.string.poi_category_theater to LocationIcon.Theater
                    "cinema" -> R.string.poi_category_cinema to LocationIcon.MovieTheater
                    "nightclub" -> R.string.poi_category_nightclub to LocationIcon.NightClub
                    "concert_hall" -> R.string.poi_category_concert_hall to LocationIcon.ConcertHall
                    "casino" -> R.string.poi_category_casino to LocationIcon.Casino

                    "pharmacy" -> R.string.poi_category_pharmacy to LocationIcon.Pharmacy
                    "bank" -> R.string.poi_category_bank to LocationIcon.Bank
                    "atm" -> R.string.poi_category_atm to LocationIcon.Atm
                    "doctors" -> R.string.poi_category_doctors to LocationIcon.Physician
                    "dentist" -> R.string.poi_category_dentist to LocationIcon.Dentist
                    "hospital" -> R.string.poi_category_hospital to LocationIcon.Hospital
                    "clinic" -> R.string.poi_category_clinic to LocationIcon.Clinic

                    "police" -> R.string.poi_category_police to LocationIcon.Police
                    "fire_station" -> R.string.poi_category_fire_station to LocationIcon.FireDepartment
                    "courthouse" -> R.string.poi_category_courthouse to LocationIcon.Courthouse
                    "post_office" -> R.string.poi_category_post_office to LocationIcon.PostOffice
                    "library" -> R.string.poi_category_library to LocationIcon.Library
                    "school" -> R.string.poi_category_school to LocationIcon.School
                    "university" -> R.string.poi_category_university to LocationIcon.University
                    "toilets" -> R.string.poi_category_toilets to LocationIcon.PublicBathroom

                    else -> null
                }
            }

            "tourism" -> values.firstNotNullOfOrNull {
                when (it) {
                    "gallery" -> R.string.poi_category_gallery to LocationIcon.ArtGallery
                    "museum" -> R.string.poi_category_museum to LocationIcon.Museum
                    "theme_park" -> R.string.poi_category_amusement_park to LocationIcon.AmusementPark
                    "hotel" -> R.string.poi_category_hotel to LocationIcon.Hotel
                    else -> null
                }
            }

            "leisure" -> values.firstNotNullOfOrNull {
                when (it) {
                    "stadium" -> R.string.poi_category_stadium to LocationIcon.Stadium
                    "fitness_centre" -> R.string.poi_category_fitness_center to LocationIcon.FitnessCenter
                    "swimming_pool" -> R.string.poi_category_swimming to LocationIcon.Swimming
                    "pitch", "sports_centre" -> matchAnyTag<Int, LocationIcon>("sport") {
                        "soccer" with (R.string.poi_category_soccer to LocationIcon.Soccer)
                        "tennis" with (R.string.poi_category_tennis to LocationIcon.Tennis)
                        "basketball" with (R.string.poi_category_basketball to LocationIcon.Basketball)
                        "gymnastics" with (R.string.poi_category_gymnastics to LocationIcon.Gymnastics)
                        "martial_arts" with (R.string.poi_category_martial_arts to LocationIcon.MartialArts)
                        "golf" with (R.string.poi_category_golf to LocationIcon.Golf)
                        "ice_hockey" with (R.string.poi_category_ice_hockey to LocationIcon.Hockey)
                        "baseball" with (R.string.poi_category_baseball to LocationIcon.Baseball)
                        "american_football" with (R.string.poi_category_american_football to LocationIcon.AmericanFootball)
                        "handball" with (R.string.poi_category_handball to LocationIcon.Handball)
                        "volleyball" with (R.string.poi_category_volleyball to LocationIcon.Volleyball)
                        "skiing" with (R.string.poi_category_skiing to LocationIcon.Skiing)
                        "cricket" with (R.string.poi_category_cricket to LocationIcon.Cricket)
                    }
                    "park" -> R.string.poi_category_park to LocationIcon.Park
                    else -> null
                }
            }

            "historic" -> values.firstNotNullOfOrNull {
                when (it) {
                    "monument" -> R.string.poi_category_monument to LocationIcon.Monument
                    else -> null
                }
            }

            "building" -> values.firstNotNullOfOrNull {
                when (it) {
                    "government" -> R.string.poi_category_government_building to LocationIcon.GovernmentBuilding
                    else -> null
                }
            }

            else -> null
        }
    }
    val (rid, icon) = category ?: (R.string.poi_category_other to null)
    return context.resources.getString(rid) to icon
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
        return OpeningSchedule.TwentyFourSeven

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
            if (group.any { it.equals("PH", ignoreCase = true) }) {
                times = emptyList()
            } else {
                everyDay = true
                days = daysOfWeek.toSet()
            }
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

    return if (allDay && everyDay) {
        OpeningSchedule.TwentyFourSeven
    } else {
        OpeningSchedule.Hours(openingHours)
    }
}


