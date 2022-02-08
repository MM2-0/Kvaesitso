package de.mm20.launcher2.search

import de.mm20.launcher2.search.data.Searchable

interface SearchableSerializer {
    fun serialize(searchable: Searchable): String?
    val typePrefix: String
}

class NullSerializer : SearchableSerializer {
    override fun serialize(searchable: Searchable): String? {
        return null
    }

    override val typePrefix: String
        get() = "null"

}