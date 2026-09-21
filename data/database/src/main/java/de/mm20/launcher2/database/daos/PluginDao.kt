package de.mm20.launcher2.database.daos

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
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