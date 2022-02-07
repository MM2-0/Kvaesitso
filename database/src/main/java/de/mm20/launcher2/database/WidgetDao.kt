package de.mm20.launcher2.database

import androidx.lifecycle.LiveData
import androidx.room.*
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

    @Query("DELETE FROM Widget")
    fun deleteAll()
}