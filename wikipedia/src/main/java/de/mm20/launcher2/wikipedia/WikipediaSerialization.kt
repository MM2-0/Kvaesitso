package de.mm20.launcher2.wikipedia

import android.content.Context
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.search.data.Wikipedia
import org.json.JSONObject

class WikipediaSerializer : SearchableSerializer {
    override fun serialize(searchable: Searchable): String {
        searchable as Wikipedia
        val json = JSONObject()
        json.put("label", searchable.label)
        json.put("text", searchable.text)
        json.put("id", searchable.id)
        json.put("image", searchable.image)
        json.put("wikipedia_url", searchable.wikipediaUrl)
        return json.toString()
    }

    override val typePrefix: String
        get() = "wikipedia"
}

class WikipediaDeserializer(val context: Context) : SearchableDeserializer {
    override fun deserialize(serialized: String): Searchable? {
        val json = JSONObject(serialized)
        return Wikipedia(
            label = json.getString("label"),
            text = json.getString("text"),
            id = json.getLong("id"),
            image = json.optString("image"),
            wikipediaUrl = json.optString("wikipedia_url").takeIf { !it.isNullOrBlank() } ?: return null,
        )
    }
}