@file:Suppress("ClassName")

package de.mm20.launcher2.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import de.mm20.launcher2.database.entities.*
import de.mm20.launcher2.database.migrations.Migration_10_11
import de.mm20.launcher2.database.migrations.Migration_11_12
import de.mm20.launcher2.database.migrations.Migration_12_13
import de.mm20.launcher2.database.migrations.Migration_13_14
import de.mm20.launcher2.database.migrations.Migration_14_15
import de.mm20.launcher2.database.migrations.Migration_15_16
import de.mm20.launcher2.database.migrations.Migration_16_17
import de.mm20.launcher2.database.migrations.Migration_17_18
import de.mm20.launcher2.database.migrations.Migration_18_19
import de.mm20.launcher2.database.migrations.Migration_19_20
import de.mm20.launcher2.database.migrations.Migration_20_21
import de.mm20.launcher2.database.migrations.Migration_21_22
import de.mm20.launcher2.database.migrations.Migration_6_7
import de.mm20.launcher2.database.migrations.Migration_7_8
import de.mm20.launcher2.database.migrations.Migration_8_9
import de.mm20.launcher2.database.migrations.Migration_9_10

@Database(
    entities = [
        ForecastEntity::class,
        SavedSearchableEntity::class,
        CurrencyEntity::class,
        IconEntity::class,
        IconPackEntity::class,
        WidgetEntity::class,
        CustomAttributeEntity::class,
        SearchActionEntity::class
    ], version = 22, exportSchema = true
)
@TypeConverters(ComponentNameConverter::class, StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun weatherDao(): WeatherDao
    abstract fun searchDao(): SearchDao
    abstract fun iconDao(): IconDao
    abstract fun widgetDao(): WidgetDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun backupDao(): BackupRestoreDao
    abstract fun customAttrsDao(): CustomAttrsDao

    abstract fun searchActionDao(): SearchActionDao

    companion object {
        private var _instance: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase {
            val instance = _instance
                ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "room")
                    //.fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL("INSERT INTO `SearchAction` (`position`, `type`) VALUES" +
                                    "(0, 'call')," +
                                    "(1, 'message')," +
                                    "(2, 'email')," +
                                    "(3, 'contact')," +
                                    "(4, 'alarm')," +
                                    "(5, 'timer')," +
                                    "(6, 'calendar')," +
                                    "(7, 'website')"
                            )

                            db.execSQL("INSERT INTO `SearchAction` (`position`, `type`, `data`, `label`, `color`, `icon`, `customIcon`, `options`) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?, ?, ?)",
                                arrayOf(
                                    8, "url", context.getString(R.string.default_websearch_1_url), context.getString(R.string.default_websearch_1_name), 0, 0, null, null,
                                    9, "url", context.getString(R.string.default_websearch_2_url), context.getString(R.string.default_websearch_2_name), 0, 0, null, null,
                                    10, "url", context.getString(R.string.default_websearch_3_url), context.getString(R.string.default_websearch_3_name), 0, 0, null, null,
                                )
                            )

                            db.execSQL(
                                "INSERT INTO Widget (type, data, height, position, label) VALUES " +
                                        "('internal', 'weather', -1, 0, '${context.getString(R.string.widget_name_weather)}')," +
                                        "('internal', 'music', -1, 1, '${context.getString(R.string.widget_name_music)}')," +
                                        "('internal', 'calendar', -1, 2, '${context.getString(R.string.widget_name_calendar)}');"
                            )
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
                        Migration_14_15(),
                        Migration_15_16(),
                        Migration_16_17(),
                        Migration_17_18(),
                        Migration_18_19(),
                        Migration_19_20(),
                        Migration_20_21(),
                        Migration_21_22()
                    ).build()
            if (_instance == null) _instance = instance
            return instance
        }
    }
}

