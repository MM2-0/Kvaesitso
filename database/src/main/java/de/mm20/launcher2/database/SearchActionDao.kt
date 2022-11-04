package de.mm20.launcher2.database

import androidx.room.Dao
import androidx.room.Query
import de.mm20.launcher2.database.entities.SearchActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchActionDao {
    @Query("SELECT * FROM SearchAction ORDER BY position ASC")
    fun getSearchActions(): Flow<List<SearchActionEntity>>
}