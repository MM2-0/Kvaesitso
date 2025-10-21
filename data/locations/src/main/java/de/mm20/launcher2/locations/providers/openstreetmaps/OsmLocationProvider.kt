package de.mm20.launcher2.locations.providers.openstreetmaps

import android.content.Context
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.locations.providers.AndroidLocation
import de.mm20.launcher2.locations.providers.LocationProvider
import de.mm20.launcher2.openstreetmaps.R
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.search.UpdateResult
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first

internal class OsmLocationProvider(
    private val context: Context,
    private val settings: LocationSearchSettings
) : LocationProvider<Long> {

    private val overpassApi = OverpassApi()


    suspend fun update(
        id: Long
    ): UpdateResult<Location> {

        val baseUrl = settings.overpassUrl.first()?.takeIf { it.isNotBlank() }
            ?: LocationSearchSettings.DefaultOverpassUrl

        val response = try {
            overpassApi.interpreter(
                baseUrl,
                OverpassIdQuery(
                    id = id
                ),
            )
        } catch (e: Exception) {
            CrashReporter.logException(e)
            return UpdateResult.TemporarilyUnavailable(e)
        }

        val locations = OsmLocation.fromOverpassResponse(response, context)

        if (locations.isEmpty()) return UpdateResult.PermanentlyUnavailable()

        return UpdateResult.Success(locations.first().apply {
            updatedSelf = { update(id) }
        })
    }

    override suspend fun search(
        query: String,
        userLocation: AndroidLocation,
        searchRadiusMeters: Int,
        hideUncategorized: Boolean,
    ): List<Location> {
        if (query.length < 2) return emptyList()

        val baseUrl = settings.overpassUrl.first()?.takeIf { it.isNotBlank() }
            ?: LocationSearchSettings.DefaultOverpassUrl

        val response = try {
            overpassApi.interpreter(
                baseUrl,
                OverpassFuzzyRadiusQuery(
                    query = query,
                    tagGroups = delocalizeToQueryableTags(query),
                    radius = searchRadiusMeters,
                    latitude = userLocation.latitude,
                    longitude = userLocation.longitude
                )
            )
        } catch (e: Exception) {
            CrashReporter.logException(e)
            return emptyList()
        }

        val locations = OsmLocation.fromOverpassResponse(response, context)

        return locations.asSequence().filter {
            (!hideUncategorized || (it.category != null)) && it.distanceTo(userLocation) < searchRadiusMeters
        }.groupBy {
            it.label.lowercase()
        }.flatMap { (_, duplicates) ->
            // deduplicate results with same labels, if
            // - same category
            // - distance is less than 100m
            if (duplicates.size < 2) {
                duplicates
            } else {
                val luckyFirst = duplicates.first()
                duplicates
                    .drop(1)
                    .filter {
                        it.category != luckyFirst.category ||
                                it.distanceTo(luckyFirst) > 100.0
                    } + luckyFirst
            }
        }.sortedBy {
            it.distanceTo(userLocation)
        }.take(9)
    }

    private val poiCategories = mapOf(
        R.string.poi_category_restaurant to listOf("amenity=restaurant"),
        R.string.poi_category_fast_food to listOf("amenity=fast_food"),
        R.string.poi_category_bar to listOf("amenity=bar"),
        R.string.poi_category_cafe to listOf("amenity=cafe"),
        R.string.poi_category_hotel to listOf("tourism=hotel"),
        R.string.poi_category_supermarket to listOf("shop=supermarket"),
        R.string.poi_category_school to listOf("amenity=school"),
        R.string.poi_category_parking to listOf("amenity=parking"),
        R.string.poi_category_fuel to listOf("amenity=fuel"),
        R.string.poi_category_toilets to listOf("amenity=toilets"),
        R.string.poi_category_pharmacy to listOf("amenity=pharmacy"),
        R.string.poi_category_hospital to listOf("amenity=hospital"),
        R.string.poi_category_post_office to listOf("amenity=post_office"),
        R.string.poi_category_pub to listOf("amenity=pub"),
        R.string.poi_category_doctors to listOf("amenity=doctors"),
        R.string.poi_category_police to listOf("amenity=police"),
        R.string.poi_category_dentist to listOf("amenity=dentist"),
        R.string.poi_category_library to listOf("amenity=library"),
        R.string.poi_category_ice_cream to listOf("amenity=ice_cream"),
        R.string.poi_category_theater to listOf("amenity=theatre"),
        R.string.poi_category_cinema to listOf("amenity=cinema"),
        R.string.poi_category_nightclub to listOf("amenity=nightclub"),
        R.string.poi_category_clinic to listOf("amenity=clinic"),
        R.string.poi_category_university to listOf("amenity=university"),
        R.string.poi_category_clothes to listOf("shop=clothes"),
        R.string.poi_category_convenience to listOf("shop=convenience"),
        R.string.poi_category_hairdresser to listOf("shop=hairdresser"),
        R.string.poi_category_books to listOf("shop=books"),
        R.string.poi_category_bakery to listOf("shop=bakery"),
        R.string.poi_category_car_rental to listOf("amenity=car_rental"),
        R.string.poi_category_car_sharing to listOf("amenity=car_sharing"),
        R.string.poi_category_mobile_phone to listOf("shop=mobile_phone"),
        R.string.poi_category_furniture to listOf("shop=furniture"),
        R.string.poi_category_alcohol to listOf("shop=alcohol"),
        R.string.poi_category_florist to listOf("shop=florist"),
        R.string.poi_category_mall to listOf("shop=mall"),
        R.string.poi_category_optician to listOf("shop=optician"),
        R.string.poi_category_jewelry to listOf("shop=jewelry"),
        R.string.poi_category_laundry to listOf("amenity=laundry"),
        R.string.poi_category_bank to listOf("amenity=bank"),
        R.string.poi_category_soccer to listOf("leisure=pitch,sport=soccer"),
        R.string.poi_category_basketball to listOf("leisure=pitch,sport=basketball"),
        R.string.poi_category_tennis to listOf("leisure=pitch,sport=tennis"),
        R.string.poi_category_atm to listOf("amenity=atm"),
        R.string.poi_category_kiosk to listOf("shop=kiosk"),
        R.string.poi_category_museum to listOf("tourism=museum"),
        R.string.poi_category_fitness_center to listOf("leisure=fitness_centre"),
        R.string.poi_category_church to listOf("amenity=place_of_worship,religion=christian"),
        R.string.poi_category_mosque to listOf("amenity=place_of_worship,religion=muslim"),
        R.string.poi_category_buddhist_temple to listOf("amenity=place_of_worship,religion=buddhist"),
        R.string.poi_category_hindu_temple to listOf("amenity=place_of_worship,religion=hindu"),
        R.string.poi_category_synagogue to listOf("amenity=place_of_worship,religion=jewish"),
        R.string.poi_category_pizza_restaurant to listOf("amenity=restaurant,cuisine=pizza"),
        R.string.poi_category_burger_restaurant to listOf("amenity=restaurant,cuisine=burger"),
        R.string.poi_category_place_of_worship to listOf("amenity=place_of_worship"),
        R.string.poi_category_chinese_restaurant to listOf("amenity=restaurant,cuisine=chinese"),
        R.string.poi_category_japanese_restaurant to listOf("amenity=restaurant,cuisine=japanese"),
        R.string.poi_category_kebab_restaurant to listOf("amenity=restaurant,cuisine=kebab"),
        R.string.poi_category_asian_restaurant to listOf("amenity=restaurant,cuisine=asian"),
        R.string.poi_category_ramen_restaurant to listOf("amenity=restaurant,cuisine=ramen"),
        R.string.poi_category_soup_restaurant to listOf("amenity=restaurant,cuisine=soup"),
        R.string.poi_category_brunch_restaurant to listOf("amenity=restaurant,cuisine=brunch"),
        R.string.poi_category_car_wash to listOf("amenity=car_wash"),
        R.string.poi_category_charging_station to listOf("amenity=charging_station"),
        R.string.poi_category_motorcycle_rental to listOf("amenity=motorcycle_rental"),
        R.string.poi_category_gallery to listOf("tourism=gallery"),
        R.string.poi_category_amusement_park to listOf("tourism=theme_park"),
        R.string.poi_category_concert_hall to listOf("amenity=concert_hall"),
        R.string.poi_category_stadium to listOf("leisure=stadium"),
        R.string.poi_category_casino to listOf("amenity=casino"),
        R.string.poi_category_discount_store to listOf("shop=discount"),
        R.string.poi_category_pet to listOf("shop=pet"),
        R.string.poi_category_shopping to listOf("shop=mall"),
        R.string.poi_category_swimming to listOf("leisure=swimming_pool"),
        R.string.poi_category_martial_arts to listOf(
            "leisure=sports_centre",
            "leisure=sports_hall"
        ).map { "$it,sport=martial_arts" },
        R.string.poi_category_golf to listOf("leisure=golf_course"),
        R.string.poi_category_gymnastics to listOf(
            "leisure=sports_hall",
            "leisure=sports_centre"
        ).map { "$it,sport=gymnastics" },
        R.string.poi_category_ice_hockey to listOf(
            "leisure=sports_hall",
            "leisure=sports_centre"
        ).map { "$it,sport=ice_hockey" },
        R.string.poi_category_baseball to listOf("leisure=pitch,sport=baseball"),
        R.string.poi_category_american_football to listOf("leisure=pitch,sport=american_football"),
        R.string.poi_category_handball to listOf("leisure=pitch,sport=handball"),
        R.string.poi_category_volleyball to listOf("leisure=pitch,sport=volleyball"),
        R.string.poi_category_skiing to listOf("leisure=piste"),
        R.string.poi_category_cricket to listOf("leisure=pitch,sport=cricket"),
        R.string.poi_category_park to listOf("leisure=park"),
        R.string.poi_category_monument to listOf("historic=monument"),
        R.string.poi_category_government_building to listOf("building=government"),
        R.string.poi_category_fire_station to listOf("amenity=fire_station"),
        R.string.poi_category_courthouse to listOf("amenity=courthouse"),
        R.string.poi_category_townhall to listOf("amenity=townhall"),
        R.string.poi_category_stationery to listOf("shop=stationery"),
        R.string.poi_category_climbing_gym to listOf(
            "leisure=sports_hall",
            "leisure=sports_centre"
        ).map { "$it,sport=climbing" },
        R.string.poi_category_hackerspace to listOf("leisure=hackerspace")
    ).mapKeys { context.getString(it.key) }

    private fun delocalizeToQueryableTags(localizedQuery: String): List<String> =
        poiCategories.flatMap { (string, tags) ->
            val score = ResultScore(
                localizedQuery,
                primaryFields = listOf(string)
            )
            if (score.score > 0.8f) tags else persistentListOf()
        }
}
