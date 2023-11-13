package de.mm20.launcher2.search

import android.content.Context
import androidx.core.content.ContextCompat
import de.mm20.launcher2.base.R
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

interface Location : SavableSearchable {

    val latitude: Double
    val longitude: Double

    suspend fun getCategory(): LocationCategory?
    suspend fun getStreet(): String?
    suspend fun getHouseNumber(): String?
    suspend fun getOpeningHours(): List<OpeningTime>?
    suspend fun getWebsiteUrl(): String?

    override val preferDetailsOverLaunch: Boolean
        get() = true

    override suspend fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        val (resId, bgColor) = when (getCategory()) {
            LocationCategory.RESTAURANT -> R.drawable.ic_location_restaurant to R.color.orange
            LocationCategory.FAST_FOOD -> R.drawable.ic_location_fastfood to R.color.red
            LocationCategory.BAR -> R.drawable.ic_location_bar to R.color.amber
            LocationCategory.CAFE -> R.drawable.ic_location_cafe to R.color.brown
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
            LocationCategory.LIBRARY -> R.drawable.ic_location_library to R.color.brown
            LocationCategory.COLLEGE, LocationCategory.UNIVERSITY -> R.drawable.ic_location_college to R.color.purple
            LocationCategory.ICE_CREAM -> R.drawable.ic_location_ice_cream to R.color.pink
            LocationCategory.THEATRE -> R.drawable.ic_location_theatre to R.color.purple
            LocationCategory.PUBLIC_BUILDING -> R.drawable.ic_location_public_building to R.color.bluegrey
            LocationCategory.CINEMA -> R.drawable.ic_location_cinema to R.color.purple
            LocationCategory.NIGHTCLUB -> R.drawable.ic_location_nightclub to R.color.purple
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
    UNIVERSITY
}

data class OpeningTime(val dayOfWeek: DayOfWeek, val startTime: LocalTime, val duration: Duration) {
    val isOpen: Boolean
        get() = LocalDate.now().dayOfWeek == dayOfWeek &&
                LocalTime.now().isAfter(startTime) &&
                LocalTime.now().isBefore(startTime.plus(duration))

    override fun toString(): String = "$dayOfWeek $startTime-${startTime.plus(duration)}"
}
