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
    OTHER
}

data class OpeningTime(val dayOfWeek: DayOfWeek, val startTime: LocalTime, val duration: Duration) {
    val isOpen: Boolean
        get() = LocalDate.now().dayOfWeek == dayOfWeek &&
                LocalTime.now().isAfter(startTime) &&
                LocalTime.now().isBefore(startTime.plus(duration))

    override fun toString(): String = "$dayOfWeek $startTime-${startTime.plus(duration)}"
}
