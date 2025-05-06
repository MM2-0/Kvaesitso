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
import de.mm20.launcher2.search.asUpdateResult
import de.mm20.launcher2.search.location.Address
import de.mm20.launcher2.search.location.Attribution
import de.mm20.launcher2.search.location.Departure
import de.mm20.launcher2.search.location.LocationIcon
import de.mm20.launcher2.search.location.OpeningSchedule
import de.mm20.launcher2.search.location.PaymentMethod
import de.mm20.launcher2.serialization.Json
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable

@Serializable
internal data class SerializedLocation(
    val id: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val icon: LocationIcon? = null,
    val category: String? = null,
    val label: String? = null,
    val address: Address? = null,
    val websiteUrl: String? = null,
    val phoneNumber: String? = null,
    val emailAddress: String? = null,
    val userRating: Float? = null,
    val userRatingCount: Int? = null,
    val openingSchedule: OpeningSchedule? = null,
    val timestamp: Long? = null,
    val departures: List<Departure>? = null,
    val fixMeUrl: String? = null,
    val attribution: Attribution? = null,
    val acceptedPaymentMethods: Map<PaymentMethod, Boolean>? = null,
    val authority: String? = null,
    val storageStrategy: StorageStrategy? = null,
)

internal class OsmLocationSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as OsmLocation
        return Json.Lenient.encodeToString(
            SerializedLocation(
                id = searchable.id.toString(),
                lat = searchable.latitude,
                lon = searchable.longitude,
                icon = searchable.icon,
                category = searchable.category,
                label = searchable.label,
                address = searchable.address,
                websiteUrl = searchable.websiteUrl,
                phoneNumber = searchable.phoneNumber,
                emailAddress = searchable.emailAddress,
                userRating = searchable.userRating,
                userRatingCount = searchable.userRatingCount,
                openingSchedule = searchable.openingSchedule,
                timestamp = searchable.timestamp,
                departures = searchable.departures,
                fixMeUrl = searchable.fixMeUrl,
                acceptedPaymentMethods = searchable.acceptedPaymentMethods
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
            icon = json.icon,
            category = json.category,
            label = json.label ?: return null,
            address = json.address,
            websiteUrl = json.websiteUrl,
            phoneNumber = json.phoneNumber,
            emailAddress = json.emailAddress,
            userRating = json.userRating,
            openingSchedule = json.openingSchedule,
            timestamp = json.timestamp ?: return null,
            acceptedPaymentMethods = json.acceptedPaymentMethods,
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
                        icon = searchable.icon,
                        category = searchable.category,
                        label = searchable.label,
                        address = searchable.address,
                        websiteUrl = searchable.websiteUrl,
                        phoneNumber = searchable.phoneNumber,
                        emailAddress = searchable.emailAddress,
                        userRating = searchable.userRating,
                        userRatingCount = searchable.userRatingCount,
                        attribution = searchable.attribution,
                        openingSchedule = searchable.openingSchedule,
                        timestamp = searchable.timestamp,
                        departures = searchable.departures,
                        acceptedPaymentMethods = searchable.acceptedPaymentMethods,
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
                PluginLocationProvider(context, authority).get(id).getOrNull()
            }

            else -> {
                val timestamp = json.timestamp ?: 0
                PluginLocation(
                    id = id,
                    latitude = json.lat ?: return null,
                    longitude = json.lon ?: return null,
                    icon = json.icon,
                    category = json.category,
                    label = json.label ?: return null,
                    address = json.address,
                    websiteUrl = json.websiteUrl,
                    phoneNumber = json.phoneNumber,
                    emailAddress = json.emailAddress,
                    userRating = json.userRating,
                    userRatingCount = json.userRatingCount,
                    openingSchedule = json.openingSchedule,
                    timestamp = timestamp,
                    departures = json.departures,
                    fixMeUrl = json.fixMeUrl,
                    attribution = json.attribution,
                    acceptedPaymentMethods = json.acceptedPaymentMethods,
                    authority = authority,
                    storageStrategy = strategy,
                    updatedSelf = {
                        if (it !is PluginLocation) UpdateResult.TemporarilyUnavailable()
                        else PluginLocationProvider(context, authority).refresh(it, timestamp).asUpdateResult()
                    }
                )
            }
        }
    }
}
