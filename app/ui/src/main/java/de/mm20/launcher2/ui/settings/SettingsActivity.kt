package de.mm20.launcher2.ui.settings

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.mm20.launcher2.licenses.AppLicense
import de.mm20.launcher2.licenses.OpenSourceLicenses
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCurrentTime
import de.mm20.launcher2.ui.base.ProvideSettings
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalNavController
import de.mm20.launcher2.ui.locals.LocalWallpaperColors
import de.mm20.launcher2.ui.overlays.OverlayHost
import de.mm20.launcher2.ui.settings.about.AboutSettingsScreen
import de.mm20.launcher2.ui.settings.appearance.AppearanceSettingsScreen
import de.mm20.launcher2.ui.settings.backup.BackupSettingsScreen
import de.mm20.launcher2.ui.settings.buildinfo.BuildInfoSettingsScreen
import de.mm20.launcher2.ui.settings.cards.CardsSettingsScreen
import de.mm20.launcher2.ui.settings.colorscheme.ThemeSettingsScreen
import de.mm20.launcher2.ui.settings.colorscheme.ThemesSettingsScreen
import de.mm20.launcher2.ui.settings.crashreporter.CrashReportScreen
import de.mm20.launcher2.ui.settings.crashreporter.CrashReporterScreen
import de.mm20.launcher2.ui.settings.debug.DebugSettingsScreen
import de.mm20.launcher2.ui.settings.easteregg.EasterEggSettingsScreen
import de.mm20.launcher2.ui.settings.favorites.FavoritesSettingsScreen
import de.mm20.launcher2.ui.settings.filesearch.FileSearchSettingsScreen
import de.mm20.launcher2.ui.settings.gestures.GestureSettingsScreen
import de.mm20.launcher2.ui.settings.hiddenitems.HiddenItemsSettingsScreen
import de.mm20.launcher2.ui.settings.homescreen.HomescreenSettingsScreen
import de.mm20.launcher2.ui.settings.icons.IconsSettingsScreen
import de.mm20.launcher2.ui.settings.integrations.IntegrationsSettingsScreen
import de.mm20.launcher2.ui.settings.license.LicenseScreen
import de.mm20.launcher2.ui.settings.locations.LocationsSettingsScreen
import de.mm20.launcher2.ui.settings.log.LogScreen
import de.mm20.launcher2.ui.settings.main.MainSettingsScreen
import de.mm20.launcher2.ui.settings.media.MediaIntegrationSettingsScreen
import de.mm20.launcher2.ui.settings.plugins.PluginSettingsScreen
import de.mm20.launcher2.ui.settings.plugins.PluginsSettingsScreen
import de.mm20.launcher2.ui.settings.search.SearchSettingsScreen
import de.mm20.launcher2.ui.settings.searchactions.SearchActionsSettingsScreen
import de.mm20.launcher2.ui.settings.tags.TagsSettingsScreen
import de.mm20.launcher2.ui.settings.unitconverter.SupportedUnitsScreen
import de.mm20.launcher2.ui.settings.unitconverter.UnitConverterSettingsScreen
import de.mm20.launcher2.ui.settings.weather.WeatherIntegrationSettingsScreen
import de.mm20.launcher2.ui.settings.wikipedia.WikipediaSettingsScreen
import de.mm20.launcher2.ui.theme.LauncherTheme
import de.mm20.launcher2.ui.theme.wallpaperColorsAsState
import java.net.URLDecoder
import java.util.UUID

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val navController = rememberNavController()

            LaunchedEffect(intent) {
                intent.getStringExtra(EXTRA_ROUTE)
                    ?.let { navController.navigate(it) }
            }
            val wallpaperColors by wallpaperColorsAsState()
            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalWallpaperColors provides wallpaperColors,
            ) {
                ProvideSettings {
                    ProvideCurrentTime {
                        LauncherTheme {
                            val systemBarColor = MaterialTheme.colorScheme.surfaceDim
                            val systemBarColorAlt = MaterialTheme.colorScheme.onSurface
                            val isDarkTheme = LocalDarkTheme.current
                            LaunchedEffect(isDarkTheme, systemBarColor, systemBarColorAlt) {
                                enableEdgeToEdge(
                                    if (isDarkTheme) SystemBarStyle.dark(systemBarColor.toArgb())
                                    else SystemBarStyle.light(systemBarColor.toArgb(), systemBarColorAlt.toArgb())
                                )
                            }
                            OverlayHost {
                                NavHost(
                                    navController = navController,
                                    startDestination = "settings",
                                    exitTransition = {
                                        fadeOut() + scaleOut(targetScale = 0.5f)
                                    },
                                    enterTransition = {
                                        slideInHorizontally { it }
                                    },
                                    popEnterTransition = {
                                        fadeIn() + scaleIn(initialScale = 0.5f)
                                    },
                                    popExitTransition = {
                                        slideOutHorizontally { it }
                                    },
                                ) {
                                    composable("settings") {
                                        MainSettingsScreen()
                                    }
                                    composable("settings/appearance") {
                                        AppearanceSettingsScreen()
                                    }
                                    composable("settings/homescreen") {
                                        HomescreenSettingsScreen()
                                    }
                                    composable("settings/icons") {
                                        IconsSettingsScreen()
                                    }
                                    composable("settings/appearance/themes") {
                                        ThemesSettingsScreen()
                                    }
                                    composable(
                                        "settings/appearance/themes/{id}",
                                        arguments = listOf(navArgument("id") {
                                            nullable = false
                                        })
                                    ) {
                                        val id = it.arguments?.getString("id")?.let {
                                            UUID.fromString(it)
                                        } ?: return@composable
                                        ThemeSettingsScreen(id)
                                    }
                                    composable("settings/appearance/cards") {
                                        CardsSettingsScreen()
                                    }
                                    composable("settings/search") {
                                        SearchSettingsScreen()
                                    }
                                    composable("settings/gestures") {
                                        GestureSettingsScreen()
                                    }
                                    composable("settings/search/unitconverter") {
                                        UnitConverterSettingsScreen()
                                    }
                                    composable("settings/search/unitconverter/units") {
                                        SupportedUnitsScreen()
                                    }
                                    composable("settings/search/wikipedia") {
                                        WikipediaSettingsScreen()
                                    }
                                    composable("settings/search/locations") {
                                        LocationsSettingsScreen()
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
                                    composable("settings/search/tags") {
                                        TagsSettingsScreen()
                                    }
                                    composable(ROUTE_WEATHER_INTEGRATION) {
                                        WeatherIntegrationSettingsScreen()
                                    }
                                    composable(ROUTE_MEDIA_INTEGRATION) {
                                        MediaIntegrationSettingsScreen()
                                    }
                                    composable("settings/favorites") {
                                        FavoritesSettingsScreen()
                                    }
                                    composable("settings/integrations") {
                                        IntegrationsSettingsScreen()
                                    }
                                    composable("settings/plugins") {
                                        PluginsSettingsScreen()
                                    }
                                    composable("settings/plugins/{id}") {
                                        PluginSettingsScreen(it.arguments?.getString("id") ?: return@composable)
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
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "de.mm20.launcher2.settings.ROUTE"
        const val ROUTE_WEATHER_INTEGRATION = "settings/integrations/weather"
        const val ROUTE_MEDIA_INTEGRATION = "settings/integrations/media"
    }
}