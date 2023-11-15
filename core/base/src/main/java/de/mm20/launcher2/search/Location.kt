package de.mm20.launcher2.search

import android.content.Context
import androidx.core.content.ContextCompat
import de.mm20.launcher2.base.R
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer
import kotlinx.collections.immutable.ImmutableList
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

interface Location : SavableSearchable {

    val latitude: Double
    val longitude: Double
    val fixMeUrl: String?

    suspend fun getCategory(): LocationCategory?
    suspend fun getStreet(): String?
    suspend fun getHouseNumber(): String?
    suspend fun getOpeningSchedule(): OpeningSchedule?
    suspend fun getWebsiteUrl(): String?
    suspend fun getPhoneNumber(): String?

    override val preferDetailsOverLaunch: Boolean
        get() = true

    override suspend fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        val category = getCategory()
        val (resId, bgColor) = when (category) {
            LocationCategory.FAST_FOOD, LocationCategory.RESTAURANT -> with(labelOverride ?: label) {
                when {
                    contains("pizza", ignoreCase = true) -> R.drawable.ic_location_pizza to R.color.red
                    contains("ramen", ignoreCase = true) -> R.drawable.ic_location_ramen to R.color.orange
                    contains("tapas", ignoreCase = true) -> R.drawable.ic_location_tapas to R.color.orange
                    contains("keba" /* b or p, depending on locale */, ignoreCase = true) -> R.drawable.ic_location_kebab to R.color.orange
                    category == LocationCategory.FAST_FOOD -> R.drawable.ic_location_fastfood to R.color.orange
                    else -> R.drawable.ic_location_restaurant to R.color.red
                }
            }
            LocationCategory.BAR -> R.drawable.ic_location_bar to R.color.amber
            LocationCategory.CAFE, LocationCategory.COFFEE_SHOP -> R.drawable.ic_location_cafe to R.color.brown
            LocationCategory.HOTEL -> R.drawable.ic_location_hotel to R.color.green
            LocationCategory.SUPERMARKET -> R.drawable.ic_location_supermarket to R.color.lightblue
            LocationCategory.SCHOOL -> R.drawable.ic_location_school to R.color.purple
            LocationCategory.PARKING -> R.drawable.ic_location_parking to R.color.blue
            LocationCategory.FUEL -> R.drawable.ic_location_fuel to R.color.teal
            LocationCategory.TOILETS -> R.drawable.ic_location_toilets to R.color.blue
            LocationCategory.PHARMACY -> R.drawable.ic_location_pharmacy to R.color.pink
            LocationCategory.HOSPITAL, LocationCategory.CLINIC -> R.drawable.ic_location_hospital to R.color.red
            LocationCategory.POST_OFFICE -> R.drawable.ic_location_post_office to R.color.yellow
            LocationCategory.PUB, LocationCategory.BIERGARTEN -> R.drawable.ic_location_pub to R.color.amber
            LocationCategory.GRAVE_YARD -> R.drawable.ic_location_grave_yard to R.color.grey
            LocationCategory.DOCTORS -> R.drawable.ic_location_doctors to R.color.red
            LocationCategory.POLICE -> R.drawable.ic_location_police to R.color.blue
            LocationCategory.DENTIST -> R.drawable.ic_location_dentist to R.color.lightblue
            LocationCategory.LIBRARY, LocationCategory.BOOKS -> R.drawable.ic_location_library to R.color.brown
            LocationCategory.COLLEGE, LocationCategory.UNIVERSITY -> R.drawable.ic_location_college to R.color.purple
            LocationCategory.ICE_CREAM -> R.drawable.ic_location_ice_cream to R.color.pink
            LocationCategory.THEATRE -> R.drawable.ic_location_theatre to R.color.purple
            LocationCategory.PUBLIC_BUILDING -> R.drawable.ic_location_public_building to R.color.bluegrey
            LocationCategory.CINEMA -> R.drawable.ic_location_cinema to R.color.purple
            LocationCategory.NIGHTCLUB -> R.drawable.ic_location_nightclub to R.color.purple
            LocationCategory.CONVENIENCE -> R.drawable.ic_location_convenience to R.color.lightblue
            LocationCategory.CLOTHES -> R.drawable.ic_location_clothes to R.color.pink
            LocationCategory.HAIRDRESSER, LocationCategory.BEAUTY -> R.drawable.ic_location_hairdresser to R.color.pink
            LocationCategory.CAR_REPAIR -> R.drawable.ic_location_car_repair to R.color.blue
            LocationCategory.BAKERY -> R.drawable.ic_location_bakery to R.color.brown
            LocationCategory.CAR -> R.drawable.ic_location_car to R.color.blue
            LocationCategory.MOBILE_PHONE -> R.drawable.ic_location_mobile_phone to R.color.blue
            LocationCategory.FURNITURE -> R.drawable.ic_location_furniture to R.color.brown
            LocationCategory.ALCOHOL -> R.drawable.ic_location_alcohol to R.color.amber
            LocationCategory.FLORIST -> R.drawable.ic_location_florist to R.color.green
            LocationCategory.HARDWARE -> R.drawable.ic_location_hardware to R.color.brown
            LocationCategory.ELECTRONICS -> R.drawable.ic_location_electronics to R.color.blue
            LocationCategory.SHOES -> R.drawable.ic_location_shoes to R.color.pink
            LocationCategory.MALL, LocationCategory.DEPARTMENT_STORE, LocationCategory.CHEMIST -> R.drawable.ic_location_mall to R.color.blue
            LocationCategory.OPTICIAN -> R.drawable.ic_location_optician to R.color.blue
            LocationCategory.JEWELRY -> R.drawable.ic_location_jewelry to R.color.pink
            LocationCategory.GIFT -> R.drawable.ic_location_gift to R.color.pink
            LocationCategory.BICYCLE -> R.drawable.ic_location_bicycle to R.color.blue
            LocationCategory.LAUNDRY -> R.drawable.ic_location_laundry to R.color.blue
            LocationCategory.COMPUTER -> R.drawable.ic_location_computer to R.color.blue
            LocationCategory.TOBACCO -> R.drawable.ic_location_tobacco to R.color.amber
            LocationCategory.WINE -> R.drawable.ic_location_wine to R.color.amber
            LocationCategory.PHOTO -> R.drawable.ic_location_photo to R.color.blue
            LocationCategory.BANK -> R.drawable.ic_location_bank to R.color.blue
            LocationCategory.SOCCER -> R.drawable.ic_location_soccer to R.color.green
            LocationCategory.BASKETBALL -> R.drawable.ic_location_basketball to R.color.orange
            LocationCategory.TENNIS -> R.drawable.ic_location_tennis to R.color.orange
            LocationCategory.FITNESS, LocationCategory.FITNESS_CENTRE -> R.drawable.ic_location_fitness to R.color.orange
            LocationCategory.TRAM_STOP -> R.drawable.ic_location_tram_stop to R.color.blue
            LocationCategory.RAILWAY_STOP -> R.drawable.ic_location_railway_stop to R.color.lightblue
            LocationCategory.BUS_STATION, LocationCategory.BUS_STOP -> R.drawable.ic_location_bus_station to R.color.blue
            LocationCategory.ATM -> R.drawable.ic_location_atm to R.color.green
            LocationCategory.ART -> R.drawable.ic_location_art to R.color.deeporange
            LocationCategory.KIOSK -> R.drawable.ic_location_kiosk to R.color.bluegrey
            LocationCategory.MUSEUM -> R.drawable.ic_location_museum to R.color.deeporange
            LocationCategory.PARCEL_LOCKER -> R.drawable.ic_location_parcel_locker to R.color.bluegrey
            LocationCategory.TRAVEL_AGENCY -> R.drawable.ic_location_travel_agency to R.color.lightblue
            else -> R.drawable.ic_location_place to R.color.bluegrey
        }
        return StaticLauncherIcon(
            foregroundLayer = TintedIconLayer(
                icon = ContextCompat.getDrawable(context, resId)!!,
                scale = 0.5f,
                color = ContextCompat.getColor(context, bgColor)
            ),
            backgroundLayer = ColorLayer(ContextCompat.getColor(context, bgColor))
        )
    }

    fun toAndroidLocation(): android.location.Location {
        val location = android.location.Location("KvaesitsoLocationProvider")

        location.latitude = latitude
        location.longitude = longitude

        return location
    }
}

