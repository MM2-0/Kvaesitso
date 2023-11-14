package de.mm20.launcher2.openstreetmaps

import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.search.LocationCategory
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
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

class OsmLocationDeserializer : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable {
        val json = JSONObject(serialized)
        return OsmLocation(
            id = json.getLong("id"),
            latitude = json.getDouble("lat"),
            longitude = json.getDouble("lon"),
            category = json.getString("category").runCatching { LocationCategory.valueOf(this) }.getOrNull(),
            label = json.getString("label"),
            street = null,
            houseNumber = null,
            openingHours = null,
            websiteUrl = null,
        )
    }
}