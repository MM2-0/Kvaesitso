package de.mm20.launcher2.search

interface SearchableDeserializer {
    fun deserialize(serialized: String): PinnableSearchable?
}

class NullDeserializer: SearchableDeserializer {
    override fun deserialize(serialized: String): PinnableSearchable? {
        return null
    }

}