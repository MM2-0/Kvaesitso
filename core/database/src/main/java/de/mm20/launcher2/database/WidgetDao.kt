package de.mm20.launcher2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import de.mm20.launcher2.database.entities.WidgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WidgetDao {
    @Query("SELECT * FROM Widget ORDER BY position ASC")
    fun getWidgets(): Flow<List<WidgetEntity>>

    @Transaction
    fun updateWidgets(widgets: List<WidgetEntity>) {
        deleteAll()
        insertAll(widgets)
    }

    @Insert
    fun insertAll(widgets: List<WidgetEntity>)

    @Insert
    fun insert(widget: WidgetEntity)

    @Query("DELETE FROM Widget")
    fun deleteAll()


    @Query("DELETE FROM Widget WHERE data = :data AND type = :type")
    fun deleteWidget(type: String, data: String)

    @Query("UPDATE Widget SET height = :newHeight WHERE data = :data AND type = :type")
    fun updateHeight(type: String, data: String, newHeight: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM Widget WHERE type = :type AND data = :data)")
    fun exists(type: String, data: String) : Flow<Boolean>
}