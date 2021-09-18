package de.mm20.launcher2.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.REPLACE
import androidx.room.Query
import androidx.room.Transaction
import de.mm20.launcher2.database.entities.ForecastEntity

@Dao
interface WeatherDao {
    @Query("SELECT * FROM ${ForecastEntity.TABLE_NAME} ORDER BY timestamp ASC")
    fun getForecasts(): LiveData<List<ForecastEntity>>

    @Insert(onConflict = REPLACE)
    fun insertAll(forecasts: List<ForecastEntity>)

    @Query("DELETE FROM ${ForecastEntity.TABLE_NAME}")
    fun deleteAll()

    @Transaction
    fun replaceAll(forecasts: List<ForecastEntity>) {
        deleteAll()
        insertAll(forecasts)
    }
}