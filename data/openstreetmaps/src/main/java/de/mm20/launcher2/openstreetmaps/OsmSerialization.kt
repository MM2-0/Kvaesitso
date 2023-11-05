package de.mm20.launcher2.openstreetmaps

import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer

class OsmLocationSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as OsmLocation
        TODO()
    }

    override val typePrefix: String
        get() = "osmlocation"
}

class OsmLocationDeserializer : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable? {
        TODO()
    }
}