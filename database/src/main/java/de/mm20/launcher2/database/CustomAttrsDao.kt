package de.mm20.launcher2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    @Insert
    suspend fun insertCustomAttributes(entities: List<CustomAttributeEntity>)

    @Query("SELECT * FROM CustomAttributes WHERE type = :type AND key IN (:keys)")
    fun getCustomAttributes(keys: List<String>, type: String) : Flow<List<CustomAttributeEntity>>

    @Query("SELECT DISTINCT key FROM CustomAttributes WHERE (type = 'label' OR type = 'tag') AND value LIKE :query")
    fun search(query: String): Flow<List<String>>

    @Transaction
    suspend fun setTags(key: String, tags: List<CustomAttributeEntity>) {
        clearCustomAttribute(key, "tag")
        insertCustomAttributes(tags)
    }

    @Query("SELECT DISTINCT value FROM CustomAttributes WHERE type = 'tag' AND value LIKE :like ORDER BY value")
    suspend fun getAllTagsLike(like: String): List<String>

    @Query("SELECT DISTINCT value FROM CustomAttributes WHERE type = 'tag' ORDER BY value")
    suspend fun getAllTags(): List<String>

    @Query("SELECT key FROM CustomAttributes WHERE type = 'tag' AND value = :tag")
    fun getItemsWithTag(tag: String): Flow<List<String>>

    @Transaction
    suspend fun addTag(key: String, tag: String) {
        removeTag(key, tag)
        insertTag(key, tag)
    }

    @Query("DELETE FROM CustomAttributes WHERE type = 'tag' AND key = :key AND value = :tag")
    suspend fun removeTag(key: String, tag: String)

    @Query("INSERT INTO CustomAttributes (key, value, type) VALUES (:key, :tag, 'tag')")
    suspend fun insertTag(key: String, tag: String)

}