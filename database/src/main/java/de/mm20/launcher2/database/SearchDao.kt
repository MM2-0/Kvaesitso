package de.mm20.launcher2.database

import androidx.room.*
import de.mm20.launcher2.database.entities.FavoritesItemEntity
import de.mm20.launcher2.database.entities.WebsearchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchDao {

    @Insert()
    fun insertAll(items: List<FavoritesItemEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAllSkipExisting(items: List<FavoritesItemEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSkipExisting(items: FavoritesItemEntity)


    @Query("SELECT * FROM Searchable " +
            "WHERE ((:manuallySorted AND pinned > 1) OR " +
            "(:automaticallySorted AND pinned = 1) OR" +
            "(:frequentlyUsed AND pinned = 0 AND launchCount > 0)" +
            ") ORDER BY pinned DESC, launchCount DESC LIMIT :limit")
    fun getFavorites(
        manuallySorted: Boolean = false,
        automaticallySorted: Boolean = false,
        frequentlyUsed: Boolean = false,
        limit: Int,
    ): Flow<List<FavoritesItemEntity>>

    @Query("SELECT * FROM Searchable " +
            "WHERE SUBSTR(`key`, 0, INSTR(`key`, '://')) IN (:includeTypes) AND (" +
            "(:manuallySorted AND pinned > 1) OR " +
            "(:automaticallySorted AND pinned = 1) OR" +
            "(:frequentlyUsed AND pinned = 0 AND launchCount > 0)" +
            ") ORDER BY pinned DESC, launchCount DESC LIMIT :limit")
    fun getFavoritesWithTypes(
        includeTypes: List<String>,
        manuallySorted: Boolean = false,
        automaticallySorted: Boolean = false,
        frequentlyUsed: Boolean = false,
        limit: Int,
    ): Flow<List<FavoritesItemEntity>>

    @Query("SELECT * FROM Searchable " +
            "WHERE SUBSTR(`key`, 0, INSTR(`key`, '://')) NOT IN (:excludeTypes) AND (" +
            "(:manuallySorted AND pinned > 1) OR " +
            "(:automaticallySorted AND pinned = 1) OR" +
            "(:frequentlyUsed AND pinned = 0 AND launchCount > 0)" +
            ") ORDER BY pinned DESC, launchCount DESC LIMIT :limit")
    fun getFavoritesWithoutTypes(
        excludeTypes: List<String>,
        manuallySorted: Boolean = false,
        automaticallySorted: Boolean = false,
        frequentlyUsed: Boolean = false,
        limit: Int,
    ): Flow<List<FavoritesItemEntity>>

    @Query("SELECT `key` FROM Searchable WHERE hidden = 1 AND `key` LIKE 'calendar://%'")
    fun getHiddenCalendarEventKeys(): Flow<List<String>>


    @Query("SELECT COUNT(key) as count FROM Searchable WHERE pinned = 1;")
    fun getPinCount(): Int

    @Query("SELECT * FROM Searchable WHERE pinned = 0 AND launchCount > 0 AND hidden = 0 AND NOT `key` LIKE 'calendar://%' ORDER BY launchCount DESC LIMIT :count")
    fun getAutoFavorites(count: Int): List<FavoritesItemEntity>

    @Query("DELETE FROM Searchable WHERE `key` IN (:keys)")
    fun deleteAll(keys: List<String>)


    @Query("UPDATE Searchable SET pinned = 1, hidden = 0 WHERE `key` = :key")
    fun pinExistingItem(key: String)

    @Transaction
    fun pinToFavorites(item: FavoritesItemEntity) {
        pinExistingItem(item.key)
        insertSkipExisting(item)
    }

    @Query("UPDATE Searchable SET pinned = 0 WHERE `key` = :key")
    fun unpinFavorite(key: String)

    @Query("DELETE FROM Searchable WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("UPDATE Searchable SET pinned = 0 WHERE `key` = :key")
    fun unpinApp(key: String)


    @Query("SELECT pinned FROM Searchable WHERE `key` = :key UNION SELECT 0 as pinned ORDER BY pinned DESC LIMIT 1")
    fun isPinned(key: String): Flow<Boolean>


    @Query("UPDATE Searchable SET hidden = 1, pinned = 0 WHERE `key` = :key")
    fun hideExistingItem(key: String)

    @Transaction
    fun hideItem(item: FavoritesItemEntity) {
        hideExistingItem(item.key)
        insertSkipExisting(item)
    }

    @Query("UPDATE Searchable SET hidden = 0 WHERE `key` = :key")
    fun unhideItem(key: String)

    @Query("SELECT hidden FROM Searchable WHERE `key` = :key UNION SELECT 0 as hidden ORDER BY hidden DESC LIMIT 1")
    fun isHidden(key: String): Flow<Boolean>

    @Query("SELECT `key` FROM SEARCHABLE WHERE hidden = 1")
    fun getHiddenItemKeys(): Flow<List<String>>

    @Query("SELECT * FROM SEARCHABLE WHERE hidden = 1")
    fun getHiddenItems(): Flow<List<FavoritesItemEntity>>

    @Query("SELECT * FROM Websearch ORDER BY label ASC")
    fun getWebSearches(): Flow<List<WebsearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWebsearch(websearch: WebsearchEntity)

    @Delete
    fun deleteWebsearch(websearch: WebsearchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllWebsearches(websearches: List<WebsearchEntity>)

    @Query("UPDATE Searchable SET launchCount = launchCount + 1 WHERE `key` = :key")
    fun incrementExistingLaunchCount(key: String)

    @Transaction
    fun incrementLaunchCount(item: FavoritesItemEntity) {
        incrementExistingLaunchCount(item.key)
        insertSkipExisting(item)
    }

    @Query("SELECT * FROM Searchable WHERE `key` = :key")
    fun getFavorite(key: String): FavoritesItemEntity?

    @Query("SELECT * FROM Searchable WHERE `key` IN (:keys)")
    fun getFromKeys(keys: List<String>): List<FavoritesItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReplaceExisting(toDatabaseEntity: FavoritesItemEntity)

    @Transaction
    fun saveFavorites(favorites: List<FavoritesItemEntity>) {
        deleteAllFavorites()
        insertAll(favorites)
    }

    @Query("DELETE FROM Searchable WHERE hidden = 0")
    fun deleteAllFavorites()

}
