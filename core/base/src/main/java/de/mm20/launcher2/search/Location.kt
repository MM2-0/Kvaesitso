package de.mm20.launcher2.search

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Attractions
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Bento
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.BreakfastDining
import androidx.compose.material.icons.rounded.BrunchDining
import androidx.compose.material.icons.rounded.CarRental
import androidx.compose.material.icons.rounded.CarRepair
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DirectionsTransit
import androidx.compose.material.icons.rounded.Discount
import androidx.compose.material.icons.rounded.DownhillSkiing
import androidx.compose.material.icons.rounded.ElectricScooter
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Festival
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.Kayaking
import androidx.compose.material.icons.rounded.KebabDining
import androidx.compose.material.icons.rounded.Liquor
import androidx.compose.material.icons.rounded.LocalAtm
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalCarWash
import androidx.compose.material.icons.rounded.LocalConvenienceStore
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material.icons.rounded.LocalPostOffice
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Moped
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Motorcycle
import androidx.compose.material.icons.rounded.Museum
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Nightlife
import androidx.compose.material.icons.rounded.NordicWalking
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Paragliding
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Skateboarding
import androidx.compose.material.icons.rounded.Snowboarding
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material.icons.rounded.Sports
import androidx.compose.material.icons.rounded.SportsBar
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsCricket
import androidx.compose.material.icons.rounded.SportsFootball
import androidx.compose.material.icons.rounded.SportsGolf
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.SportsHandball
import androidx.compose.material.icons.rounded.SportsHockey
import androidx.compose.material.icons.rounded.SportsMartialArts
import androidx.compose.material.icons.rounded.SportsMotorsports
import androidx.compose.material.icons.rounded.SportsRugby
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.SportsVolleyball
import androidx.compose.material.icons.rounded.Stadium
import androidx.compose.material.icons.rounded.Subway
import androidx.compose.material.icons.rounded.Surfing
import androidx.compose.material.icons.rounded.Synagogue
import androidx.compose.material.icons.rounded.TakeoutDining
import androidx.compose.material.icons.rounded.TempleBuddhist
import androidx.compose.material.icons.rounded.TempleHindu
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material.icons.rounded.Wc
import androidx.core.content.ContextCompat
import de.mm20.launcher2.base.R
import de.mm20.launcher2.icons.CableCar
import de.mm20.launcher2.icons.Candle
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.Dentistry
import de.mm20.launcher2.icons.Eyeglasses
import de.mm20.launcher2.icons.Monument
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.Stethoscope
import de.mm20.launcher2.icons.VectorLayer
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.location.Address
import de.mm20.launcher2.search.location.Attribution
import de.mm20.launcher2.search.location.Departure
import de.mm20.launcher2.search.location.LocationIcon
import de.mm20.launcher2.search.location.OpeningHours
import de.mm20.launcher2.search.location.OpeningSchedule
import de.mm20.launcher2.search.location.PaymentMethod
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import android.location.Location as AndroidLocation
import androidx.core.net.toUri
import de.mm20.launcher2.icons.Climbing

interface Location : SavableSearchable {

    val latitude: Double
    val longitude: Double
    val fixMeUrl: String?

    val icon: LocationIcon?
    val category: String?

    val address: Address?

    val websiteUrl: String?
    val phoneNumber: String?
    val emailAddress: String?

    val userRating: Float?
    val userRatingCount: Int?

    val openingSchedule: OpeningSchedule?
    val departures: List<Departure>?

    val acceptedPaymentMethods: Map<PaymentMethod, Boolean>?

    val attribution: Attribution?
        get() = null

