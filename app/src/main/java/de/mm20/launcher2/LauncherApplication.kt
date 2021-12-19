package de.mm20.launcher2

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import de.mm20.launcher2.applications.applicationsModule
import de.mm20.launcher2.badges.badgesModule
import de.mm20.launcher2.calculator.calculatorModule
import de.mm20.launcher2.calendar.calendarModule
import de.mm20.launcher2.contacts.contactsModule
import de.mm20.launcher2.debug.Debug
import de.mm20.launcher2.favorites.favoritesModule
import de.mm20.launcher2.files.filesModule
import de.mm20.launcher2.hiddenitems.hiddenItemsModule
import de.mm20.launcher2.icons.iconsModule
import de.mm20.launcher2.music.musicModule
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.preferences.Themes
import de.mm20.launcher2.search.searchModule
import de.mm20.launcher2.unitconverter.unitConverterModule
import de.mm20.launcher2.websites.websitesModule
import de.mm20.launcher2.widgets.widgetsModule
import de.mm20.launcher2.wikipedia.wikipediaModule
import de.mm20.launcher2.database.databaseModule
import kotlinx.coroutines.*
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.text.Collator
import kotlin.coroutines.CoroutineContext

class LauncherApplication : Application(), CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + SupervisorJob()

    override fun onCreate() {
        super.onCreate()
        Debug()
        LauncherPreferences.initialize(this)

        val theme = LauncherPreferences.instance.theme
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                Themes.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO // light
                Themes.DARK -> AppCompatDelegate.MODE_NIGHT_YES // dark, black
                Themes.AUTO -> AppCompatDelegate.MODE_NIGHT_AUTO // auto
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM //system
            }
        )

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@LauncherApplication)
            modules(
                listOf(
                    applicationsModule,
                    calculatorModule,
                    badgesModule,
                    calendarModule,
                    contactsModule,
                    databaseModule,
                    favoritesModule,
                    filesModule,
                    hiddenItemsModule,
                    iconsModule,
                    musicModule,
                    searchModule,
                    unitConverterModule,
                    websitesModule,
                    widgetsModule,
                    wikipediaModule
                )
            )
        }
    }

    companion object {

        val collator: Collator by lazy {
            Collator.getInstance().apply { strength = Collator.SECONDARY }
        }
    }

}

object PermissionRequests {
    const val CALENDAR = 309
    const val LOCATION = 410
    const val ALL = 666
}
