package de.mm20.launcher2.search

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Attractions
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CarRental
import androidx.compose.material.icons.rounded.CarRepair
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Discount
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.KebabDining
import androidx.compose.material.icons.rounded.Liquor
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalCarWash
import androidx.compose.material.icons.rounded.LocalConvenienceStore
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material.icons.rounded.LocalPostOffice
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Money
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Museum
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Nightlife
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Plumbing
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material.icons.rounded.Stadium
import androidx.compose.material.icons.rounded.Subway
import androidx.compose.material.icons.rounded.Synagogue
import androidx.compose.material.icons.rounded.TempleBuddhist
import androidx.compose.material.icons.rounded.TempleHindu
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tram
import androidx.core.content.ContextCompat
import de.mm20.launcher2.base.R
import de.mm20.launcher2.icons.CableCar
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.VectorLayer
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.location.Address
import de.mm20.launcher2.search.location.Attribution
import de.mm20.launcher2.search.location.Departure
import de.mm20.launcher2.search.location.LocationIcon
import de.mm20.launcher2.search.location.OpeningHours
import de.mm20.launcher2.search.location.OpeningSchedule
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import android.location.Location as AndroidLocation

interface Location : SavableSearchable {

    val latitude: Double
    val longitude: Double
    val fixMeUrl: String?

    val icon: LocationIcon?
    val category: String?

    val address: Address?

    val websiteUrl: String?
    val phoneNumber: String?

    val userRating: Float?

    val openingSchedule: OpeningSchedule?
    val departures: List<Departure>?

    val attribution: Attribution?
        get() = null

