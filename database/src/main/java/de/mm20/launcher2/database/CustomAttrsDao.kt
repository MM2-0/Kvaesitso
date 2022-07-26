package de.mm20.launcher2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.mm20.launcher2.database.entities.CustomAttributeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomAttrsDao {
    @Query("SELECT * FROM CustomAttributes WHERE type = :type AND key = :key LIMIT 1")
    fun getCustomAttribute(key: String, type: String) : Flow<CustomAttributeEntity?>

    @Query("DELETE FROM CustomAttributes WHERE type = :type AND key = :key")
    fun clearCustomAttribute(key: String, type: String)

    @Insert
    fun setCustomAttribute(entity: CustomAttributeEntity)
}