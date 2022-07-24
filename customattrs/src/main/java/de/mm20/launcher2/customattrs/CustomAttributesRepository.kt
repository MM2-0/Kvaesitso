package de.mm20.launcher2.customattrs

import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface CustomAttributesRepository {
    fun getCustomIcon(searchable: Searchable): Flow<CustomIcon?>
}

internal class CustomAttributesRepositoryImpl(
    private val appDatabase: AppDatabase,
) : CustomAttributesRepository {
    override fun getCustomIcon(searchable: Searchable): Flow<CustomIcon?> {
        val dao = appDatabase.customAttrsDao()
        return dao.getCustomIcon(searchable.key)
            .map {
                CustomAttribute.fromDatabaseEntity(it) as? CustomIcon
            }
    }
}