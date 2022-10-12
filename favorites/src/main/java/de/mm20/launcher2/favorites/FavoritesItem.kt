package de.mm20.launcher2.favorites

import de.mm20.launcher2.database.entities.FavoritesItemEntity
import de.mm20.launcher2.search.PinnableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.Searchable

data class FavoritesItem(
    val key: String,
    /**
     * null if searchable could not be deserialized (i.e. the app has been uninstalled)
     */
    val searchable: PinnableSearchable?,
    var launchCount: Int,
    var pinPosition: Int,
    var hidden: Boolean
) {
    private val serializer: SearchableSerializer = getSerializer(searchable)

    fun toDatabaseEntity(): FavoritesItemEntity? {
        val serializer = serializer

        val data = searchable?.let { serializer.serialize(it) } ?: return null

        return FavoritesItemEntity(
            key = key,
            serializedSearchable = "${serializer.typePrefix}#${data}",
            hidden = hidden,
            pinPosition = pinPosition,
            launchCount = launchCount
        )
    }
}