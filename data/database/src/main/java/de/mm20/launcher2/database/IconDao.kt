package de.mm20.launcher2.database

import androidx.room.*
import de.mm20.launcher2.database.entities.IconEntity
import de.mm20.launcher2.database.entities.IconPackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IconDao {
    @Insert
    suspend fun insertAll(icons: List<IconEntity>)

    @Query("SELECT * FROM Icons WHERE packageName = :packageName AND (activityName = :activityName OR activityName IS NULL) AND iconPack = :iconPack AND type IN ('app', 'calendar', 'clock') ORDER BY type DESC LIMIT 1")
    suspend fun getIcon(packageName: String, activityName: String?, iconPack: String): IconEntity?

    @Query("SELECT * FROM Icons WHERE drawable = :iconName AND iconPack = :iconPack ORDER BY type DESC LIMIT 1")
    suspend fun getIcon(iconName: String, iconPack: String): IconEntity?

    @Query("SELECT * FROM Icons WHERE packageName = :packageName AND (activityName = :activityName OR activityName IS NULL) AND type IN ('app', 'calendar', 'clock')")
    suspend fun getIconsFromAllPacks(packageName: String, activityName: String): List<IconEntity>

    @Query("SELECT * FROM Icons WHERE type IN ('app', 'calendar', 'clock') AND (drawable LIKE :drawableQuery OR name LIKE :nameQuery) AND (:iconPack IS NULL OR iconPack = :iconPack) GROUP BY drawable, iconPack, type ORDER BY type DESC, iconPack, drawable LIMIT :limit")
    suspend fun searchIconPackIcons(
        nameQuery: String,
        drawableQuery: String,
        iconPack: String?,
        limit: Int = 100
    ): List<IconEntity>

    @Query("DELETE FROM Icons WHERE iconPack = :iconPack")
    fun deleteIcons(iconPack: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun installIconPack(iconPack: IconPackEntity)

    @Query("SELECT * FROM IconPack ORDER BY name ASC")
    fun getInstalledIconPacks(): Flow<List<IconPackEntity>>

    @Query("SELECT * FROM IconPack WHERE packageName = :packageName LIMIT 1")
    suspend fun getIconPack(packageName: String): IconPackEntity?

    @Delete
    fun deleteIconPack(iconPack: IconPackEntity)

    @Query("SELECT drawable FROM Icons WHERE iconPack = :pack AND type = 'iconback'")
    suspend fun getIconBacks(pack: String): List<String>

    @Query("SELECT drawable FROM Icons WHERE iconPack = :pack AND type = 'iconupon'")
    suspend fun getIconUpons(pack: String): List<String>

    @Query("SELECT drawable FROM Icons WHERE iconPack = :pack AND type = 'iconmask'")
    suspend fun getIconMasks(pack: String): List<String>

    @Query("SELECT scale FROM IconPack WHERE packageName = :pack")
    suspend fun getScale(pack: String): Float?

    @Query("DELETE FROM Icons WHERE iconPack NOT IN (:keep)")
    suspend fun deleteIconsNotIn(keep: List<String>)

    @Query("DELETE FROM IconPack WHERE packageName NOT IN (:keep)")
    suspend fun deleteIconPacksNotIn(keep: List<String>)
}