package de.mm20.launcher2.locations

import android.content.Context
import de.mm20.launcher2.locations.providers.PluginLocation
import de.mm20.launcher2.locations.providers.PluginLocationProvider
import de.mm20.launcher2.locations.providers.openstreetmaps.OsmLocation
import de.mm20.launcher2.locations.providers.openstreetmaps.OsmLocationProvider
import de.mm20.launcher2.plugin.PluginRepository
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.search.location.Departure
import de.mm20.launcher2.search.location.LocationCategory
import de.mm20.launcher2.search.location.OpeningHours
import de.mm20.launcher2.search.location.OpeningSchedule
import de.mm20.launcher2.serialization.Json
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

@Serializable
internal data class SerializedLocation(
    val id: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val category: LocationCategory? = null,
    val label: String? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val websiteUrl: String? = null,
    val phoneNumber: String? = null,
    val userRating: Float? = null,
    val openingSchedule: OpeningSchedule? = null,
    val timestamp: Long? = null,
    val departures: List<Departure>? = null,
    val fixMeUrl: String? = null,
    val authority: String? = null,
    val storageStrategy: StorageStrategy? = null,
)

internal fun getOpeningSchedule(json: JSONObject): OpeningSchedule {
    fun getOpeningHours(array: JSONArray): ImmutableList<OpeningHours> {
        val hours = mutableListOf<OpeningHours>()

        for (i in 0 until array.length()) {
            val json = array.getJSONObject(i)
            val dayOfWeek =
                DayOfWeek.of(json.optInt("day").takeIf { it in 1..7 } ?: continue)
            val openingTimeMillis =
                json.optLong("openingTime", -1).takeIf { it >= 0 } ?: continue
            val durationMillis = json.optLong("duration", -1).takeIf { it >= 0 } ?: continue

            hours.add(
                OpeningHours(
                    dayOfWeek = dayOfWeek,
                    startTime = LocalTime.ofSecondOfDay(openingTimeMillis / 1000L),
                    duration = Duration.ofMillis(durationMillis)
                )
            )
        }

        return hours.toPersistentList()
    }

    return OpeningSchedule(
        isTwentyFourSeven = json.optBoolean("isTwentyFourSeven"),
        openingHours = json.optJSONArray("openingHours")?.let {
            getOpeningHours(it)
        } ?: persistentListOf()
    )
}

internal class OsmLocationSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as OsmLocation
        return Json.Lenient.encodeToString(
            SerializedLocation(
                id = searchable.id.toString(),
                lat = searchable.latitude,
                lon = searchable.longitude,
                category = searchable.category,
                label = searchable.label,
                street = searchable.street,
                houseNumber = searchable.houseNumber,
                websiteUrl = searchable.websiteUrl,
                phoneNumber = searchable.phoneNumber,
                userRating = searchable.userRating,
                openingSchedule = searchable.openingSchedule,
                timestamp = searchable.timestamp,
                departures = searchable.departures,
                fixMeUrl = searchable.fixMeUrl,
            )
        )
    }

    override val typePrefix: String
        get() = "osmlocation"
}

internal class OsmLocationDeserializer(
    private val osmProvider: OsmLocationProvider,
) : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable? {
        val json = Json.Lenient.decodeFromString<SerializedLocation>(serialized)
        val id = json.id?.toLongOrNull() ?: return null

        return OsmLocation(
            id = id,
            latitude = json.lat ?: return null,
            longitude = json.lon ?: return null,
            category = json.category,
            label = json.label ?: return null,
            street = json.street,
            houseNumber = json.houseNumber,
            websiteUrl = json.websiteUrl,
            phoneNumber = json.phoneNumber,
            userRating = json.userRating,
            openingSchedule = json.openingSchedule,
            timestamp = json.timestamp ?: return null,
            updatedSelf = {
                osmProvider.update(id)
            }
        )
    }
}

internal class PluginLocationSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as PluginLocation
        return when (searchable.storageStrategy) {
            StorageStrategy.StoreReference -> Json.Lenient.encodeToString(
                SerializedLocation(
                    id = searchable.id,
                    authority = searchable.authority,
                    storageStrategy = StorageStrategy.StoreReference,
                )
            )

            else -> {
                Json.Lenient.encodeToString(
                    SerializedLocation(
                        id = searchable.id,
                        lat = searchable.latitude,
                        lon = searchable.longitude,
                        category = searchable.category,
                        label = searchable.label,
                        street = searchable.street,
                        houseNumber = searchable.houseNumber,
                        websiteUrl = searchable.websiteUrl,
                        phoneNumber = searchable.phoneNumber,
                        userRating = searchable.userRating,
                        openingSchedule = searchable.openingSchedule,
                        timestamp = searchable.timestamp,
                        departures = searchable.departures,
                        fixMeUrl = searchable.fixMeUrl,
                        authority = searchable.authority,
                        storageStrategy = searchable.storageStrategy,
                    )
                )
            }
        }
    }

    override val typePrefix: String
        get() = PluginLocation.DOMAIN
}

internal class PluginLocationDeserializer(
    private val context: Context,
    private val pluginRepository: PluginRepository,
) : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable? {
        val json = Json.Lenient.decodeFromString<SerializedLocation>(serialized)
        val authority = json.authority ?: return null
        val id = json.id ?: return null
        val strategy = json.storageStrategy ?: StorageStrategy.StoreCopy

        val plugin = pluginRepository.get(authority).firstOrNull() ?: return null
        if (!plugin.enabled) return null

        return when (strategy) {
            StorageStrategy.StoreReference -> {
                val result = PluginLocationProvider(context, authority).update(id)
                if (result is UpdateResult.Success) {
                    result.result
                } else {
                    null
                }
            }

            else -> {
                PluginLocation(
                    id = id,
                    latitude = json.lat ?: return null,
                    longitude = json.lon ?: return null,
                    category = json.category,
                    label = json.label ?: return null,
                    street = json.street,
                    houseNumber = json.houseNumber,
                    websiteUrl = json.websiteUrl,
                    phoneNumber = json.phoneNumber,
                    userRating = json.userRating,
                    openingSchedule = json.openingSchedule,
                    timestamp = json.timestamp ?: return null,
                    departures = json.departures,
                    fixMeUrl = json.fixMeUrl,
                    authority = authority,
                    storageStrategy = strategy,
                    updatedSelf = if (json.storageStrategy == StorageStrategy.Deferred) {
                        { PluginLocationProvider(context, authority).update(id) }
                    } else null
                )
            }
        }
    }
}
