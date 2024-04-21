package de.mm20.launcher2.locations

import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.locations.providers.openstreetmaps.OsmLocation
import de.mm20.launcher2.locations.providers.openstreetmaps.OsmLocationProvider
import de.mm20.launcher2.search.LocationCategory
import de.mm20.launcher2.search.OpeningHours
import de.mm20.launcher2.search.OpeningSchedule
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

class OsmLocationSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as OsmLocation
        return jsonObjectOf(
            "id" to searchable.id,
            "lat" to searchable.latitude,
            "lon" to searchable.longitude,
            "category" to searchable.category?.name,
            "label" to searchable.label,
            "street" to searchable.street,
            "houseNumber" to searchable.houseNumber,
            "websiteUrl" to searchable.websiteUrl,
            "phoneNumber" to searchable.phoneNumber,
            "openingSchedule" to searchable.openingSchedule?.let {
                jsonObjectOf(
                    "isTwentyFourSeven" to it.isTwentyFourSeven,
                    "openingHours" to JSONArray(it.openingHours.map {
                        jsonObjectOf(
                            "day" to it.dayOfWeek.value,
                            "openingTime" to it.startTime.toSecondOfDay() * 1000L,
                            "duration" to it.duration.toMillis(),
                        )
                    })
                )
            },
            "timestamp" to searchable.timestamp,
        ).toString()
    }

    override val typePrefix: String
        get() = "osmlocation"
}

internal class OsmLocationDeserializer(
    private val osmRepository: OsmLocationProvider,
) : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable {
        val json = JSONObject(serialized)
        val id = json.getLong("id")

        return OsmLocation(
            id = id,
            latitude = json.getDouble("lat"),
            longitude = json.getDouble("lon"),
            category = json.getString("category").runCatching { LocationCategory.valueOf(this) }
                .getOrNull(),
            label = json.getString("label"),
            street = json.optString("street").takeIf { it.isNotBlank() },
            houseNumber = json.optString("houseNumber").takeIf { it.isNotBlank() },
            openingSchedule = json.optJSONObject("openingSchedule")?.let { getOpeningSchedule(it) },
            websiteUrl = json.optString("websiteUrl").takeIf { it.isNotBlank() },
            phoneNumber = json.optString("phoneNumber").takeIf { it.isNotBlank() },
            timestamp = json.optLong("timestamp"),
            updatedSelf = { osmRepository.update(id) }
        )
    }

    private fun getOpeningSchedule(json: JSONObject): OpeningSchedule {
        return OpeningSchedule(
            isTwentyFourSeven = json.optBoolean("isTwentyFourSeven"),
            openingHours = json.optJSONArray("openingHours")?.let {
                getOpeningHours(it)
            } ?: persistentListOf()
        )
    }

    private fun getOpeningHours(array: JSONArray): ImmutableList<OpeningHours> {
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
}