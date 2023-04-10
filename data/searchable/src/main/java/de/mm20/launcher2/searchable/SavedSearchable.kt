package de.mm20.launcher2.searchable

import de.mm20.launcher2.database.entities.SavedSearchableEntity
import de.mm20.launcher2.search.SavableSearchable

data class SavedSearchable(
    val key: String,
    /**
     * null if searchable could not be deserialized (i.e. the app has been uninstalled)
     */
    val searchable: SavableSearchable?,
    var launchCount: Int,
    var pinPosition: Int,
    var hidden: Boolean,
    var weight: Double
) {
    fun toDatabaseEntity(): SavedSearchableEntity? {
        val serializer = getSerializer(searchable)

        val data = searchable?.let { serializer.serialize(it) } ?: return null

        return SavedSearchableEntity(
            key = key,
            type = searchable.domain,
            serializedSearchable = data,
            hidden = hidden,
            pinPosition = pinPosition,
            launchCount = launchCount,
            weight = weight
        )
    }
}