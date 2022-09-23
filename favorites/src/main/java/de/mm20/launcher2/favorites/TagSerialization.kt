package de.mm20.launcher2.favorites

import android.content.Context
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.search.data.Tag
import org.json.JSONObject

class TagSerializer: SearchableSerializer {
    override fun serialize(searchable: Searchable): String {
        searchable as Tag
        val json = JSONObject()
        json.put("tag", searchable.tag)
        return json.toString()
    }

    override val typePrefix: String
        get() = "tag"
}

class TagDeserializer: SearchableDeserializer {
    override fun deserialize(serialized: String): Searchable {
        val json = JSONObject(serialized)

        return Tag(json.getString("tag"))
    }

}