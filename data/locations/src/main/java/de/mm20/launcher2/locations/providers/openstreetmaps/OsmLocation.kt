package de.mm20.launcher2.locations.providers.openstreetmaps

import android.content.Context
import de.mm20.launcher2.ktx.ifNullOrEmpty
import de.mm20.launcher2.ktx.into
import de.mm20.launcher2.ktx.map
import de.mm20.launcher2.ktx.stripStartOrNull
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
import de.mm20.launcher2.search.location.PaymentMethod
import de.westnordost.osm_opening_hours.model.ClockTime
import de.westnordost.osm_opening_hours.model.ExtendedClockTime
import de.westnordost.osm_opening_hours.model.LastNth
import de.westnordost.osm_opening_hours.model.MonthRange
import de.westnordost.osm_opening_hours.model.Nth
import de.westnordost.osm_opening_hours.model.NthRange
import de.westnordost.osm_opening_hours.model.Range
import de.westnordost.osm_opening_hours.model.SingleMonth
import de.westnordost.osm_opening_hours.model.SpecificWeekdays
import de.westnordost.osm_opening_hours.model.StartingAtYear
import de.westnordost.osm_opening_hours.model.TimeSpan
import de.westnordost.osm_opening_hours.model.TimesSelector
import de.westnordost.osm_opening_hours.model.TwentyFourSeven
import de.westnordost.osm_opening_hours.model.Weekday
import de.westnordost.osm_opening_hours.model.WeekdayRange
import de.westnordost.osm_opening_hours.model.WeekdaysSelector
import de.westnordost.osm_opening_hours.model.Year
import de.westnordost.osm_opening_hours.model.YearRange
import de.westnordost.osm_opening_hours.parser.toOpeningHoursOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.woheller69.AndroidAddressFormatter.OsmAddressFormatter
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
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
    override val userRating: Float?,
    override val acceptedPaymentMethods: Map<PaymentMethod, Boolean>?
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

        internal val addressFormatter =
            OsmAddressFormatter(
                false,
                false,
                false
            )

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
                address = it.tags.toAddress(),
                openingSchedule = it.tags["opening_hours"]?.let { ot -> parseOpeningSchedule(ot) },
                websiteUrl = it.tags["website"] ?: it.tags["contact:website"],
                phoneNumber = it.tags["phone"] ?: it.tags["contact:phone"],
                emailAddress = it.tags["email"] ?: it.tags["contact:email"],
                timestamp = System.currentTimeMillis(),
                userRating = it.tags["stars"]?.runCatching { this.toInt() }?.getOrNull()
                    ?.let { min(it, 5) / 5.0f },
                acceptedPaymentMethods = with(
                    it.tags.mapNotNull { (key, value) ->
                        (key.stripStartOrNull("payment:") ?: return@mapNotNull null) to value
                    }.toMap()
                ) {
                    // best-effort way to take any method payment as it being available,
                    // otherwise as being unavailable, or undefined
                    mapOf(
                        PaymentMethod.Card to listOf("credit_cards", "debit_cards", "cards"),
                        PaymentMethod.Cash to listOf("cash")
                    ).mapNotNull { (method, values) ->
                        when {
                            values.any { this[it] in listOf("yes", "only") } -> method to true
                            values.any { this[it] == "no" } -> method to false
                            else -> null
                        }
                    }.toMap().takeUnless { it.isEmpty() }
                }
            )
        }
    }
}

private fun Map<String, String>.firstOfAlso(vararg strs: String, also: (String) -> Unit): String? {
    for (str in strs) {
        if (str in this) {
            also(str)
            return this[str]
        }
    }
    return null
}

