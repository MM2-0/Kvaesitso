package de.mm20.launcher2.locations.providers.openstreetmaps

import android.content.Context
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.locations.providers.AndroidLocation
import de.mm20.launcher2.locations.providers.LocationProvider
import de.mm20.launcher2.openstreetmaps.R
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.search.UpdateResult
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.UnknownHostException

private val Scope = CoroutineScope(Job() + Dispatchers.IO)
private val HttpClient = OkHttpClient()

internal class OsmLocationProvider(
    private val context: Context,
    settings: LocationSearchSettings
) : LocationProvider<Long> {

    private val overpassApi = settings.overpassUrl.map {
        try {
            Retrofit.Builder()
                .client(HttpClient)
                .baseUrl(it?.takeIf { it.isNotBlank() }
                    ?: LocationSearchSettings.DefaultOverpassUrl)
                .addConverterFactory(OverpassQueryConverterFactory())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OverpassApi::class.java)
        } catch (e: Exception) {
            CrashReporter.logException(e)
            null
        }
    }.stateIn(Scope, SharingStarted.Eagerly, null)


    suspend fun update(
        id: Long
    ): UpdateResult<Location> = overpassApi.first()?.runCatching {
        this.search(
            OverpassIdQuery(
                id = id
            )
        ).let {
            OsmLocation.fromOverpassResponse(it, context)
        }.first().apply {
            updatedSelf = { update(id) }
        }
    }?.fold(
        onSuccess = { UpdateResult.Success(it) },
        onFailure = {
            when (it) {
                is CancellationException, is UnknownHostException -> {
                    // network
                    UpdateResult.TemporarilyUnavailable(it)
                }

                is HttpException -> when (it.code()) {
                    in 400..499 -> UpdateResult.PermanentlyUnavailable(it)
                    else -> UpdateResult.TemporarilyUnavailable(it)
                }

                is NoSuchElementException -> {
                    // empty response
                    UpdateResult.PermanentlyUnavailable(it)
                }

                else -> {
                    if (it is Exception) {
                        CrashReporter.logException(it)
                    }
                    UpdateResult.TemporarilyUnavailable(it)
                }
            }
        }
    ) ?: let {
        Log.e("OsmProvider", "overpassApi was not initialized")
        UpdateResult.TemporarilyUnavailable()
    }

    override suspend fun search(
        query: String,
        userLocation: AndroidLocation,
        allowNetwork: Boolean,
        searchRadiusMeters: Int,
        hideUncategorized: Boolean,
    ): List<Location> {
        if (!allowNetwork || query.length < 2) {
            return emptyList()
        }

        withContext(Dispatchers.IO) {
            HttpClient.dispatcher.cancelAll()
        }

        return overpassApi.first()?.runCatching {
            search(
                OverpassFuzzyRadiusQuery(
                    query = query,
                    tagGroups = delocalizeToQueryableTags(query),
                    radius = searchRadiusMeters,
                    latitude = userLocation.latitude,
                    longitude = userLocation.longitude
                )
            )
        }?.onFailure {
            if (it !is HttpException && it !is CancellationException) {
                Log.e("OsmLocationProvider", "Failed to search for: $query", it)
            }
        }?.getOrNull()?.let {
            OsmLocation.fromOverpassResponse(it, context)
        }?.asSequence()?.filter {
            (!hideUncategorized || (it.category != null)) && it.distanceTo(userLocation) < searchRadiusMeters
        }?.groupBy {
            it.label.lowercase()
        }?.flatMap { (_, duplicates) ->
            // deduplicate results with same labels, if
            // - same category
            // - distance is less than 100m
            if (duplicates.size < 2) duplicates
            else {
                val luckyFirst = duplicates.first()
                duplicates
                    .drop(1)
                    .filter {
                        it.category != luckyFirst.category ||
                                it.distanceTo(luckyFirst) > 100.0
                    } + luckyFirst
            }
        }?.sortedBy {
            it.distanceTo(userLocation)
        }?.take(9)?.toImmutableList() ?: emptyList()
    }

    private val poiCategories = mapOf(
        R.string.poi_category_restaurant to "amenity=restaurant",
        R.string.poi_category_fast_food to "amenity=fast_food",
        R.string.poi_category_bar to "amenity=bar",
        R.string.poi_category_cafe to "amenity=cafe",
        R.string.poi_category_hotel to "tourism=hotel",
        R.string.poi_category_supermarket to "shop=supermarket",
        R.string.poi_category_school to "amenity=school",
        R.string.poi_category_parking to "amenity=parking",
        R.string.poi_category_fuel to "amenity=fuel",
        R.string.poi_category_toilets to "amenity=toilets",
        R.string.poi_category_pharmacy to "amenity=pharmacy",
        R.string.poi_category_hospital to "amenity=hospital",
        R.string.poi_category_post_office to "amenity=post_office",
        R.string.poi_category_pub to "amenity=pub",
        R.string.poi_category_doctors to "amenity=doctors",
        R.string.poi_category_police to "amenity=police",
        R.string.poi_category_dentist to "amenity=dentist",
        R.string.poi_category_library to "amenity=library",
        R.string.poi_category_ice_cream to "amenity=ice_cream",
        R.string.poi_category_theater to "amenity=theatre",
        R.string.poi_category_cinema to "amenity=cinema",
        R.string.poi_category_nightclub to "amenity=nightclub",
        R.string.poi_category_clinic to "amenity=clinic",
        R.string.poi_category_university to "amenity=university",
        R.string.poi_category_clothes to "shop=clothes",
        R.string.poi_category_convenience to "shop=convenience",
        R.string.poi_category_hairdresser to "shop=hairdresser",
        R.string.poi_category_books to "shop=books",
        R.string.poi_category_bakery to "shop=bakery",
        R.string.poi_category_car_rental to "amenity=car_rental",
        R.string.poi_category_car_sharing to "amenity=car_sharing",
        R.string.poi_category_mobile_phone to "shop=mobile_phone",
        R.string.poi_category_furniture to "shop=furniture",
        R.string.poi_category_alcohol to "shop=alcohol",
        R.string.poi_category_florist to "shop=florist",
        R.string.poi_category_mall to "shop=mall",
        R.string.poi_category_optician to "shop=optician",
        R.string.poi_category_jewelry to "shop=jewelry",
        R.string.poi_category_laundry to "amenity=laundry",
        R.string.poi_category_bank to "amenity=bank",
        R.string.poi_category_soccer to "leisure=pitch,sport=soccer",
        R.string.poi_category_basketball to "leisure=pitch,sport=basketball",
        R.string.poi_category_tennis to "leisure=pitch,sport=tennis",
        R.string.poi_category_atm to "amenity=atm",
        R.string.poi_category_kiosk to "shop=kiosk",
        R.string.poi_category_museum to "tourism=museum",
        R.string.poi_category_fitness_center to "leisure=fitness_centre",
        R.string.poi_category_church to "amenity=place_of_worship,religion=christian",
        R.string.poi_category_mosque to "amenity=place_of_worship,religion=muslim",
        R.string.poi_category_buddhist_temple to "amenity=place_of_worship,religion=buddhist",
        R.string.poi_category_hindu_temple to "amenity=place_of_worship,religion=hindu",
        R.string.poi_category_synagogue to "amenity=place_of_worship,religion=jewish",
        R.string.poi_category_pizza_restaurant to "amenity=restaurant,cuisine=pizza",
        R.string.poi_category_burger_restaurant to "amenity=restaurant,cuisine=burger",
        R.string.poi_category_place_of_worship to "amenity=place_of_worship",
        R.string.poi_category_chinese_restaurant to "amenity=restaurant,cuisine=chinese",
        R.string.poi_category_japanese_restaurant to "amenity=restaurant,cuisine=japanese",
        R.string.poi_category_kebab_restaurant to "amenity=restaurant,cuisine=kebab",
        R.string.poi_category_asian_restaurant to "amenity=restaurant,cuisine=asian",
        R.string.poi_category_ramen_restaurant to "amenity=restaurant,cuisine=ramen",
        R.string.poi_category_soup_restaurant to "amenity=restaurant,cuisine=soup",
        R.string.poi_category_brunch_restaurant to "amenity=restaurant,cuisine=brunch",
        R.string.poi_category_car_wash to "amenity=car_wash",
        R.string.poi_category_charging_station to "amenity=charging_station",
        R.string.poi_category_motorcycle_rental to "amenity=motorcycle_rental",
        R.string.poi_category_gallery to "tourism=gallery",
        R.string.poi_category_amusement_park to "tourism=theme_park",
        R.string.poi_category_concert_hall to "amenity=concert_hall",
        R.string.poi_category_stadium to "leisure=stadium",
        R.string.poi_category_casino to "amenity=casino",
        R.string.poi_category_discount_store to "shop=discount",
        R.string.poi_category_pet to "shop=pet",
        R.string.poi_category_shopping to "shop=mall",
        R.string.poi_category_swimming to "leisure=swimming_pool",
        R.string.poi_category_martial_arts to "leisure=sports_centre,sport=martial_arts",
        R.string.poi_category_golf to "leisure=golf_course",
        R.string.poi_category_gymnastics to "leisure=sports_hall,sport=gymnastics",
        R.string.poi_category_ice_hockey to "leisure=sports_centre,sport=ice_hockey",
        R.string.poi_category_baseball to "leisure=pitch,sport=baseball",
        R.string.poi_category_american_football to "leisure=pitch,sport=american_football",
        R.string.poi_category_handball to "leisure=pitch,sport=handball",
        R.string.poi_category_volleyball to "leisure=pitch,sport=volleyball",
        R.string.poi_category_skiing to "leisure=piste",
        R.string.poi_category_cricket to "leisure=pitch,sport=cricket",
        R.string.poi_category_park to "leisure=park",
        R.string.poi_category_monument to "historic=monument",
        R.string.poi_category_government_building to "building=government",
        R.string.poi_category_fire_station to "amenity=fire_station",
        R.string.poi_category_courthouse to "amenity=courthouse",
        R.string.poi_category_townhall to "amenity=townhall"
    ).mapKeys { context.getString(it.key) }

    private fun delocalizeToQueryableTags(localizedQuery: String): List<String> =
        poiCategories.mapNotNull { (string, tags) ->
            val score = ResultScore(
                localizedQuery,
                primaryFields = listOf(string)
            )
            if (score.score > 0.8f) tags else null
        }
}
