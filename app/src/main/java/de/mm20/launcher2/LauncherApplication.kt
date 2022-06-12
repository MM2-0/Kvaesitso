package de.mm20.launcher2

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import de.mm20.launcher2.accounts.accountsModule
import de.mm20.launcher2.applications.applicationsModule
import de.mm20.launcher2.appshortcuts.appShortcutsModule
import de.mm20.launcher2.backup.backupModule
import de.mm20.launcher2.badges.badgesModule
import de.mm20.launcher2.calculator.calculatorModule
import de.mm20.launcher2.calendar.calendarModule
import de.mm20.launcher2.contacts.contactsModule
import de.mm20.launcher2.debug.Debug
import de.mm20.launcher2.favorites.favoritesModule
import de.mm20.launcher2.files.filesModule
import de.mm20.launcher2.icons.iconsModule
import de.mm20.launcher2.music.musicModule
import de.mm20.launcher2.search.searchModule
import de.mm20.launcher2.unitconverter.unitConverterModule
import de.mm20.launcher2.websites.websitesModule
import de.mm20.launcher2.widgets.widgetsModule
import de.mm20.launcher2.wikipedia.wikipediaModule
import de.mm20.launcher2.database.databaseModule
import de.mm20.launcher2.notifications.notificationsModule
import de.mm20.launcher2.permissions.permissionsModule
import de.mm20.launcher2.preferences.preferencesModule
import de.mm20.launcher2.weather.weatherModule
import kotlinx.coroutines.*
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.text.Collator
import kotlin.coroutines.CoroutineContext

class LauncherApplication : Application(), CoroutineScope, ImageLoaderFactory {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + SupervisorJob()

    override fun onCreate() {
        super.onCreate()
        Debug()

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@LauncherApplication)
            modules(
                listOf(
                    accountsModule,
                    applicationsModule,
                    appShortcutsModule,
                    calculatorModule,
                    backupModule,
                    badgesModule,
                    calendarModule,
                    contactsModule,
                    databaseModule,
                    favoritesModule,
                    filesModule,
                    iconsModule,
                    musicModule,
                    notificationsModule,
                    permissionsModule,
                    preferencesModule,
                    searchModule,
                    unitConverterModule,
                    weatherModule,
                    websitesModule,
                    widgetsModule,
                    wikipediaModule
                )
            )
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(applicationContext)
            .componentRegistry {
                add(SvgDecoder(applicationContext))
            }
            .crossfade(true)
            .crossfade(200)
            .build()
    }

    companion object {

        val collator: Collator by lazy {
            Collator.getInstance().apply { strength = Collator.SECONDARY }
        }
    }

}