package de.mm20.launcher2.database.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import de.mm20.launcher2.database.entities.PluginEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginDao {

    @Query("""
        SELECT * FROM Plugins WHERE
            (type = :type OR :type IS NULL) AND
            (enabled = :enabled OR :enabled IS NULL) AND
            (packageName = :packageName OR :packageName IS NULL)
    """)
    fun findMany(
        type: String? = null,
        enabled: Boolean? = null,
        packageName: String? = null,
    ): Flow<List<PluginEntity>>

    @Query("SELECT * FROM Plugins WHERE authority = :authority")
    fun get(authority: String): Flow<PluginEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMany(plugins: List<PluginEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plugin: PluginEntity)

    @Update
    suspend fun update(plugin: PluginEntity)

    @Update
    suspend fun updateMany(plugins: List<PluginEntity>)

    @Query("DELETE FROM Plugins")
    suspend fun deleteMany()
}