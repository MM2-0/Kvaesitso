package de.mm20.launcher2.wikipedia

import android.content.Context
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.data.Wikipedia
import org.json.JSONObject

class WikipediaSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as Wikipedia
        val json = JSONObject()
        json.put("label", searchable.label)
        json.put("text", searchable.text)
        json.put("id", searchable.id)
        json.put("image", searchable.image)
        json.put("wikipedia_url", searchable.wikipediaUrl)
        json.put("url", searchable.url)
        return json.toString()
    }

    override val typePrefix: String
        get() = "wikipedia"
}

class WikipediaDeserializer(val context: Context) : SearchableDeserializer {
    override fun deserialize(serialized: String): SavableSearchable? {
        val json = JSONObject(serialized)
        val wikipediaUrl = json.optString("wikipedia_url").takeIf { !it.isNullOrBlank() } ?: return null
        val id = json.getLong("id")
        return Wikipedia(
            label = json.getString("label"),
            text = json.getString("text"),
            id = id,
            image = json.optString("image"),
            url = json.optString("url").takeIf { !it.isNullOrBlank() } ?: "${wikipediaUrl.padEnd(1, '/')}wiki?curid=$id",
            wikipediaUrl = wikipediaUrl
        )
    }
}