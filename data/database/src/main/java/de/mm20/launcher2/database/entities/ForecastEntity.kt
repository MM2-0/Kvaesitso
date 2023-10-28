package de.mm20.launcher2.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = ForecastEntity.TABLE_NAME)
data class ForecastEntity(
        @PrimaryKey val timestamp: Long,
        val temperature: Double,
        val minTemp: Double = -1.0,
        val maxTemp: Double = -1.0,
        val pressure: Double = -1.0,
        val humidity: Double = -1.0,
        val icon: Int,
        val condition: String,
        val clouds: Int = -1,
        val windSpeed: Double = -1.0,
        val windDirection: Double = -1.0,
        @ColumnInfo(name = "rain") val precipitation: Double = -1.0,
        val snow: Double = -1.0,
        val night: Boolean = false,
        val location: String,
        val provider: String,
        val providerUrl: String = "",
        @ColumnInfo(name = "rainProbability") val precipProbability: Int = -1,
        val snowProbability: Int = -1,
        val updateTime: Long
) {
    companion object {
        const val TABLE_NAME = "forecasts"
    }
}