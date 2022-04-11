package de.mm20.launcher2.ui.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.*
import androidx.navigation.navArgument
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import de.mm20.launcher2.licenses.AppLicense
import de.mm20.launcher2.licenses.OpenSourceLicenses
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.LauncherTheme
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.locals.LocalCardStyle
import de.mm20.launcher2.ui.locals.LocalNavController
import de.mm20.launcher2.ui.settings.about.AboutSettingsScreen
import de.mm20.launcher2.ui.settings.accounts.AccountsSettingsScreen
import de.mm20.launcher2.ui.settings.appearance.AppearanceSettingsScreen
import de.mm20.launcher2.ui.settings.badges.BadgeSettingsScreen
import de.mm20.launcher2.ui.settings.buildinfo.BuildInfoSettingsScreen
import de.mm20.launcher2.ui.settings.calendarwidget.CalendarWidgetSettingsScreen
import de.mm20.launcher2.ui.settings.cards.CardsSettingsScreen
import de.mm20.launcher2.ui.settings.clockwidget.ClockWidgetSettingsScreen
import de.mm20.launcher2.ui.settings.colorscheme.ColorSchemeSettingsScreen
import de.mm20.launcher2.ui.settings.crashreporter.CrashReportScreen
import de.mm20.launcher2.ui.settings.crashreporter.CrashReporterScreen
import de.mm20.launcher2.ui.settings.debug.DebugSettingsScreen
import de.mm20.launcher2.ui.settings.easteregg.EasterEggSettingsScreen
import de.mm20.launcher2.ui.settings.filesearch.FileSearchSettingsScreen
import de.mm20.launcher2.ui.settings.license.LicenseScreen
import de.mm20.launcher2.ui.settings.main.MainSettingsScreen
import de.mm20.launcher2.ui.settings.musicwidget.MusicWidgetSettingsScreen
import de.mm20.launcher2.ui.settings.search.SearchSettingsScreen
import de.mm20.launcher2.ui.settings.weatherwidget.WeatherWidgetSettingsScreen
import de.mm20.launcher2.ui.settings.websearch.WebSearchSettingsScreen
import de.mm20.launcher2.ui.settings.widgets.WidgetsSettingsScreen
import de.mm20.launcher2.ui.settings.wikipedia.WikipediaSettingsScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.android.ext.android.inject

class SettingsActivity : BaseActivity() {

    private val dataStore: LauncherDataStore by inject()

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberAnimatedNavController()

            LaunchedEffect(intent) {
                intent.getStringExtra("de.mm20.launcher2.settings.ROUTE")
                    ?.let { navController.navigate(it) }
            }

            val cardStyle by remember {
                dataStore.data.map { it.cards }.distinctUntilChanged()
            }.collectAsState(
                Settings.CardSettings.getDefaultInstance()
            )
            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalCardStyle provides cardStyle
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
                        composable("settings/appearance/cards") {
                            CardsSettingsScreen()
                        }
                        composable("settings/search") {
                            SearchSettingsScreen()
                        }
                        composable("settings/search/wikipedia") {
                            WikipediaSettingsScreen()
                        }
                        composable("settings/search/files") {
                            FileSearchSettingsScreen()
                        }
                        composable("settings/search/websearch") {
                            WebSearchSettingsScreen()
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
                        composable("settings/debug/crashreporter") {
                            CrashReporterScreen()
                        }
                        composable("settings/debug/crashreporter/report?fileName={fileName}",
                            arguments = listOf(navArgument("fileName") {
                                nullable = false
                            })
                        ) {
                            val fileName = it.arguments?.getString("fileName")
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
}