package de.mm20.launcher2.wikipedia

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
        return json.toString()
    }

    override val typePrefix: String
        get() = "wikipedia"
}

class WikipediaDeserializer : SearchableDeserializer {
    override fun deserialize(serialized: String): Searchable? {
        val json = JSONObject(serialized)
        return Wikipedia(
            label = json.getString("label"),
            text = json.getString("text"),
            id = json.getLong("id"),
            image = json.optString("image")
        )
    }
}