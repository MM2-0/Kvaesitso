package de.mm20.launcher2.locations

import android.content.Context
import android.graphics.Color
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.locations.providers.PluginLocation
import de.mm20.launcher2.locations.providers.PluginLocationProvider
import de.mm20.launcher2.locations.providers.openstreetmaps.OsmLocation
import de.mm20.launcher2.locations.providers.openstreetmaps.OsmLocationProvider
import de.mm20.launcher2.plugin.PluginRepository
import de.mm20.launcher2.search.Departure
import de.mm20.launcher2.search.LineType
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.LocationCategory
import de.mm20.launcher2.search.OpeningHours
import de.mm20.launcher2.search.OpeningSchedule
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import kotlin.time.Duration.Companion.days

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

internal abstract class BaseLocationSerializer : SearchableSerializer {
    protected fun serializeBase(searchable: SavableSearchable): JSONObject {
        searchable as Location
        return jsonObjectOf(
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
        )
    }
}

internal class OsmLocationSerializer : BaseLocationSerializer() {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as OsmLocation
        return super.serializeBase(searchable).apply {
            put("id", searchable.id)
            put("timestamp", searchable.timestamp)
        }.toString()
    }

    override val typePrefix: String
        get() = "osmlocation"
}

internal class OsmLocationDeserializer(
    private val osmProvider: OsmLocationProvider,
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
            updatedSelf = { osmProvider.update(id) }
        )
    }
}

internal class PluginLocationSerializer : BaseLocationSerializer() {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as PluginLocation
        return super.serializeBase(searchable).apply {
            put("id", searchable.id)
            put("authority", searchable.authority)
            put("userRating", searchable.userRating)
            put("timestamp", searchable.timestamp)
            put("fixMeUrl", searchable.fixMeUrl)
            put("departures", searchable.departures?.let {
                buildJsonArray {
                    it.map {
                        addJsonObject {
                            put("time", it.time.toSecondOfDay() * 1000L)
                            it.delay?.let { put("delay", it) }
                            put("line", it.line)
                            it.lastStop?.let { put("lastStop", it) }
                            it.type?.let { put("type", it.name) }
                        }
                    }
                }
            })
        }.toString()
    }

    override val typePrefix: String
        get() = PluginLocation.DOMAIN
}

internal class PluginLocationDeserializer(
    private val context: Context,
    private val pluginRepository: PluginRepository,
) : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable? {
        val json = JSONObject(serialized)
        val authority = json.getString("authority")

        val plugin = pluginRepository.get(authority).firstOrNull() ?: return null
        if (!plugin.enabled) return null

        val id = json.getString("id")
        val timestamp = json.getLong("timestamp")

        return PluginLocation(
            id = id,
            timestamp = timestamp,
            authority = authority,
            fixMeUrl = json.optString("fixMeUrl").takeIf { it.isNotBlank() },
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
            updatedSelf = { PluginLocationProvider(context, authority).update(id) },
            userRating = json.optDouble("userRating").takeUnless { it.isNaN() }?.toFloat(),
            departures = if (System.currentTimeMillis() - timestamp > 1.days.inWholeMilliseconds) null else {
                val arr = json.optJSONArray("departures")
                (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }
                    .mapNotNull {
                        Departure(
                            time = it.getLong("time").let { LocalTime.ofSecondOfDay(it / 1000L) },
                            delay = it.optLong("delay").takeIf { it > 0 }
                                ?.let { Duration.ofMillis(it) },
                            line = it.getString("line"),
                            lastStop = it.optString("lastStop").takeIf { it.isNotBlank() },
                            type = it.optString("type").takeIf { it.isNotBlank() }
                                ?.runCatching { LineType.valueOf(this.uppercase()) }?.getOrNull(),
                            lineColor = it.runCatching { getInt("lineColor") }
                                .getOrNull()
                                ?.let { Color.valueOf(it) }
                        )
                    }

            }
        )
    }
}
