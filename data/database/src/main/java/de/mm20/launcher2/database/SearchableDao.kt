package de.mm20.launcher2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import de.mm20.launcher2.database.entities.SavedSearchableEntity
import de.mm20.launcher2.database.entities.SavedSearchableUpdateContentEntity
import de.mm20.launcher2.database.entities.SavedSearchableUpdatePinEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchableDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(searchable: SavedSearchableEntity): Long

    @Upsert(entity = SavedSearchableEntity::class)
    suspend fun upsert(searchable: SavedSearchableEntity)

    @Upsert(entity = SavedSearchableEntity::class)
    suspend fun upsert(searchable: List<SavedSearchableUpdatePinEntity>)

    @Update(entity = SavedSearchableEntity::class)
    suspend fun update(searchable: SavedSearchableUpdatePinEntity)

    @Update(entity = SavedSearchableEntity::class)
    suspend fun update(searchable: SavedSearchableUpdateContentEntity)

    @Query(
        "SELECT * FROM Searchable " +
                "WHERE (" +
                "(:manuallySorted AND pinPosition > 1) OR " +
                "(:automaticallySorted AND pinPosition = 1) OR " +
                "(:frequentlyUsed AND pinPosition = 0 AND launchCount > 0) OR " +
                "(:unused AND pinPosition = 0 AND launchCount = 0)" +
                ") AND (hidden <= :minVisibility AND hidden >= :maxVisibility) " +
                "ORDER BY pinPosition DESC, weight DESC, launchCount DESC LIMIT :limit"
    )
    fun get(
        manuallySorted: Boolean = false,
        automaticallySorted: Boolean = false,
        frequentlyUsed: Boolean = false,
        unused: Boolean = false,
        minVisibility: Int = 2,
        maxVisibility: Int = 0,
        limit: Int,
    ): Flow<List<SavedSearchableEntity>>

    @Query(
        "SELECT * FROM Searchable " +
                "WHERE (`type` IN (:includeTypes)) AND " +
                "(" +
                "(:manuallySorted AND pinPosition > 1) OR " +
                "(:automaticallySorted AND pinPosition = 1) OR " +
                "(:frequentlyUsed AND pinPosition = 0 AND launchCount > 0) OR" +
                "(:unused AND pinPosition = 0 AND launchCount = 0)" +
                ") AND (hidden <= :minVisibility AND hidden >= :maxVisibility) " +
                "ORDER BY pinPosition DESC, weight DESC, launchCount DESC LIMIT :limit"
    )
    fun getIncludeTypes(
        includeTypes: List<String>?,
        manuallySorted: Boolean = false,
        automaticallySorted: Boolean = false,
        frequentlyUsed: Boolean = false,
        unused: Boolean = false,
        minVisibility: Int = 2,
        maxVisibility: Int = 0,
        limit: Int,
    ): Flow<List<SavedSearchableEntity>>

    @Query(
        "SELECT * FROM Searchable " +
                "WHERE (`type` NOT IN (:excludeTypes)) AND " +
                "(" +
                "(:manuallySorted AND pinPosition > 1) OR " +
                "(:automaticallySorted AND pinPosition = 1) OR " +
                "(:frequentlyUsed AND pinPosition = 0 AND launchCount > 0) OR " +
                "(:unused AND pinPosition = 0 AND launchCount = 0)" +
                ") AND (hidden <= :minVisibility AND hidden >= :maxVisibility) " +
                "ORDER BY pinPosition DESC, weight DESC, launchCount DESC LIMIT :limit"
    )
    fun getExcludeTypes(
        excludeTypes: List<String>?,
        manuallySorted: Boolean = false,
        automaticallySorted: Boolean = false,
        frequentlyUsed: Boolean = false,
        unused: Boolean = false,
        minVisibility: Int = 2,
        maxVisibility: Int = 0,
        limit: Int,
    ): Flow<List<SavedSearchableEntity>>

    @Query(
        "SELECT `key` FROM Searchable " +
                "WHERE (" +
                "(:manuallySorted AND pinPosition > 1) OR " +
                "(:automaticallySorted AND pinPosition = 1) OR " +
                "(:frequentlyUsed AND pinPosition = 0 AND launchCount > 0) OR " +
                "(:unused AND pinPosition = 0 AND launchCount = 0)" +
                ") AND (hidden <= :minVisibility AND hidden >= :maxVisibility) " +
                "ORDER BY pinPosition DESC, weight DESC, launchCount DESC LIMIT :limit"
    )
    fun getKeys(
        manuallySorted: Boolean = false,
        automaticallySorted: Boolean = false,
        frequentlyUsed: Boolean = false,
        unused: Boolean = false,
        minVisibility: Int = 2,
        maxVisibility: Int = 0,
        limit: Int,
    ): Flow<List<String>>

    @Query(
        "SELECT `key` FROM Searchable " +
                "WHERE (`type` IN (:includeTypes)) AND " +
                "(" +
                "(:manuallySorted AND pinPosition > 1) OR " +
                "(:automaticallySorted AND pinPosition = 1) OR" +
                "(:frequentlyUsed AND pinPosition = 0 AND launchCount > 0) OR " +
                "(:unused AND pinPosition = 0 AND launchCount = 0)" +
                ") AND (hidden <= :minVisibility AND hidden >= :maxVisibility) " +
                "ORDER BY pinPosition DESC, weight DESC, launchCount DESC LIMIT :limit"
    )
    fun getKeysIncludeTypes(
        includeTypes: List<String>?,
        manuallySorted: Boolean = false,
        automaticallySorted: Boolean = false,
        frequentlyUsed: Boolean = false,
        unused: Boolean = false,
        minVisibility: Int = 2,
        maxVisibility: Int = 0,
        limit: Int,
    ): Flow<List<String>>

    @Query(
        "SELECT `key` FROM Searchable " +
                "WHERE (`type` NOT IN (:excludeTypes)) AND " +
                "(" +
                "(:manuallySorted AND pinPosition > 1) OR " +
                "(:automaticallySorted AND pinPosition = 1) OR " +
                "(:frequentlyUsed AND pinPosition = 0 AND launchCount > 0) OR " +
                "(:unused AND pinPosition = 0 AND launchCount = 0)" +
                ") AND (hidden <= :minVisibility AND hidden >= :maxVisibility) " +
                "ORDER BY pinPosition DESC, weight DESC, launchCount DESC LIMIT :limit"
    )
    fun getKeysExcludeTypes(
        excludeTypes: List<String>?,
        manuallySorted: Boolean = false,
        automaticallySorted: Boolean = false,
        frequentlyUsed: Boolean = false,
        unused: Boolean = false,
        minVisibility: Int = 2,
        maxVisibility: Int = 0,
        limit: Int,
    ): Flow<List<String>>

    @Query("SELECT * FROM Searchable WHERE `key` IN (:keys)")
    fun getByKeys(keys: List<String>): Flow<List<SavedSearchableEntity>>

    @Query("SELECT * FROM Searchable WHERE `key` = :key")
    fun getByKey(key: String): Flow<SavedSearchableEntity?>

    @Transaction
    suspend fun touch(item: SavedSearchableEntity, alpha: Double) {
        incrementLaunchCount(item.key)
        increaseWeightWhere(item.key, alpha)
        reduceWeightExcept(item.key, alpha)
        if (insert(item) == -1L) {
            update(
                SavedSearchableUpdateContentEntity(
                    serializedSearchable = item.serializedSearchable,
                    type = item.type,
                    key = item.key,
                )
            )
        }
    }

    @Query("UPDATE Searchable SET launchCount = launchCount + 1 WHERE `key` = :key")
    fun incrementLaunchCount(key: String)

    @Query("UPDATE Searchable SET `weight` = `weight` * (1.0 - :alpha) WHERE `key` != :key AND weight > 0.001")
    fun reduceWeightExcept(key: String, alpha: Double)

    @Query("UPDATE Searchable SET `weight` = `weight` + :alpha * (1.0 - `weight`) WHERE `key` == :key")
    fun increaseWeightWhere(key: String, alpha: Double)

    @Query("DELETE FROM Searchable WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("UPDATE Searchable SET `pinPosition` = 0")
    suspend fun unpinAll()

    @Query("SELECT `key` FROM Searchable WHERE `key` IN (:keys) AND launchCount > 0 ORDER BY launchCount DESC, pinPosition DESC")
    fun sortByRelevance(keys: List<String>): Flow<List<String>>

    @Query("SELECT `key` FROM Searchable WHERE `key` IN (:keys) ORDER BY `weight` DESC, pinPosition DESC")
    fun sortByWeight(keys: List<String>): Flow<List<String>>

    @Query("SELECT `key`, `weight` FROM Searchable WHERE `key` IN (:keys)")
    fun getWeights(keys: List<String>): Flow<Map<@MapColumn(columnName = "key") String, @MapColumn(columnName = "weight") Double>>

    @Query("SELECT hidden FROM Searchable WHERE `key` = :key UNION SELECT 0 as hidden ORDER BY hidden DESC LIMIT 1")
    fun getVisibility(key: String): Flow<Int>

    @Query("SELECT pinPosition FROM Searchable WHERE `key` = :key UNION SELECT 0 as pinPosition ORDER BY pinPosition DESC LIMIT 1")
    fun isPinned(key: String): Flow<Boolean>

    @Transaction
    suspend fun replace(key: String, item: SavedSearchableUpdateContentEntity) {
        updateKey(key, item.key)
        update(item)
    }

    @Query("UPDATE Searchable SET `key` = :newKey WHERE `key` = :oldKey")
    suspend fun updateKey(oldKey: String, newKey: String)
}