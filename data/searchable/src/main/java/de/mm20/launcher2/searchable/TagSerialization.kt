package de.mm20.launcher2.searchable

import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.data.Tag
import org.json.JSONObject

class TagSerializer: SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as Tag
        val json = JSONObject()
        json.put("tag", searchable.tag)
        return json.toString()
    }

    override val typePrefix: String
        get() = "tag"
}

class TagDeserializer: SearchableDeserializer {
    override fun deserialize(serialized: String): SavableSearchable {
        val json = JSONObject(serialized)

        return Tag(json.getString("tag"))
    }

}