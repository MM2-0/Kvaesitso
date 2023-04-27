package de.mm20.launcher2.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import de.mm20.launcher2.database.entities.WidgetEntity
import de.mm20.launcher2.database.entities.PartialWidgetEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface WidgetDao {
    @Query("SELECT * FROM Widget WHERE parentId IS NULL ORDER BY position ASC LIMIT :limit OFFSET :offset")
    fun queryRoot(limit: Int, offset: Int): Flow<List<WidgetEntity>>

    @Query("SELECT * FROM Widget WHERE parentId = :parentId ORDER BY position ASC LIMIT :limit OFFSET :offset")
    fun queryByParent(parentId: UUID,limit: Int, offset: Int): Flow<List<WidgetEntity>>

    @Insert
    suspend fun insert(widget: WidgetEntity)

    @Insert
    suspend fun insert(widgets: List<WidgetEntity>)

    @Update(entity = WidgetEntity::class)
    suspend fun patch(widget: PartialWidgetEntity)

    @Update(entity = WidgetEntity::class)
    suspend fun patch(widgets: List<PartialWidgetEntity>)

    @Update
    suspend fun update(widget: WidgetEntity)

    @Update
    suspend fun update(widgets: List<WidgetEntity>)

    @Query("DELETE FROM Widget WHERE id = :id")
    suspend fun delete(id: UUID)

    @Query("DELETE FROM WIDGET WHERE id IN (:ids)")
    suspend fun delete(ids: List<UUID>)

    @Query("DELETE FROM Widget WHERE parentId = :parentId")
    suspend fun deleteByParent(parentId: UUID)

    @Query("DELETE FROM Widget WHERE parentId IS NULL")
    suspend fun deleteRoot()

    @Query("SELECT EXISTS(SELECT 1 FROM Widget WHERE type = :type)")
    fun exists(type: String): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM Widget WHERE type = :type")
    fun count(type: String): Flow<Int>

}