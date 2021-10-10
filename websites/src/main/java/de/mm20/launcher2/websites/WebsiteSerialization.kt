package de.mm20.launcher2.websites

import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.search.data.Website
import org.json.JSONObject

class WebsiteSerializer : SearchableSerializer {
    override fun serialize(searchable: Searchable): String {
        searchable as Website
        return jsonObjectOf(
            "label" to searchable.label,
            "url" to searchable.url,
            "description" to searchable.description,
            "image" to searchable.image,
            "favicon" to searchable.favicon,
            "color" to searchable.color
        ).toString()
    }

    override val typePrefix: String
        get() = "website"
}

class WebsiteDeserializer: SearchableDeserializer {
    override fun deserialize(serialized: String): Searchable? {
        val json = JSONObject(serialized)
        return Website(
            label = json.getString("label"),
            favicon = json.getString("favicon"),
            image = json.getString("image"),
            description = json.getString("description"),
            url = json.getString("url"),
            color = json.getInt("color")
        )
    }
}