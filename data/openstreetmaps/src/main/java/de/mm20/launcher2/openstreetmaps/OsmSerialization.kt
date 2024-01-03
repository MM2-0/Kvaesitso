package de.mm20.launcher2.openstreetmaps

import de.mm20.launcher2.coroutines.deferred
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.search.LocationCategory
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class OsmLocationSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as OsmLocation
        return jsonObjectOf(
            "id" to searchable.id,
            "lat" to searchable.latitude,
            "lon" to searchable.longitude,
            "category" to searchable.category?.name,
            "label" to searchable.label,
        ).toString()
    }

    override val typePrefix: String
        get() = "osmlocation"
}

internal class OsmLocationDeserializer(
    private val osmRepository: OsmRepository,
) : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable {
        val json = JSONObject(serialized)
        val id = json.getLong("id")
        return OsmLocation(
            id = id,
            latitude = json.getDouble("lat"),
            longitude = json.getDouble("lon"),
            category = json.getString("category").runCatching { LocationCategory.valueOf(this) }.getOrNull(),
            label = json.getString("label"),
            street = null,
            houseNumber = null,
            openingSchedule = null,
            websiteUrl = null,
            phoneNumber = null,
            updatedSelf = deferred {
                osmRepository.get(id).firstOrNull()
            }
        )
    }
}