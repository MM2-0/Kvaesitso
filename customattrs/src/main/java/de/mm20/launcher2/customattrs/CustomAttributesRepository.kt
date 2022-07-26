package de.mm20.launcher2.customattrs

import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface CustomAttributesRepository {
    fun getCustomIcon(searchable: Searchable): Flow<CustomIcon?>
    fun setCustomIcon(searchable: Searchable, icon: CustomIcon?)
}

internal class CustomAttributesRepositoryImpl(
    private val appDatabase: AppDatabase,
) : CustomAttributesRepository {
    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    override fun getCustomIcon(searchable: Searchable): Flow<CustomIcon?> {
        val dao = appDatabase.customAttrsDao()
        return dao.getCustomAttribute(searchable.key, CustomAttributeType.Icon.value)
            .map {
                CustomAttribute.fromDatabaseEntity(it) as? CustomIcon
            }
    }

    override fun setCustomIcon(searchable: Searchable, icon: CustomIcon?) {
        val dao = appDatabase.customAttrsDao()
        scope.launch {
            dao.clearCustomAttribute(searchable.key, CustomAttributeType.Icon.value)
            if (icon != null) {
                dao.setCustomAttribute(icon.toDatabaseEntity(searchable.key))
            }
        }
    }
}