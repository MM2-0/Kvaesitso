package de.mm20.launcher2.preferences

import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.preferences.search.ContactSearchSettings
import de.mm20.launcher2.preferences.media.MediaSettings
import de.mm20.launcher2.preferences.search.CalculatorSearchSettings
import de.mm20.launcher2.preferences.search.CalendarSearchSettings
import de.mm20.launcher2.preferences.search.FavoritesSettings
import de.mm20.launcher2.preferences.search.FileSearchSettings
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import de.mm20.launcher2.preferences.search.RankingSettings
import de.mm20.launcher2.preferences.search.SearchFilterSettings
import de.mm20.launcher2.preferences.search.ShortcutSearchSettings
import de.mm20.launcher2.preferences.search.UnitConverterSettings
import de.mm20.launcher2.preferences.search.WebsiteSearchSettings
import de.mm20.launcher2.preferences.search.WikipediaSearchSettings
import de.mm20.launcher2.preferences.ui.BadgeSettings
import de.mm20.launcher2.preferences.ui.ClockWidgetSettings
import de.mm20.launcher2.preferences.ui.GestureSettings
import de.mm20.launcher2.preferences.ui.IconSettings
import de.mm20.launcher2.preferences.ui.LocaleSettings
import de.mm20.launcher2.preferences.ui.SearchUiSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.preferences.ui.UiState
import de.mm20.launcher2.preferences.weather.WeatherSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val preferencesModule = module {
    single { LauncherDataStore(androidContext()) }
    factory<Backupable>(named<LauncherDataStore>()) { get<LauncherDataStore>() }
    factory { MediaSettings(get()) }
    factory { ContactSearchSettings(get()) }
    factory { FileSearchSettings(get()) }
    factory { UnitConverterSettings(get()) }
    factory { BadgeSettings(get()) }
    factory { UiSettings(get()) }
    factory { ShortcutSearchSettings(get()) }
    factory { FavoritesSettings(get()) }
    factory { WikipediaSearchSettings(get()) }
    factory { IconSettings(get()) }
    factory { RankingSettings(get()) }
    factory { CalendarSearchSettings(get()) }
    factory { WebsiteSearchSettings(get()) }
    factory { UiState(get()) }
    factory { SearchUiSettings(get()) }
    factory { WeatherSettings(get()) }
    factory { GestureSettings(get()) }
    factory { CalculatorSearchSettings(get()) }
    factory { ClockWidgetSettings(get()) }
    factory { LocationSearchSettings(get()) }
    factory { SearchFilterSettings(get()) }
    factory { LocaleSettings(get()) }
}