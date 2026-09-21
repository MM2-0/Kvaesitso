package de.mm20.launcher2.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import de.mm20.launcher2.database.entities.SearchActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchActionDao {
    @Query("SELECT * FROM SearchAction ORDER BY position ASC")
    fun getSearchActions(): Flow<List<SearchActionEntity>>

    @Transaction
    suspend fun replaceAll(actions: List<SearchActionEntity>) {
        deleteAll()
        insertAll(actions)
    }

    @Query("DELETE FROM `SearchAction`")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(actions: List<SearchActionEntity>)
}