package de.mm20.launcher2.database

import androidx.room.Dao
import androidx.room.Query
import de.mm20.launcher2.database.entities.CustomAttributeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomAttrsDao {
    @Query("SELECT * FROM CustomAttributes WHERE type = 'icon' AND key = :key LIMIT 1")
    fun getCustomIcon(key: String) : Flow<CustomAttributeEntity?>
}