package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.mm20.launcher2.database.entities.ForecastEntity

class Migration_8_9 : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `${ForecastEntity.TABLE_NAME}2` (" +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`temperature` REAL NOT NULL, " +
                    "`minTemp` REAL NOT NULL, " +
                    "`maxTemp` REAL NOT NULL, " +
                    "`pressure` REAL NOT NULL, " +
                    "`humidity` REAL NOT NULL, " +
                    "`icon` INTEGER NOT NULL, " +
                    "`condition` TEXT NOT NULL, " +
                    "`clouds` INTEGER NOT NULL, " +
                    "`windSpeed` REAL NOT NULL, " +
                    "`windDirection` REAL NOT NULL, " +
                    "`rain` REAL NOT NULL, " +
                    "`snow` REAL NOT NULL, " +
                    "`night` INTEGER NOT NULL, " +
                    "`location` TEXT NOT NULL, " +
                    "`provider` TEXT NOT NULL, " +
                    "`providerUrl` TEXT NOT NULL, " +
                    "`rainProbability` INTEGER NOT NULL, " +
                    "`snowProbability` INTEGER NOT NULL, " +
                    "`updateTime` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`timestamp`))"
        )
        database.execSQL("INSERT INTO ${ForecastEntity.TABLE_NAME}2 SELECT timestamp, temperature, minTemp, maxTemp, pressure, humidity, icon, condition, clouds, windSpeed, windDirection, rain, snow, night, location, provider, providerUrl, rainPropability as rainProbability, snowProbability, 0 as updateTime FROM ${ForecastEntity.TABLE_NAME}")
        database.execSQL("DROP TABLE ${ForecastEntity.TABLE_NAME}")
        database.execSQL("ALTER TABLE ${ForecastEntity.TABLE_NAME}2 RENAME TO ${ForecastEntity.TABLE_NAME}")
    }

}