private fun Map<String, String>.toAddress(): Address? {
    val formatAddrKeys = this.keys.filter { it.contains("addr") }.toMutableSet()
    if (formatAddrKeys.isEmpty()) return null

    val addr = Address(
        city = firstOfAlso("addr:city", "addr:suburb", "addr:hamlet") { formatAddrKeys.remove(it) },
        state = firstOfAlso("addr:state", "addr:province") { formatAddrKeys.remove(it) },
        postalCode = firstOfAlso("addr:postcode") { formatAddrKeys.remove(it) },
        country = firstOfAlso("addr:country") { formatAddrKeys.remove(it) },
    )

    val formattedRest = buildJsonObject {
        formatAddrKeys.mapNotNull {
            val (_, subkey) = it.split(':', limit = 2).takeIf { it.size == 2 }
                ?: return@mapNotNull null
            put(subkey, this@toAddress[it])
        }
    }.takeIf { it.isNotEmpty() }?.toString()?.runCatching {
        OsmLocation.addressFormatter.format(
            this,
            this@toAddress["addr:country"] ?: Locale.getDefault().country
        )
    }?.getOrNull() ?: return addr

    val lines = formattedRest.lines().filter { it.isNotBlank() }
    return addr.copy(
        address = lines.getOrNull(0),
        address2 = lines.getOrNull(1),
        address3 = lines.getOrNull(2),
    )
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

private fun Map<String, String>.categorize(context: Context): Pair<String?, LocationIcon?> {
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
                    "stationery" -> R.string.poi_category_stationery to LocationIcon.Stationery

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
                    } ?: (R.string.poi_category_place_of_worship to LocationIcon.PlaceOfWorship)

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
                    } ?: (R.string.poi_category_restaurant to LocationIcon.Restaurant)

                    "fuel" -> R.string.poi_category_fuel to LocationIcon.GasStation
                    "car_rental" -> R.string.poi_category_car_rental to LocationIcon.CarRental
                    "car_sharing" -> R.string.poi_category_car_sharing to LocationIcon.CarRental
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
                    "townhall" -> R.string.poi_category_townhall to LocationIcon.GovernmentBuilding

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
                    "pitch", "sports_centre", "sports_hall" -> matchAnyTag<Int, LocationIcon>("sport") {
                        "soccer" with (R.string.poi_category_soccer to LocationIcon.Soccer)
                        "tennis" with (R.string.poi_category_tennis to LocationIcon.Tennis)
                        "basketball" with (R.string.poi_category_basketball to LocationIcon.Basketball)
                        "gymnastics" with (R.string.poi_category_gymnastics to LocationIcon.Gymnastics)
                        "martial_arts" with (R.string.poi_category_martial_arts to LocationIcon.MartialArts)
                        "ice_hockey" with (R.string.poi_category_ice_hockey to LocationIcon.Hockey)
                        "baseball" with (R.string.poi_category_baseball to LocationIcon.Baseball)
                        "american_football" with (R.string.poi_category_american_football to LocationIcon.AmericanFootball)
                        "handball" with (R.string.poi_category_handball to LocationIcon.Handball)
                        "volleyball" with (R.string.poi_category_volleyball to LocationIcon.Volleyball)
                        "skiing" with (R.string.poi_category_skiing to LocationIcon.Skiing)
                        "cricket" with (R.string.poi_category_cricket to LocationIcon.Cricket)
                        "climbing" with (R.string.poi_category_climbing_gym to LocationIcon.Climbing)
                    }

                    "golf_course" -> R.string.poi_category_golf to LocationIcon.Golf
                    "park" -> R.string.poi_category_park to LocationIcon.Park
                    "hackerspace" -> R.string.poi_category_hackerspace to LocationIcon.Hackerspace
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
    val (rid, icon) = category ?: return null to null
    return context.resources.getString(rid) to icon
}

