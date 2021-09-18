package de.mm20.launcher2.favorites

import android.content.Context
import de.mm20.launcher2.database.entities.FavoritesItemEntity
import de.mm20.launcher2.search.data.Searchable

data class FavoritesItem(
        val key: String,
        /**
         * null if searchable could not be deserialized (i.e. the app has been uninstalled)
         */
        val searchable: Searchable?,
        var launchCount: Int,
        var pinPosition: Int,
        var hidden: Boolean
){
    constructor(context: Context, entity: FavoritesItemEntity) : this(
            key = entity.key,
            searchable = SearchableDeserializer(context).deserialize(entity.serializedSearchable),
            launchCount = entity.launchCount,
            pinPosition = entity.pinPosition,
            hidden = entity.hidden
    )


    fun toDatabaseEntity(): FavoritesItemEntity {
        return FavoritesItemEntity(
                key = key,
                serializedSearchable = searchable?.let { "${SearchableDeserializer.getTypePrefix(it)}#${it.serialize()}" } ?: "",
                hidden = hidden,
                pinPosition = pinPosition,
                launchCount = launchCount
        )
    }
}