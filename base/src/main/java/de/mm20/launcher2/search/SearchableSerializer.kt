package de.mm20.launcher2.search

interface SearchableSerializer {
    fun serialize(searchable: PinnableSearchable): String?
    val typePrefix: String
}

class NullSerializer : SearchableSerializer {
    override fun serialize(searchable: PinnableSearchable): String? {
        return null
    }

    override val typePrefix: String
        get() = "null"

}