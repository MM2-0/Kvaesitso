@file:Suppress("ClassName")

package de.mm20.launcher2.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import de.mm20.launcher2.database.daos.PluginDao
import de.mm20.launcher2.database.daos.ThemeDao
import de.mm20.launcher2.database.entities.ColorsEntity
import de.mm20.launcher2.database.entities.CurrencyEntity
import de.mm20.launcher2.database.entities.CustomAttributeEntity
import de.mm20.launcher2.database.entities.ForecastEntity
import de.mm20.launcher2.database.entities.IconEntity
import de.mm20.launcher2.database.entities.IconPackEntity
import de.mm20.launcher2.database.entities.PluginEntity
import de.mm20.launcher2.database.entities.SavedSearchableEntity
import de.mm20.launcher2.database.entities.SearchActionEntity
import de.mm20.launcher2.database.entities.ShapesEntity
import de.mm20.launcher2.database.entities.TransparenciesEntity
import de.mm20.launcher2.database.entities.TypographyEntity
import de.mm20.launcher2.database.entities.WidgetEntity
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
import de.mm20.launcher2.database.migrations.Migration_22_23
import de.mm20.launcher2.database.migrations.Migration_23_24
import de.mm20.launcher2.database.migrations.Migration_24_25
import de.mm20.launcher2.database.migrations.Migration_25_26
import de.mm20.launcher2.database.migrations.Migration_27_28
import de.mm20.launcher2.database.migrations.Migration_28_29
import de.mm20.launcher2.database.migrations.Migration_29_30
import de.mm20.launcher2.database.migrations.Migration_6_7
import de.mm20.launcher2.database.migrations.Migration_7_8
import de.mm20.launcher2.database.migrations.Migration_8_9
import de.mm20.launcher2.database.migrations.Migration_9_10
import de.mm20.launcher2.ktx.toBytes
import de.mm20.launcher2.preferences.WidgetScreenTarget
import java.util.UUID

@Database(
    entities = [
        ForecastEntity::class,
        SavedSearchableEntity::class,
        CurrencyEntity::class,
        IconEntity::class,
        IconPackEntity::class,
        WidgetEntity::class,
        CustomAttributeEntity::class,
        SearchActionEntity::class,
        ColorsEntity::class,
        PluginEntity::class,
        ShapesEntity::class,
        TransparenciesEntity::class,
        TypographyEntity::class,
    ], version = 30, exportSchema = true
)
@TypeConverters(ComponentNameConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun weatherDao(): WeatherDao
    abstract fun iconDao(): IconDao

    abstract fun searchableDao(): SearchableDao
    abstract fun widgetDao(): WidgetDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun backupDao(): BackupRestoreDao
    abstract fun customAttrsDao(): CustomAttrsDao

    abstract fun searchActionDao(): SearchActionDao

    abstract fun themeDao(): ThemeDao

    abstract fun pluginDao(): PluginDao

    companion object {
        private var _instance: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase {
            val instance = _instance
                ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "room")
                    //.fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL(
                                "INSERT INTO `SearchAction` (`position`, `type`) VALUES" +
                                        "(0, 'call')," +
                                        "(1, 'message')," +
                                        "(2, 'email')," +
                                        "(3, 'contact')," +
                                        "(4, 'alarm')," +
                                        "(5, 'timer')," +
                                        "(6, 'calendar')," +
                                        "(7, 'website')," +
                                        "(8, 'websearch')"
                            )

                            db.execSQL(
                                "INSERT INTO `SearchAction` (`position`, `type`, `data`, `label`, `color`, `icon`, `customIcon`, `options`) " +
                                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?, ?, ?)",
                                arrayOf<Any?>(
                                    9,
                                    "url",
                                    context.getString(R.string.default_websearch_2_url),
                                    context.getString(R.string.default_websearch_2_name),
                                    0,
                                    0,
                                    null,
                                    null,
                                    10,
                                    "url",
                                    context.getString(R.string.default_websearch_3_url),
                                    context.getString(R.string.default_websearch_3_name),
                                    0,
                                    0,
                                    null,
                                    null,
                                )
                            )

                            val defaultParentId = WidgetScreenTarget.Default.scopeId
                            db.execSQL(
                                "INSERT INTO Widget (`type`, `position`, `id`, `parentId`) VALUES " +
                                        "('weather', 0, ?, ?)," +
                                        "('music', 1, ?, ?)," +
                                        "('calendar', 2, ?, ?);",
                                arrayOf(
                                    UUID.randomUUID().toBytes(),
                                    defaultParentId.toBytes(),
                                    UUID.randomUUID().toBytes(),
                                    defaultParentId.toBytes(),
                                    UUID.randomUUID().toBytes(),
                                    defaultParentId.toBytes()
                                )
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
                        Migration_21_22(),
                        Migration_22_23(),
                        Migration_23_24(),
                        Migration_24_25(),
                        Migration_25_26(),
                        Migration_27_28(),
                        Migration_28_29(),
                        Migration_29_30(),
                    ).build()
            if (_instance == null) _instance = instance
            return instance
        }
    }
}