    override val preferDetailsOverLaunch: Boolean
        get() = true

    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:$latitude,$longitude?q=${Uri.encode(label)}")
            ),
            options
        )
    }

    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        val (vector, bgColor) = when (icon) {
            // blue: transportation
            LocationIcon.Car -> Icons.Rounded.DirectionsCar to R.color.blue
            LocationIcon.CarRental -> Icons.Rounded.CarRental to R.color.blue
            LocationIcon.CarRepair -> Icons.Rounded.CarRepair to R.color.blue
            LocationIcon.CarWash -> Icons.Rounded.LocalCarWash to R.color.blue
            LocationIcon.ChargingStation -> Icons.Rounded.EvStation to R.color.blue
            LocationIcon.GasStation -> Icons.Rounded.LocalGasStation to R.color.blue
            LocationIcon.Parking -> Icons.Rounded.LocalParking to R.color.blue
            LocationIcon.Bus -> Icons.Rounded.DirectionsBus to R.color.blue
            LocationIcon.Tram -> Icons.Rounded.Tram to R.color.blue
            LocationIcon.Subway -> Icons.Rounded.Subway to R.color.blue
            LocationIcon.Train -> Icons.Rounded.Train to R.color.blue
            LocationIcon.CableCar -> Icons.Rounded.CableCar to R.color.blue
            LocationIcon.Airport -> Icons.Rounded.Flight to R.color.blue
            LocationIcon.Boat -> Icons.Rounded.DirectionsBoat to R.color.blue

            // cyan: museum, art, culture, entertainment
            LocationIcon.ArtGallery -> Icons.Rounded.Palette to R.color.cyan
            LocationIcon.Museum -> Icons.Rounded.Museum to R.color.cyan
            LocationIcon.Theater -> Icons.Rounded.TheaterComedy to R.color.cyan
            LocationIcon.MovieTheater -> Icons.Rounded.Theaters to R.color.cyan
            LocationIcon.AmusementPark -> Icons.Rounded.Attractions to R.color.cyan
            LocationIcon.NightClub -> Icons.Rounded.Nightlife to R.color.cyan
            LocationIcon.Music -> Icons.Rounded.MusicNote to R.color.cyan
            LocationIcon.Stadium -> Icons.Rounded.Stadium to R.color.cyan
            LocationIcon.Casino -> Icons.Rounded.Casino to R.color.cyan
            // pink: hotels
            LocationIcon.Hotel -> Icons.Rounded.Hotel to R.color.pink
            // amber: food and drink
            LocationIcon.Restaurant -> Icons.Rounded.Restaurant to R.color.amber
            LocationIcon.Cafe -> Icons.Rounded.LocalCafe to R.color.amber
            LocationIcon.FastFood -> Icons.Rounded.Fastfood to R.color.amber
            LocationIcon.Pizza -> Icons.Rounded.LocalPizza to R.color.amber
            LocationIcon.Burger -> Icons.Rounded.LunchDining to R.color.amber
            LocationIcon.Kebab -> Icons.Rounded.KebabDining to R.color.amber
            LocationIcon.IceCream -> Icons.Rounded.Icecream to R.color.amber
            LocationIcon.Ramen -> Icons.Rounded.RamenDining to R.color.amber
            LocationIcon.Soup -> Icons.Rounded.SoupKitchen to R.color.amber
            LocationIcon.Bar -> Icons.Rounded.LocalBar to R.color.amber
            // deep purple: shopping and services
            LocationIcon.Shopping -> Icons.Rounded.ShoppingBag to R.color.deeppurple
            LocationIcon.Supermarket -> Icons.Rounded.LocalGroceryStore to R.color.deeppurple
            LocationIcon.Florist -> Icons.Rounded.LocalFlorist to R.color.deeppurple
            LocationIcon.Kiosk -> Icons.Rounded.Newspaper to R.color.deeppurple
            LocationIcon.FurnitureStore -> Icons.Rounded.Bed to R.color.deeppurple
            LocationIcon.CellPhoneStore -> Icons.Rounded.Phone to R.color.deeppurple
            LocationIcon.BookStore -> Icons.Rounded.Book to R.color.deeppurple
            LocationIcon.ClothingStore -> Icons.Rounded.Checkroom to R.color.deeppurple
            LocationIcon.ConvenienceStore -> Icons.Rounded.LocalConvenienceStore to R.color.deeppurple
            LocationIcon.DiscountStore -> Icons.Rounded.Discount to R.color.deeppurple
            LocationIcon.JewelryStore -> Icons.Rounded.Diamond to R.color.deeppurple
            LocationIcon.ShoppingMall -> Icons.Rounded.LocalMall to R.color.deeppurple
            LocationIcon.LiquorStore -> Icons.Rounded.Liquor to R.color.deeppurple
            LocationIcon.PetStore -> Icons.Rounded.Pets to R.color.deeppurple
            // red: health
            LocationIcon.Hospital -> Icons.Rounded.LocalHospital to R.color.red
            LocationIcon.Pharmacy -> Icons.Rounded.LocalPharmacy to R.color.red
            // light green: nature
            LocationIcon.Park -> Icons.Rounded.Park to R.color.lightgreen
            LocationIcon.Forest -> Icons.Rounded.Forest to R.color.lightgreen
            // brown: places of worship and remembrance
            LocationIcon.Church -> Icons.Rounded.Church to R.color.brown
            LocationIcon.Mosque -> Icons.Rounded.Mosque to R.color.brown
            LocationIcon.Synagogue -> Icons.Rounded.Synagogue to R.color.brown
            LocationIcon.HinduTemple -> Icons.Rounded.TempleHindu to R.color.brown
            LocationIcon.BuddhistTemple -> Icons.Rounded.TempleBuddhist to R.color.brown
            // grey: public services
            LocationIcon.Police -> Icons.Rounded.LocalPolice to R.color.grey
            LocationIcon.FireDepartment -> Icons.Rounded.LocalFireDepartment to R.color.grey
            LocationIcon.Courthouse -> Icons.Rounded.Gavel to R.color.grey
            LocationIcon.PostOffice -> Icons.Rounded.LocalPostOffice to R.color.grey
            LocationIcon.Library -> Icons.Rounded.Book to R.color.grey
            LocationIcon.School -> Icons.Rounded.School to R.color.grey
            LocationIcon.University -> Icons.Rounded.School to R.color.grey
            LocationIcon.Bank -> Icons.Rounded.AttachMoney to R.color.grey

            null -> Icons.Rounded.Place to R.color.bluegrey
        }
        return StaticLauncherIcon(
            foregroundLayer = VectorLayer(
                vector = vector,
                color = ContextCompat.getColor(context, bgColor)
            ),
            backgroundLayer = ColorLayer(ContextCompat.getColor(context, bgColor))
        )
    }

    fun toAndroidLocation(): AndroidLocation =
        AndroidLocation("KvaesitsoLocationProvider").apply {
            this.latitude = this@Location.latitude
            this.longitude = this@Location.longitude
        }

    fun distanceTo(androidLocation: AndroidLocation): Float {
        return androidLocation.distanceTo(this.toAndroidLocation())
    }

    fun distanceTo(otherLocation: Location): Float =
        this.distanceTo(otherLocation.toAndroidLocation())

    fun distanceTo(
        latitude: Double,
        longitude: Double,
        locationProvider: String = "KvaesitsoLocationProvider"
    ): Float =
        this.distanceTo(AndroidLocation(locationProvider).apply {
            this.latitude = latitude
            this.longitude = longitude
        })
}

fun OpeningSchedule.isOpen(date: LocalDateTime = LocalDateTime.now()): Boolean {
    return isTwentyFourSeven || openingHours.any { it.isOpen(date) }
}

fun OpeningHours.isOpen(date: LocalDateTime = LocalDateTime.now()): Boolean {
    val startTime = date.with(TemporalAdjusters.previousOrSame(dayOfWeek)).with(startTime)
    val endTime = startTime.plus(duration)
    return date in startTime..<endTime
}