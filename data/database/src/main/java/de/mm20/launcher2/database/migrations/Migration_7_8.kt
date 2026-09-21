package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import de.mm20.launcher2.database.entities.ForecastEntity

class Migration_7_8 : Migration(7, 8) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `${ForecastEntity.TABLE_NAME}2` (`timestamp` INTEGER NOT NULL, `temperature` REAL NOT NULL, `minTemp` REAL NOT NULL, `maxTemp` REAL NOT NULL, `pressure` REAL NOT NULL, `humidity` REAL NOT NULL, `icon` INTEGER NOT NULL, `condition` TEXT NOT NULL, `clouds` INTEGER NOT NULL, `windSpeed` REAL NOT NULL, `windDirection` REAL NOT NULL, `rain` REAL NOT NULL, `snow` REAL NOT NULL, `night` INTEGER NOT NULL, `location` TEXT NOT NULL, `provider` TEXT NOT NULL, `providerUrl` TEXT NOT NULL, `rainPropability` INTEGER NOT NULL, `snowProbability` INTEGER NOT NULL, PRIMARY KEY(`timestamp`))")
        connection.execSQL("INSERT INTO ${ForecastEntity.TABLE_NAME}2 SELECT *, -1 as rainPropability, -1 as snowPropability FROM ${ForecastEntity.TABLE_NAME}")
        connection.execSQL("DROP TABLE ${ForecastEntity.TABLE_NAME}")
        connection.execSQL("ALTER TABLE ${ForecastEntity.TABLE_NAME}2 RENAME TO ${ForecastEntity.TABLE_NAME}")
    }
}