package de.mm20.launcher2.openstreetmaps

import android.util.Log
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class OsmLocationSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as OsmLocation
        return jsonObjectOf(
            "id" to searchable.id
        ).toString()
    }

    override val typePrefix: String
        get() = "osmlocation"
}

class OsmLocationDeserializer : SearchableDeserializer {

    private val retrofit = Retrofit.Builder()
        .client(OkHttpClient())
        .baseUrl("https://overpass-api.de/") // TODO make configurable (maybe)
        .addConverterFactory(OverpassQueryConverterFactory())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val overpassService = retrofit.create(OverpassApi::class.java)

    override suspend fun deserialize(serialized: String): SavableSearchable? =
        serialized.runCatching {
            OsmLocation.fromOverpassResponse(
                overpassService.search(
                    OverpassIdQuery(
                        JSONObject(
                            serialized
                        ).getLong("id")
                    )
                )
            ).firstOrNull()
        }.onFailure {
            Log.e("OsmLocationDeserializer", "Request failed", it)
        }.getOrNull()


}