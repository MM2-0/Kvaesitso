package de.mm20.launcher2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.mm20.launcher2.database.entities.FavoritesItemEntity
import de.mm20.launcher2.database.entities.WebsearchEntity
import de.mm20.launcher2.database.entities.WidgetEntity

@Dao
interface BackupRestoreDao {

    @Query("DELETE FROM Searchable")
    suspend fun wipeFavorites()

    @Query("SELECT * FROM Searchable LIMIT :limit OFFSET :offset")
    suspend fun exportFavorites(limit: Int, offset: Int): List<FavoritesItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun importFavorites(items: List<FavoritesItemEntity>)

    @Query("DELETE FROM Widget")
    suspend fun wipeWidgets()

    @Query("SELECT * FROM Widget LIMIT :limit OFFSET :offset")
    suspend fun exportWidgets(limit: Int, offset: Int): List<WidgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun importWidgets(items: List<WidgetEntity>)

    @Query("DELETE FROM Websearch")
    suspend fun wipeWebsearches()

    @Query("SELECT * FROM Websearch LIMIT :limit OFFSET :offset")
    suspend fun exportWebsearches(limit: Int, offset: Int): List<WebsearchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun importWebsearches(items: List<WebsearchEntity>)
}