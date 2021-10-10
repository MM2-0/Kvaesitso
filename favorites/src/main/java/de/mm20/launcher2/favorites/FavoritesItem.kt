package de.mm20.launcher2.favorites

import android.content.Context
import de.mm20.launcher2.database.entities.FavoritesItemEntity
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.data.Searchable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

data class FavoritesItem(
    val key: String,
    /**
     * null if searchable could not be deserialized (i.e. the app has been uninstalled)
     */
    val searchable: Searchable?,
    var launchCount: Int,
    var pinPosition: Int,
    var hidden: Boolean
) : KoinComponent {
    private val serializer: SearchableSerializer by inject { parametersOf(searchable) }

    fun toDatabaseEntity(): FavoritesItemEntity {

        return FavoritesItemEntity(
            key = key,
            serializedSearchable = searchable?.let {
                "${serializer.typePrefix}#${
                    serializer.serialize(
                        it
                    )
                }"
            } ?: "",
            hidden = hidden,
            pinPosition = pinPosition,
            launchCount = launchCount
        )
    }
}