    override val preferDetailsOverLaunch: Boolean
        get() = true

    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(
            Intent(
                Intent.ACTION_VIEW,
                "geo:$latitude,$longitude?q=${Uri.encode(label)}".toUri()
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
            LocationIcon.Moped -> Icons.Rounded.Moped to R.color.blue
            LocationIcon.Bike -> Icons.AutoMirrored.Rounded.DirectionsBike to R.color.blue
            LocationIcon.Motorcycle -> Icons.Rounded.Motorcycle to R.color.blue
            LocationIcon.ElectricScooter -> Icons.Rounded.ElectricScooter to R.color.blue
            LocationIcon.Taxi -> Icons.Rounded.LocalTaxi to R.color.blue
            LocationIcon.GenericTransit -> Icons.Rounded.DirectionsTransit to R.color.blue

            // cyan: art, culture, entertainment
            LocationIcon.ArtGallery -> Icons.Rounded.Palette to R.color.cyan
            LocationIcon.Museum -> Icons.Rounded.Museum to R.color.cyan
            LocationIcon.Theater -> Icons.Rounded.TheaterComedy to R.color.cyan
            LocationIcon.MovieTheater -> Icons.Rounded.Theaters to R.color.cyan
            LocationIcon.AmusementPark -> Icons.Rounded.Attractions to R.color.cyan
            LocationIcon.NightClub -> Icons.Rounded.Nightlife to R.color.cyan
            LocationIcon.ConcertHall -> Icons.Rounded.MusicNote to R.color.cyan
            LocationIcon.Stadium -> Icons.Rounded.Stadium to R.color.cyan
            LocationIcon.Casino -> Icons.Rounded.Casino to R.color.cyan
            LocationIcon.Circus -> Icons.Rounded.Festival to R.color.cyan
            // pink: hotels
            LocationIcon.Hotel -> Icons.Rounded.Hotel to R.color.pink
            // orange: food and drink
            LocationIcon.Restaurant -> Icons.Rounded.Restaurant to R.color.orange
            LocationIcon.Cafe -> Icons.Rounded.LocalCafe to R.color.orange
            LocationIcon.FastFood -> Icons.Rounded.Fastfood to R.color.orange
            LocationIcon.Pizza -> Icons.Rounded.LocalPizza to R.color.orange
            LocationIcon.Burger -> Icons.Rounded.LunchDining to R.color.orange
            LocationIcon.Kebab -> Icons.Rounded.KebabDining to R.color.orange
            LocationIcon.IceCream -> Icons.Rounded.Icecream to R.color.orange
            LocationIcon.Ramen -> Icons.Rounded.RamenDining to R.color.orange
            LocationIcon.Soup -> Icons.Rounded.SoupKitchen to R.color.orange
            LocationIcon.Bar -> Icons.Rounded.LocalBar to R.color.orange
            LocationIcon.Brunch -> Icons.Rounded.BrunchDining to R.color.orange
            LocationIcon.Breakfast -> Icons.Rounded.BreakfastDining to R.color.orange
            LocationIcon.Pub -> Icons.Rounded.SportsBar to R.color.orange
            LocationIcon.JapaneseCuisine -> Icons.Rounded.Bento to R.color.orange
            LocationIcon.AsianCuisine -> Icons.Rounded.TakeoutDining to R.color.orange
            // indigo: business and shopping
            LocationIcon.Shopping -> Icons.Rounded.ShoppingBag to R.color.indigo
            LocationIcon.Supermarket -> Icons.Rounded.LocalGroceryStore to R.color.indigo
            LocationIcon.Florist -> Icons.Rounded.LocalFlorist to R.color.indigo
            LocationIcon.Kiosk -> Icons.Rounded.Newspaper to R.color.indigo
            LocationIcon.FurnitureStore -> Icons.Rounded.Bed to R.color.indigo
            LocationIcon.CellPhoneStore -> Icons.Rounded.Phone to R.color.indigo
            LocationIcon.BookStore -> Icons.Rounded.Book to R.color.indigo
            LocationIcon.ClothingStore -> Icons.Rounded.Checkroom to R.color.indigo
            LocationIcon.ConvenienceStore -> Icons.Rounded.LocalConvenienceStore to R.color.indigo
            LocationIcon.DiscountStore -> Icons.Rounded.Discount to R.color.indigo
            LocationIcon.JewelryStore -> Icons.Rounded.Diamond to R.color.indigo
            LocationIcon.ShoppingMall -> Icons.Rounded.LocalMall to R.color.indigo
            LocationIcon.LiquorStore -> Icons.Rounded.Liquor to R.color.indigo
            LocationIcon.PetStore -> Icons.Rounded.Pets to R.color.indigo
            LocationIcon.Bakery -> Icons.Rounded.BakeryDining to R.color.indigo
            LocationIcon.Optician -> Icons.Rounded.Eyeglasses to R.color.indigo
            LocationIcon.Pharmacy -> Icons.Rounded.LocalPharmacy to R.color.indigo
            LocationIcon.HairSalon -> Icons.Rounded.ContentCut to R.color.indigo
            LocationIcon.Laundromat -> Icons.Rounded.LocalLaundryService to R.color.indigo
            LocationIcon.Stationery -> Icons.Rounded.AttachFile to R.color.indigo
            // purple: sports and recreation
            LocationIcon.Sports -> Icons.Rounded.Sports to R.color.purple
            LocationIcon.FitnessCenter -> Icons.Rounded.FitnessCenter to R.color.purple
            LocationIcon.Soccer -> Icons.Rounded.SportsSoccer to R.color.purple
            LocationIcon.Basketball -> Icons.Rounded.SportsBasketball to R.color.purple
            LocationIcon.Golf -> Icons.Rounded.SportsGolf to R.color.purple
            LocationIcon.Tennis -> Icons.Rounded.SportsTennis to R.color.purple
            LocationIcon.Baseball -> Icons.Rounded.SportsBaseball to R.color.purple
            LocationIcon.Rugby -> Icons.Rounded.SportsRugby to R.color.purple
            LocationIcon.AmericanFootball -> Icons.Rounded.SportsFootball to R.color.purple
            LocationIcon.Hiking -> Icons.Rounded.Hiking to R.color.purple
            LocationIcon.Swimming -> Icons.Rounded.Pool to R.color.purple
            LocationIcon.Surfing -> Icons.Rounded.Surfing to R.color.purple
            LocationIcon.Motorsports -> Icons.Rounded.SportsMotorsports to R.color.purple
            LocationIcon.Handball -> Icons.Rounded.SportsHandball to R.color.purple
            LocationIcon.Volleyball -> Icons.Rounded.SportsVolleyball to R.color.purple
            LocationIcon.Skiing -> Icons.Rounded.DownhillSkiing to R.color.purple
            LocationIcon.Kayaking -> Icons.Rounded.Kayaking to R.color.purple
            LocationIcon.Skateboarding -> Icons.Rounded.Skateboarding to R.color.purple
            LocationIcon.Cricket -> Icons.Rounded.SportsCricket to R.color.purple
            LocationIcon.Climbing -> Icons.Rounded.Climbing to R.color.purple
            LocationIcon.MartialArts -> Icons.Rounded.SportsMartialArts to R.color.purple
            LocationIcon.NordicWalking -> Icons.Rounded.NordicWalking to R.color.purple
            LocationIcon.Paragliding -> Icons.Rounded.Paragliding to R.color.purple
            LocationIcon.Gymnastics -> Icons.Rounded.SportsGymnastics to R.color.purple
            LocationIcon.Snowboarding -> Icons.Rounded.Snowboarding to R.color.purple
            LocationIcon.Hockey -> Icons.Rounded.SportsHockey to R.color.purple
            LocationIcon.Hackerspace -> Icons.Rounded.Terminal to R.color.purple
            // green: finances
            LocationIcon.Bank -> Icons.Rounded.AttachMoney to R.color.green
            LocationIcon.Atm -> Icons.Rounded.LocalAtm to R.color.green
            // red: health
            LocationIcon.Hospital -> Icons.Rounded.LocalHospital to R.color.red
            LocationIcon.Clinic -> Icons.Rounded.LocalHospital to R.color.red
            LocationIcon.Dentist -> Icons.Rounded.Dentistry to R.color.red
            LocationIcon.Physician -> Icons.Rounded.Stethoscope to R.color.red
            // light green: nature
            LocationIcon.Park -> Icons.Rounded.Park to R.color.lightgreen
            LocationIcon.Forest -> Icons.Rounded.Forest to R.color.lightgreen
            // brown: places of worship and remembrance
            LocationIcon.Monument -> Icons.Rounded.Monument to R.color.brown
            LocationIcon.Church -> Icons.Rounded.Church to R.color.brown
            LocationIcon.Mosque -> Icons.Rounded.Mosque to R.color.brown
            LocationIcon.Synagogue -> Icons.Rounded.Synagogue to R.color.brown
            LocationIcon.HinduTemple -> Icons.Rounded.TempleHindu to R.color.brown
            LocationIcon.BuddhistTemple -> Icons.Rounded.TempleBuddhist to R.color.brown
            LocationIcon.PlaceOfWorship -> Icons.Rounded.Candle to R.color.brown
            // bluegrey: public services
            LocationIcon.GovernmentBuilding -> Icons.Rounded.AccountBalance to R.color.bluegrey
            LocationIcon.Police -> Icons.Rounded.LocalPolice to R.color.bluegrey
            LocationIcon.FireDepartment -> Icons.Rounded.LocalFireDepartment to R.color.bluegrey
            LocationIcon.Courthouse -> Icons.Rounded.Gavel to R.color.bluegrey
            LocationIcon.PostOffice -> Icons.Rounded.LocalPostOffice to R.color.bluegrey
            LocationIcon.Library -> Icons.Rounded.Book to R.color.bluegrey
            LocationIcon.School -> Icons.Rounded.School to R.color.bluegrey
            LocationIcon.University -> Icons.Rounded.School to R.color.bluegrey
            LocationIcon.PublicBathroom -> Icons.Rounded.Wc to R.color.bluegrey

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

fun OpeningSchedule.isOpen(date: LocalDateTime = LocalDateTime.now()): Boolean = when (this) {
    is OpeningSchedule.TwentyFourSeven -> true
    is OpeningSchedule.Hours -> openingHours.any { it.isOpen(date) }
}

fun OpeningHours.isOpen(date: LocalDateTime = LocalDateTime.now()): Boolean {
    val startTime = date.with(TemporalAdjusters.previousOrSame(dayOfWeek)).with(startTime)
    val endTime = startTime.plus(duration)
    return date in startTime..<endTime
}