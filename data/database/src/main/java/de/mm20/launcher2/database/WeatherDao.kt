package de.mm20.launcher2.database

import androidx.room.*
import de.mm20.launcher2.database.entities.ForecastEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {
    @Query("SELECT * FROM ${ForecastEntity.TABLE_NAME} ORDER BY timestamp ASC LIMIT :limit")
    fun getForecasts(limit: Int = 99999): Flow<List<ForecastEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(forecasts: List<ForecastEntity>)

    @Query("DELETE FROM ${ForecastEntity.TABLE_NAME}")
    fun deleteAll()

    @Transaction
    fun replaceAll(forecasts: List<ForecastEntity>) {
        deleteAll()
        insertAll(forecasts)
    }
}