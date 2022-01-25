package de.mm20.launcher2.ui.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation.navArgument
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import de.mm20.launcher2.licenses.AppLicense
import de.mm20.launcher2.licenses.OpenSourceLicenses
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.LegacyLauncherTheme
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.locals.LocalNavController
import de.mm20.launcher2.ui.settings.about.AboutSettingsScreen
import de.mm20.launcher2.ui.settings.appearance.AppearanceSettingsScreen
import de.mm20.launcher2.ui.settings.badges.BadgeSettingsScreen
import de.mm20.launcher2.ui.settings.calendarwidget.CalendarWidgetSettingsScreen
import de.mm20.launcher2.ui.settings.clockwidget.ClockWidgetSettingsScreen
import de.mm20.launcher2.ui.settings.debug.DebugSettingsScreen
import de.mm20.launcher2.ui.settings.license.LicenseScreen
import de.mm20.launcher2.ui.settings.main.MainSettingsScreen
import de.mm20.launcher2.ui.settings.musicwidget.MusicWidgetSettingsScreen
import de.mm20.launcher2.ui.settings.search.SearchSettingsScreen
import de.mm20.launcher2.ui.settings.accounts.AccountsSettingsScreen
import de.mm20.launcher2.ui.settings.buildinfo.BuildInfoSettingsScreen
import de.mm20.launcher2.ui.settings.filesearch.FileSearchSettingsScreen
import de.mm20.launcher2.ui.settings.weatherwidget.WeatherWidgetSettingsScreen
import de.mm20.launcher2.ui.settings.websearch.WebSearchSettingsScreen
import de.mm20.launcher2.ui.settings.widgets.WidgetsSettingsScreen
import de.mm20.launcher2.ui.settings.wikipedia.WikipediaSettingsScreen

class SettingsActivity : BaseActivity() {

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberAnimatedNavController()
            CompositionLocalProvider(LocalNavController provides navController) {
                LegacyLauncherTheme {
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
                        composable("settings/debug") {
                            DebugSettingsScreen()
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