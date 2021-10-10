package de.mm20.launcher2.search

import de.mm20.launcher2.search.data.Searchable

interface SearchableDeserializer {
    fun deserialize(serialized: String): Searchable?
}

class NullDeserializer: SearchableDeserializer {
    override fun deserialize(serialized: String): Searchable? {
        return null
    }

}