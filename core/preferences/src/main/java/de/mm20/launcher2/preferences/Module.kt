package de.mm20.launcher2.preferences

import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.preferences.applock.AppLockSettings
import de.mm20.launcher2.preferences.feed.FeedSettings
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import de.mm20.launcher2.preferences.protection.ProtectionSettings
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
import de.mm20.launcher2.preferences.ui.ContextProfileSettings
import de.mm20.launcher2.preferences.ui.AgentSettings
import de.mm20.launcher2.preferences.ui.DesktopModeSettings
import de.mm20.launcher2.preferences.ui.DynamicIslandSettings
import de.mm20.launcher2.preferences.ui.WebAppBrowsingSettings
import de.mm20.launcher2.preferences.ui.WebAppsPanelSettings
import de.mm20.launcher2.preferences.ui.FloatingLauncherSettings
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.preferences.battery.BatterySettings
import de.mm20.launcher2.preferences.tweaks.TweaksSettings
import de.mm20.launcher2.preferences.ui.GestureSettings
import de.mm20.launcher2.preferences.ui.IconSettings
import de.mm20.launcher2.preferences.ui.LocaleSettings
import de.mm20.launcher2.preferences.ui.SearchUiSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.preferences.ui.UiState
import de.mm20.launcher2.preferences.ui.PerformanceSettings
import de.mm20.launcher2.preferences.ui.ShutterSettings
import de.mm20.launcher2.preferences.ui.WallpaperSettings
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
    factory { FeedSettings(get()) }
    factory { FreezeSettings(get()) }
    factory { WallpaperSettings(get()) }
    factory { ProtectionSettings(get()) }
    factory { AppLockSettings(get()) }
    factory { PerformanceSettings(get()) }
    factory { ShutterSettings(get()) }
    factory { FloatingLauncherSettings(get()) }
    factory { ContextProfileSettings(get()) }
    factory { DesktopModeSettings(get()) }
    factory { AgentSettings(get()) }
    factory { DynamicIslandSettings(get()) }
    factory { WebAppsPanelSettings(get()) }
    factory { WebAppBrowsingSettings(get()) }
    factory { CommsSettings(get()) }
    factory { TweaksSettings(get()) }
    factory { BatterySettings(get()) }
}