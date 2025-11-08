package de.mm20.launcher2.search

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import de.mm20.launcher2.base.R
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
import de.mm20.launcher2.search.location.PaymentMethod
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
        val (icon, bgColor) = when (icon) {
            // blue: transportation
            LocationIcon.Car -> R.drawable.directions_car_24px to R.color.blue
            LocationIcon.CarRental -> R.drawable.car_rental_24px to R.color.blue
            LocationIcon.CarRepair -> R.drawable.car_repair_24px to R.color.blue
            LocationIcon.CarWash -> R.drawable.local_car_wash_24px to R.color.blue
            LocationIcon.ChargingStation -> R.drawable.ev_station_24px to R.color.blue
            LocationIcon.GasStation -> R.drawable.local_gas_station_24px to R.color.blue
            LocationIcon.Parking -> R.drawable.local_parking_24px to R.color.blue
            LocationIcon.Bus -> R.drawable.directions_bus_24px to R.color.blue
            LocationIcon.Tram -> R.drawable.tram_24px to R.color.blue
            LocationIcon.Subway -> R.drawable.subway_24px to R.color.blue
            LocationIcon.Train -> R.drawable.train_24px to R.color.blue
            LocationIcon.CableCar -> R.drawable.cable_car_24px to R.color.blue
            LocationIcon.AerialTramway -> R.drawable.gondola_lift_24px to R.color.blue
            LocationIcon.Airport -> R.drawable.flight_24px to R.color.blue
            LocationIcon.Boat -> R.drawable.directions_boat_24px to R.color.blue
            LocationIcon.Moped -> R.drawable.moped_24px to R.color.blue
            LocationIcon.Bike -> R.drawable.directions_bike_24px to R.color.blue
            LocationIcon.Motorcycle -> R.drawable.motorcycle_24px to R.color.blue
            LocationIcon.ElectricScooter -> R.drawable.electric_scooter_24px to R.color.blue
            LocationIcon.Taxi -> R.drawable.local_taxi_24px to R.color.blue
            LocationIcon.GenericTransit -> R.drawable.train_24px to R.color.blue

            // cyan: art, culture, entertainment
            LocationIcon.ArtGallery -> R.drawable.palette_24px to R.color.cyan
            LocationIcon.Museum -> R.drawable.museum_24px to R.color.cyan
            LocationIcon.Theater -> R.drawable.theater_comedy_24px to R.color.cyan
            LocationIcon.MovieTheater -> R.drawable.theaters_24px to R.color.cyan
            LocationIcon.AmusementPark -> R.drawable.attractions_24px to R.color.cyan
            LocationIcon.NightClub -> R.drawable.nightlife_24px to R.color.cyan
            LocationIcon.ConcertHall -> R.drawable.music_note_24px to R.color.cyan
            LocationIcon.Stadium -> R.drawable.stadium_24px to R.color.cyan
            LocationIcon.Casino -> R.drawable.casino_24px to R.color.cyan
            LocationIcon.Circus -> R.drawable.festival_24px to R.color.cyan
            // pink: hotels
            LocationIcon.Hotel -> R.drawable.hotel_24px to R.color.pink
            // orange: food and drink
            LocationIcon.Restaurant -> R.drawable.restaurant_24px to R.color.orange
            LocationIcon.Cafe -> R.drawable.local_cafe_24px to R.color.orange
            LocationIcon.FastFood -> R.drawable.fastfood_24px to R.color.orange
            LocationIcon.Pizza -> R.drawable.local_pizza_24px to R.color.orange
            LocationIcon.Burger -> R.drawable.lunch_dining_24px to R.color.orange
            LocationIcon.Kebab -> R.drawable.kebab_dining_24px to R.color.orange
            LocationIcon.IceCream -> R.drawable.icecream_24px to R.color.orange
            LocationIcon.Ramen -> R.drawable.ramen_dining_24px to R.color.orange
            LocationIcon.Soup -> R.drawable.soup_kitchen_24px to R.color.orange
            LocationIcon.Bar -> R.drawable.local_bar_24px to R.color.orange
            LocationIcon.Brunch -> R.drawable.brunch_dining_24px to R.color.orange
            LocationIcon.Breakfast -> R.drawable.breakfast_dining_24px to R.color.orange
            LocationIcon.Pub -> R.drawable.sports_bar_24px to R.color.orange
            LocationIcon.JapaneseCuisine -> R.drawable.bento_24px to R.color.orange
            LocationIcon.AsianCuisine -> R.drawable.takeout_dining_24px to R.color.orange
            // indigo: business and shopping
            LocationIcon.Shopping -> R.drawable.shopping_bag_24px to R.color.indigo
            LocationIcon.Supermarket -> R.drawable.grocery_24px to R.color.indigo
            LocationIcon.Florist -> R.drawable.local_florist_24px to R.color.indigo
            LocationIcon.Kiosk -> R.drawable.newspaper_24px to R.color.indigo
            LocationIcon.FurnitureStore -> R.drawable.bed_24px to R.color.indigo
            LocationIcon.CellPhoneStore -> R.drawable.mobile_24px to R.color.indigo
            LocationIcon.BookStore -> R.drawable.book_24px to R.color.indigo
            LocationIcon.ClothingStore -> R.drawable.apparel_24px to R.color.indigo
            LocationIcon.ConvenienceStore -> R.drawable.local_convenience_store_24px to R.color.indigo
            LocationIcon.DiscountStore -> R.drawable.percent_discount_24px to R.color.indigo
            LocationIcon.JewelryStore -> R.drawable.diamond_24px to R.color.indigo
            LocationIcon.ShoppingMall -> R.drawable.local_mall_24px to R.color.indigo
            LocationIcon.LiquorStore -> R.drawable.liquor_24px to R.color.indigo
            LocationIcon.PetStore -> R.drawable.pets_24px to R.color.indigo
            LocationIcon.Bakery -> R.drawable.bakery_dining_24px to R.color.indigo
            LocationIcon.Optician -> R.drawable.eyeglasses_24px to R.color.indigo
            LocationIcon.Pharmacy -> R.drawable.local_pharmacy_24px to R.color.indigo
            LocationIcon.HairSalon -> R.drawable.content_cut_24px to R.color.indigo
            LocationIcon.Laundromat -> R.drawable.local_laundry_service_24px to R.color.indigo
            LocationIcon.Stationery -> R.drawable.attach_file_24px to R.color.indigo
            // purple: sports and recreation
            LocationIcon.Sports -> R.drawable.sports_24px to R.color.purple
            LocationIcon.FitnessCenter -> R.drawable.fitness_center_24px to R.color.purple
            LocationIcon.Soccer -> R.drawable.sports_soccer_24px to R.color.purple
            LocationIcon.Basketball -> R.drawable.sports_basketball_24px to R.color.purple
            LocationIcon.Golf -> R.drawable.sports_golf_24px to R.color.purple
            LocationIcon.Tennis -> R.drawable.sports_tennis_24px to R.color.purple
            LocationIcon.Baseball -> R.drawable.sports_baseball_24px to R.color.purple
            LocationIcon.Rugby -> R.drawable.sports_rugby_24px to R.color.purple
            LocationIcon.AmericanFootball -> R.drawable.sports_football_24px to R.color.purple
            LocationIcon.Hiking -> R.drawable.hiking_24px to R.color.purple
            LocationIcon.Swimming -> R.drawable.pool_24px to R.color.purple
            LocationIcon.Surfing -> R.drawable.surfing_24px to R.color.purple
            LocationIcon.Motorsports -> R.drawable.sports_motorsports_24px to R.color.purple
            LocationIcon.Handball -> R.drawable.sports_handball_24px to R.color.purple
            LocationIcon.Volleyball -> R.drawable.sports_volleyball_24px to R.color.purple
            LocationIcon.Skiing -> R.drawable.downhill_skiing_24px to R.color.purple
            LocationIcon.Kayaking -> R.drawable.kayaking_24px to R.color.purple
            LocationIcon.Skateboarding -> R.drawable.skateboarding_24px to R.color.purple
            LocationIcon.Cricket -> R.drawable.sports_cricket_24px to R.color.purple
            //LocationIcon.Climbing -> Icons.Rounded.Climbing to R.color.purple
            LocationIcon.MartialArts -> R.drawable.sports_martial_arts_24px to R.color.purple
            LocationIcon.NordicWalking -> R.drawable.nordic_walking_24px to R.color.purple
            LocationIcon.Paragliding -> R.drawable.paragliding_24px to R.color.purple
            LocationIcon.Gymnastics -> R.drawable.sports_gymnastics_24px to R.color.purple
            LocationIcon.Snowboarding -> R.drawable.snowboarding_24px to R.color.purple
            LocationIcon.Hockey -> R.drawable.sports_hockey_24px to R.color.purple
            LocationIcon.Hackerspace -> R.drawable.terminal_24px to R.color.purple
            // green: finances
            LocationIcon.Bank -> R.drawable.universal_currency_alt_24px to R.color.green
            LocationIcon.Atm -> R.drawable.credit_card_24px to R.color.green
            // red: health
            LocationIcon.Hospital -> R.drawable.emergency_24px to R.color.red
            LocationIcon.Clinic -> R.drawable.health_cross_24px to R.color.red
            LocationIcon.Dentist -> R.drawable.dentistry_24px to R.color.red
            LocationIcon.Physician -> R.drawable.stethoscope_24px to R.color.red
            // light green: nature
            LocationIcon.Park -> R.drawable.park_24px to R.color.lightgreen
            LocationIcon.Forest -> R.drawable.forest_24px to R.color.lightgreen
            // brown: places of worship and remembrance
            LocationIcon.Monument -> R.drawable.chess_rook_24px to R.color.brown
            LocationIcon.Church -> R.drawable.church_24px to R.color.brown
            LocationIcon.Mosque -> R.drawable.mosque_24px to R.color.brown
            LocationIcon.Synagogue -> R.drawable.synagogue_24px to R.color.brown
            LocationIcon.HinduTemple -> R.drawable.temple_hindu_24px to R.color.brown
            LocationIcon.BuddhistTemple -> R.drawable.temple_buddhist_24px to R.color.brown
            LocationIcon.PlaceOfWorship -> R.drawable.candle_24px to R.color.brown
            // bluegrey: public services
            LocationIcon.GovernmentBuilding -> R.drawable.account_balance_24px to R.color.bluegrey
            LocationIcon.Police -> R.drawable.local_police_24px to R.color.bluegrey
            LocationIcon.FireDepartment -> R.drawable.local_fire_department_24px to R.color.bluegrey
            LocationIcon.Courthouse -> R.drawable.gavel_24px to R.color.bluegrey
            LocationIcon.PostOffice -> R.drawable.local_post_office_24px to R.color.bluegrey
            LocationIcon.Library -> R.drawable.local_library_24px to R.color.bluegrey
            LocationIcon.School -> R.drawable.school_24px to R.color.bluegrey
            LocationIcon.University -> R.drawable.school_24px to R.color.bluegrey
            LocationIcon.PublicBathroom -> R.drawable.wc_24px to R.color.bluegrey

            else -> R.drawable.location_on_24px to R.color.bluegrey
        }
        return StaticLauncherIcon(
            foregroundLayer = VectorLayer(
                icon = icon,
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