internal fun parseOpeningSchedule(
    openingHours: String?,
    localTime: LocalDateTime = LocalDateTime.now()
): OpeningSchedule? {
    val parsed = openingHours?.toOpeningHoursOrNull(lenient = true) ?: return null

    if (parsed.rules.singleOrNull()?.selector is TwentyFourSeven) {
        return OpeningSchedule.TwentyFourSeven
    }

    val rangeRules = parsed.rules.mapNotNull { it.selector as? Range }

    // Group rules by the weekdays they apply to. Rules can apply to multiple weekdays.
    val rulesMap = mutableMapOf<Weekday, MutableList<Range>>(
        Weekday.Monday to mutableListOf(),
        Weekday.Tuesday to mutableListOf(),
        Weekday.Wednesday to mutableListOf(),
        Weekday.Thursday to mutableListOf(),
        Weekday.Friday to mutableListOf(),
        Weekday.Saturday to mutableListOf(),
        Weekday.Sunday to mutableListOf()
    )

    for (rule in rangeRules) {
        if (rule.weekdays != null) {
            for (selector in rule.weekdays!!) {
                when (selector) {
                    is Weekday -> rulesMap[selector]!!.add(rule)
                    is WeekdayRange -> {
                        for (weekday in selector.start.ordinal..selector.end.ordinal) {
                            rulesMap[Weekday.entries[weekday]]!!.add(rule)
                        }
                    }
                    is SpecificWeekdays -> {
                        rulesMap[selector.weekday]!!.add(rule)
                    }
                }
            }
        } else if (!rule.holidays.isNullOrEmpty()) {
            continue // skip PH and SH entries
        } else if (!rule.times.isNullOrEmpty() || !rule.months.isNullOrEmpty()) {
            rulesMap.forEach { _, it ->
                it.add(rule)
            }
        }
    }

    // Filter out rules that are not valid for the current year, month, and week.
    val applicableRules = rulesMap.flatMap { (day, rules) ->
        rules.filterYears(localTime)
            .filterMonths(localTime)
            .filterNthDays(localTime)
            .map { it.copy(weekdays = listOf(day)) }
    }

    val hours = mutableSetOf<OpeningHours>()

    for (range in applicableRules) {

        val localTimesWithDuration =
            range.times?.mapNotNull { it.toLocalTimeWithDuration() } ?: continue
        val daysOfWeek = range.weekdays
            .ifNullOrEmpty { Weekday.entries.toList() }
            .flatMap { it.toDaysOfWeek() }

        hours += daysOfWeek.flatMap { dow ->
            localTimesWithDuration.map {
                val (start, dur) = it
                OpeningHours(dow, start, dur)
            }
        }
    }

    return OpeningSchedule.Hours(hours)
}

/**
 * Returns only the rules that are valid for the given year.
 */
private fun List<Range>.filterYears(localTime: LocalDateTime): List<Range> {
    if (all { it.years.isNullOrEmpty() }) return this

    val thisYear = filter {
        it.years?.any {
            when (it) {
                is Year -> it.year == localTime.year
                is StartingAtYear -> it.start <= localTime.year
                is YearRange -> localTime.year in it.start..it.end step (it.step ?: 1)
            }
        } == true
    }

    if (!thisYear.isEmpty()) return thisYear

    return filter { it.years.isNullOrEmpty() }
}

/**
 * Returns only the rules that are valid for the given month.
 */
private fun List<Range>.filterMonths(localTime: LocalDateTime): List<Range> {
    if (all { it.months.isNullOrEmpty() }) return this

    val thisMonth = filter {
        it.months?.any {
            when (it) {
                is MonthRange -> (it.year?.let { it == localTime.year } != false) && localTime.month.ordinal in it.start.ordinal..it.end.ordinal

                is SingleMonth -> (it.year?.let { it == localTime.year } != false) && localTime.month.ordinal == it.month.ordinal

                else -> false
            }
        } == true
    }

    if (!thisMonth.isEmpty()) return thisMonth

    return filter { it.months.isNullOrEmpty() }
}

/**
 * Returns only the rules that are valid for the given week.
 * (i.e. if the given week is the 2nd week of the month, it will return only the rules that are
 * valid for the 2nd week of the month)
 */
