package de.mm20.launcher2.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.mm20.launcher2.database.entities.*

@Database(entities = [ForecastEntity::class,
    FavoritesItemEntity::class,
    WebsearchEntity::class,
    CurrencyEntity::class,
    IconEntity::class,
    IconPackEntity::class,
    WidgetEntity::class], version = 15, exportSchema = true)
@TypeConverters(ComponentNameConverter::class, StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun weatherDao(): WeatherDao
    abstract fun searchDao(): SearchDao
    abstract fun iconDao(): IconDao
    abstract fun widgetDao(): WidgetDao
    abstract fun currencyDao(): CurrencyDao

    companion object {
        private var _instance: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase {
            val instance = _instance
                    ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "room")
                            //.fallbackToDestructiveMigration()
                            .addCallback(object : Callback() {
                                override fun onCreate(db: SupportSQLiteDatabase) {
                                    super.onCreate(db)
                                    db.execSQL("INSERT INTO Websearch (urlTemplate, label, color, icon) VALUES " +
                                            "('${context.getString(R.string.default_websearch_1_url)}', '${context.getString(R.string.default_websearch_1_name)}', 0, NULL )," +
                                            "('${context.getString(R.string.default_websearch_2_url)}', '${context.getString(R.string.default_websearch_2_name)}', 0, NULL )," +
                                            "('${context.getString(R.string.default_websearch_3_url)}', '${context.getString(R.string.default_websearch_3_name)}', 0, NULL );")

                                    db.execSQL("INSERT INTO Widget (type, data, height, position, label) VALUES " +
                                            "('internal', 'weather', -1, 0, '${context.getString(R.string.widget_name_weather)}')," +
                                            "('internal', 'music', -1, 1, '${context.getString(R.string.widget_name_music)}')," +
                                            "('internal', 'calendar', -1, 2, '${context.getString(R.string.widget_name_calendar)}');")
                                }
                            })
                            .addMigrations(
                                    Migration_6_7(),
                                    Migration_7_8(),
                                    Migration_8_9(),
                                    Migration_9_10(),
                                    Migration_10_11(),
                                    Migration_11_12(),
                                    Migration_12_13(),
                                    Migration_13_14(),
                                    Migration_14_15()
                            ).build()
            if (_instance == null) _instance = instance
            return instance
        }
    }
}

class Migration_6_7 : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE Searchable2 (`key` TEXT NOT NULL, `searchable` TEXT, `launchCount` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, `inAllApps` INTEGER NOT NULL, PRIMARY KEY(`key`))")
        database.execSQL("INSERT INTO Searchable2 SELECT * FROM Searchable")
        database.execSQL("DROP TABLE Searchable")
        database.execSQL("ALTER TABLE Searchable2 RENAME TO Searchable")
    }

}

class Migration_7_8 : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `${ForecastEntity.TABLE_NAME}2` (`timestamp` INTEGER NOT NULL, `temperature` REAL NOT NULL, `minTemp` REAL NOT NULL, `maxTemp` REAL NOT NULL, `pressure` REAL NOT NULL, `humidity` REAL NOT NULL, `icon` INTEGER NOT NULL, `condition` TEXT NOT NULL, `clouds` INTEGER NOT NULL, `windSpeed` REAL NOT NULL, `windDirection` REAL NOT NULL, `rain` REAL NOT NULL, `snow` REAL NOT NULL, `night` INTEGER NOT NULL, `location` TEXT NOT NULL, `provider` TEXT NOT NULL, `providerUrl` TEXT NOT NULL, `rainPropability` INTEGER NOT NULL, `snowProbability` INTEGER NOT NULL, PRIMARY KEY(`timestamp`))")
        database.execSQL("INSERT INTO ${ForecastEntity.TABLE_NAME}2 SELECT *, -1 as rainPropability, -1 as snowPropability FROM ${ForecastEntity.TABLE_NAME}")
        database.execSQL("DROP TABLE ${ForecastEntity.TABLE_NAME}")
        database.execSQL("ALTER TABLE ${ForecastEntity.TABLE_NAME}2 RENAME TO ${ForecastEntity.TABLE_NAME}")
    }
}

class Migration_8_9 : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `${ForecastEntity.TABLE_NAME}2` (" +
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
                "PRIMARY KEY(`timestamp`))")
        database.execSQL("INSERT INTO ${ForecastEntity.TABLE_NAME}2 SELECT timestamp, temperature, minTemp, maxTemp, pressure, humidity, icon, condition, clouds, windSpeed, windDirection, rain, snow, night, location, provider, providerUrl, rainPropability as rainProbability, snowProbability, 0 as updateTime FROM ${ForecastEntity.TABLE_NAME}")
        database.execSQL("DROP TABLE ${ForecastEntity.TABLE_NAME}")
        database.execSQL("ALTER TABLE ${ForecastEntity.TABLE_NAME}2 RENAME TO ${ForecastEntity.TABLE_NAME}")
    }

}

class Migration_9_10 : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `Plugins` (`packageName` TEXT NOT NULL, `label` TEXT NOT NULL, `description` TEXT NOT NULL, `pluginClassName` TEXT NOT NULL, `enabled` INTEGER NOT NULL, PRIMARY KEY(`packageName`) );")
    }

}

class Migration_10_11 : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `temp` (`key` TEXT NOT NULL, `searchable` TEXT NOT NULL, `launchCount` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, PRIMARY KEY(`key`))")
        database.execSQL("INSERT INTO `temp` SELECT `key`, `searchable`, `launchCount`, `pinned`, `hidden` FROM `Searchable`")
        database.execSQL("DROP TABLE `Searchable`")
        database.execSQL("ALTER TABLE `temp` RENAME TO `Searchable`")
    }
}

class Migration_11_12 : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `Currency` (`symbol` TEXT NOT NULL, `value` REAL NOT NULL, `lastUpdate` INTEGER NOT NULL, PRIMARY KEY(`symbol`))")
    }
}

class Migration_12_13 : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `Plugin` (`packageName` TEXT NOT NULL, `data` TEXT NOT NULL, `type` TEXT NOT NULL, PRIMARY KEY(`packageName`, `data`))")
    }
}
class Migration_13_14 : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS `Plugins`;")
    }
}
class Migration_14_15 : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
    }
}