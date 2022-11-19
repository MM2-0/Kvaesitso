package de.mm20.launcher2.ui.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.navArgument
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import de.mm20.launcher2.licenses.AppLicense
import de.mm20.launcher2.licenses.OpenSourceLicenses
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.locals.LocalCardStyle
import de.mm20.launcher2.ui.locals.LocalNavController
import de.mm20.launcher2.ui.locals.LocalWallpaperColors
import de.mm20.launcher2.ui.settings.about.AboutSettingsScreen
import de.mm20.launcher2.ui.settings.accounts.AccountsSettingsScreen
import de.mm20.launcher2.ui.settings.appearance.AppearanceSettingsScreen
import de.mm20.launcher2.ui.settings.backup.BackupSettingsScreen
import de.mm20.launcher2.ui.settings.badges.BadgeSettingsScreen
import de.mm20.launcher2.ui.settings.buildinfo.BuildInfoSettingsScreen
import de.mm20.launcher2.ui.settings.calendarwidget.CalendarWidgetSettingsScreen
import de.mm20.launcher2.ui.settings.cards.CardsSettingsScreen
import de.mm20.launcher2.ui.settings.clockwidget.ClockWidgetSettingsScreen
import de.mm20.launcher2.ui.settings.colorscheme.ColorSchemeSettingsScreen
import de.mm20.launcher2.ui.settings.colorscheme.CustomColorSchemeSettingsScreen
import de.mm20.launcher2.ui.settings.crashreporter.CrashReportScreen
import de.mm20.launcher2.ui.settings.crashreporter.CrashReporterScreen
import de.mm20.launcher2.ui.settings.debug.DebugSettingsScreen
import de.mm20.launcher2.ui.settings.easteregg.EasterEggSettingsScreen
import de.mm20.launcher2.ui.settings.favorites.FavoritesSettingsScreen
import de.mm20.launcher2.ui.settings.filesearch.FileSearchSettingsScreen
import de.mm20.launcher2.ui.settings.hiddenitems.HiddenItemsSettingsScreen
import de.mm20.launcher2.ui.settings.license.LicenseScreen
import de.mm20.launcher2.ui.settings.log.LogScreen
import de.mm20.launcher2.ui.settings.main.MainSettingsScreen
import de.mm20.launcher2.ui.settings.musicwidget.MusicWidgetSettingsScreen
import de.mm20.launcher2.ui.settings.search.SearchSettingsScreen
import de.mm20.launcher2.ui.settings.searchactions.SearchActionsSettingsScreen
import de.mm20.launcher2.ui.settings.unitconverter.UnitConverterSettingsScreen
import de.mm20.launcher2.ui.settings.weatherwidget.WeatherWidgetSettingsScreen
import de.mm20.launcher2.ui.settings.widgets.WidgetsSettingsScreen
import de.mm20.launcher2.ui.settings.wikipedia.WikipediaSettingsScreen
import de.mm20.launcher2.ui.theme.LauncherTheme
import de.mm20.launcher2.ui.theme.wallpaperColorsAsState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.android.ext.android.inject
import java.net.URLDecoder

class SettingsActivity : BaseActivity() {

    private val dataStore: LauncherDataStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberAnimatedNavController()

            LaunchedEffect(intent) {
                intent.getStringExtra(EXTRA_ROUTE)
                    ?.let { navController.navigate(it) }
            }

            val cardStyle by remember {
                dataStore.data.map { it.cards }.distinctUntilChanged()
            }.collectAsState(
                Settings.CardSettings.getDefaultInstance()
            )
            val wallpaperColors by wallpaperColorsAsState()
            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalCardStyle provides cardStyle,
                LocalWallpaperColors provides wallpaperColors,
            ) {
                LauncherTheme {
                    AnimatedNavHost(
                        navController = navController,
                        startDestination = "settings",
                        exitTransition = { fadeOut(tween(300, 300)) },
                        enterTransition = { fadeIn(tween(200)) },
                        popEnterTransition = { fadeIn(tween(0)) },
                        popExitTransition = { fadeOut(tween(200)) },
                    ) {
                        composable("settings") {
                            MainSettingsScreen()
                        }
                        composable("settings/appearance") {
                            AppearanceSettingsScreen()
                        }
                        composable("settings/appearance/colorscheme") {
                            ColorSchemeSettingsScreen()
                        }
                        composable("settings/appearance/colorscheme/custom") {
                            CustomColorSchemeSettingsScreen()
                        }
                        composable("settings/appearance/cards") {
                            CardsSettingsScreen()
                        }
                        composable("settings/search") {
                            SearchSettingsScreen()
                        }
                        composable("settings/search/unitconverter") {
                            UnitConverterSettingsScreen()
                        }
                        composable("settings/search/wikipedia") {
                            WikipediaSettingsScreen()
                        }
                        composable("settings/search/files") {
                            FileSearchSettingsScreen()
                        }
                        composable("settings/search/searchactions") {
                            SearchActionsSettingsScreen()
                        }
                        composable("settings/search/hiddenitems") {
                            HiddenItemsSettingsScreen()
                        }
                        composable("settings/widgets") {
                            WidgetsSettingsScreen()
                        }
                        composable("settings/widgets/weather") {
                            WeatherWidgetSettingsScreen()
                        }
                        composable("settings/widgets/music") {
                            MusicWidgetSettingsScreen()
                        }
                        composable("settings/widgets/calendar") {
                            CalendarWidgetSettingsScreen()
                        }
                        composable("settings/widgets/clock") {
                            ClockWidgetSettingsScreen()
                        }
                        composable("settings/favorites") {
                            FavoritesSettingsScreen()
                        }
                        composable("settings/badges") {
                            BadgeSettingsScreen()
                        }
                        composable("settings/accounts") {
                            AccountsSettingsScreen()
                        }
                        composable("settings/about") {
                            AboutSettingsScreen()
                        }
                        composable("settings/about/buildinfo") {
                            BuildInfoSettingsScreen()
                        }
                        composable("settings/about/easteregg") {
                            EasterEggSettingsScreen()
                        }
                        composable("settings/debug") {
                            DebugSettingsScreen()
                        }
                        composable("settings/backup") {
                            BackupSettingsScreen()
                        }
                        composable("settings/debug/crashreporter") {
                            CrashReporterScreen()
                        }
                        composable("settings/debug/logs") {
                            LogScreen()
                        }
                        composable(
                            "settings/debug/crashreporter/report?fileName={fileName}",
                            arguments = listOf(navArgument("fileName") {
                                nullable = false
                            })
                        ) {
                            val fileName = it.arguments?.getString("fileName")
                                ?.let {
                                    URLDecoder.decode(it, "utf8")
                                }
                            CrashReportScreen(fileName!!)
                        }
                        composable(
                            "settings/license?library={libraryName}",
                            arguments = listOf(navArgument("libraryName") {
                                nullable = true
                            })
                        ) {
                            val libraryName = it.arguments?.getString("libraryName")
                            val library = remember(libraryName) {
                                if (libraryName == null) {
                                    AppLicense.get(this@SettingsActivity)
                                } else {
                                    OpenSourceLicenses.first { it.name == libraryName }
                                }
                            }
                            LicenseScreen(library)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "de.mm20.launcher2.settings.ROUTE"
    }
}