package de.mm20.launcher2.openstreetmaps

import android.util.Log
import de.mm20.launcher2.coroutines.deferred
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.search.LocationCategory
import de.mm20.launcher2.search.OpeningHours
import de.mm20.launcher2.search.OpeningSchedule
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.UpdateResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import java.lang.IllegalStateException
import java.net.UnknownHostException
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import kotlin.coroutines.cancellation.CancellationException

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
            "updatedAt" to searchable.updatedAt,
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
        val updatedAt = json.optLong("updatedAt")

        // Don't refresh data if it's less than an hour old
        val isOutdated = updatedAt + 60 * 60 * 1000L < System.currentTimeMillis()

        return OsmLocation(
            id = id,
            latitude = json.getDouble("lat"),
            longitude = json.getDouble("lon"),
            category = json.getString("category").runCatching { LocationCategory.valueOf(this) }
                .getOrNull(),
            label = json.getString("label"),
            street = json.optString("street"),
            houseNumber = json.optString("houseNumber"),
            openingSchedule = null,
            websiteUrl = json.optString("websiteUrl"),
            phoneNumber = json.optString("phoneNumber"),
            updatedAt = json.optLong("updatedAt"),
            updatedSelf = if (isOutdated) deferred {
                try {
                    UpdateResult.Success(osmRepository.get(id).first())
                } catch (ce: CancellationException) {
                    UpdateResult.TemporarilyUnavailable(ce)
                } catch (ue: UnknownHostException) {
                    UpdateResult.TemporarilyUnavailable(ue)
                } catch (nse: NoSuchElementException) {
                    // empty response
                    UpdateResult.PermanentlyUnavailable(nse)
                } catch (he: HttpException) {
                    when (he.code()) {
                        in 400..499 -> UpdateResult.PermanentlyUnavailable(he)
                        else -> UpdateResult.TemporarilyUnavailable(he)
                    }
                } catch (ise: IllegalStateException) {
                    Log.e("OsmLocationDeserializer", "Deferred update unexpected failure", ise)
                    UpdateResult.TemporarilyUnavailable(ise)
                } catch (e: Exception) {
                    CrashReporter.logException(e)
                    UpdateResult.TemporarilyUnavailable(e)
                }
            } else null
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
            val dayOfWeek = DayOfWeek.of(json.optInt("day").takeIf { it in 1..7 } ?: continue)
            val openingTimeMillis = json.optLong("openingTime", -1).takeIf { it >= 0 } ?: continue
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