private fun List<Range>.filterNthDays(localTime: LocalDateTime): List<Range> {
    if (none { it.weekdays?.any { it is SpecificWeekdays } == true }) return this

    val currentWeek = localTime.getNthWeekdaysOfCurrentWeek()

    val specific = mapNotNull { range ->
        (range.weekdays?.singleOrNull() as? SpecificWeekdays)?.let {
            range to it
        }
    }

    val rule = specific.firstOrNull { (_, specific) ->
        currentWeek.any { (dow, nthFwd, nthBwd) ->
            specific.weekday.ordinal == dow.ordinal && specific.nths.any {
                when (it) {
                    is Nth -> it.nth == nthFwd
                    is NthRange -> nthFwd in it.start..it.end
                    is LastNth -> it.nth == nthBwd
                }
            }
        }
    }

    if (rule != null) return listOf(rule.first)

    return listOfNotNull(
        singleOrNull { it.weekdays?.singleOrNull() !is SpecificWeekdays }
    )
}

private fun WeekdaysSelector.toDaysOfWeek(): List<DayOfWeek> {
    return when (this) {
        is Weekday -> listOf(
            when (this) {
                Weekday.Monday -> DayOfWeek.MONDAY
                Weekday.Tuesday -> DayOfWeek.TUESDAY
                Weekday.Wednesday -> DayOfWeek.WEDNESDAY
                Weekday.Thursday -> DayOfWeek.THURSDAY
                Weekday.Friday -> DayOfWeek.FRIDAY
                Weekday.Saturday -> DayOfWeek.SATURDAY
                Weekday.Sunday -> DayOfWeek.SUNDAY
            }
        )

        is WeekdayRange -> (start to end).map { it.toDaysOfWeek().single().value }
            .into { start, end -> (start..end).map { DayOfWeek.of(it) } }

        is SpecificWeekdays -> weekday.toDaysOfWeek()
    }
}


private fun TimesSelector.toLocalTimeWithDuration(): Pair<LocalTime, Duration>? {
    if (this !is TimeSpan) return null

    val start = start as? ClockTime ?: return null
    val end = end as? ExtendedClockTime ?: return null

    return LocalTime.of(
        start.hour,
        start.minutes
    ) to Duration.ofMinutes(
        (Math.floorMod(
            end.hour - start.hour,
            24
        ) * 60 + end.minutes - start.minutes).toLong()
    )
}

/**
 * Calculates the ordinal position of each day of the current week (Monday to Sunday) within the month.
 *
 * @receiver LocalDateTime The current date and time.
 * @return A list of triples, where each triple contains:
 * - The `DayOfWeek` (e.g., Monday, Tuesday, etc.).
 * - The forward ordinal position of the day in the month (e.g., 1st Monday, 2nd Tuesday, etc.).
 * - The backward ordinal position of the day in the month (e.g., last Monday, 2nd last Tuesday, etc.).
 *
 * Example:
 * If today is the 15th of a month (Wednesday), the function will return:
 * - For Monday: `(DayOfWeek.MONDAY, 3, 2)` (3rd Monday of the month, 2nd last Monday of the month).
 * - For Wednesday: `(DayOfWeek.WEDNESDAY, 3, 2)` (3rd Wednesday of the month, 2nd last Wednesday of the month).
 */
private fun LocalDateTime.getNthWeekdaysOfCurrentWeek(): List<Triple<DayOfWeek, Int, Int>> {
    val monday = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0 until 7).map { i ->
        val (nth, nthLast) = monday.plusDays(i.toLong()).let { weekday ->
            (
                    ChronoUnit.WEEKS.between(
                        with(TemporalAdjusters.firstInMonth(weekday.dayOfWeek)),
                        weekday
                    ).toInt() + 1
                    ) to (
                    ChronoUnit.WEEKS.between(
                        weekday,
                        with(TemporalAdjusters.lastInMonth(weekday.dayOfWeek))
                    ).toInt() + 1
                    )
        }
        Triple(DayOfWeek.entries[i], nth, nthLast)
    }
}