// https://taginfo.openstreetmap.org/tags
// 'amenity', 'shop', 'sport' of which the most important
enum class LocationCategory {
    RESTAURANT,
    FAST_FOOD,
    BAR,
    CAFE,
    HOTEL,
    SUPERMARKET,
    OTHER,
    SCHOOL,
    PARKING,
    FUEL,
    TOILETS,
    PHARMACY,
    HOSPITAL,
    POST_OFFICE,
    PUB,
    GRAVE_YARD,
    DOCTORS,
    POLICE,
    DENTIST,
    LIBRARY,
    COLLEGE,
    ICE_CREAM,
    THEATRE,
    PUBLIC_BUILDING,
    CINEMA,
    NIGHTCLUB,
    BIERGARTEN,
    CLINIC,
    UNIVERSITY,
    DEPARTMENT_STORE,
    CLOTHES,
    CONVENIENCE,
    HAIRDRESSER,
    CAR_REPAIR,
    BEAUTY,
    BOOKS,
    BAKERY,
    CAR,
    MOBILE_PHONE,
    FURNITURE,
    ALCOHOL,
    FLORIST,
    HARDWARE,
    ELECTRONICS,
    SHOES,
    MALL,
    OPTICIAN,
    JEWELRY,
    GIFT,
    BICYCLE,
    LAUNDRY,
    COMPUTER,
    TOBACCO,
    WINE,
    PHOTO,
    COFFEE_SHOP,
    BANK,
    SOCCER,
    BASKETBALL,
    TENNIS,
    FITNESS,
    TRAM_STOP,
    RAILWAY_STOP,
    BUS_STATION,
    ATM,
    ART,
    KIOSK,
    BUS_STOP,
    MUSEUM,
    PARCEL_LOCKER,
    CHEMIST,
    TRAVEL_AGENCY,
    FITNESS_CENTRE
}

data class OpeningHours(val dayOfWeek: DayOfWeek, val startTime: LocalTime, val duration: Duration) {
    val isOpen: Boolean
        get() = LocalDate.now().dayOfWeek == dayOfWeek &&
                LocalTime.now().isAfter(startTime) &&
                LocalTime.now().isBefore(startTime.plus(duration))

    override fun toString(): String = "$dayOfWeek $startTime-${startTime.plus(duration)}"
}

data class OpeningSchedule(val isTwentyFourSeven: Boolean, val openingHours: ImmutableList<OpeningHours>) {
    val isOpen: Boolean
        get() = isTwentyFourSeven || openingHours.any { it.isOpen }
}
