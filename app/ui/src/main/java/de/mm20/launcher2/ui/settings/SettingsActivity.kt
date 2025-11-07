package de.mm20.launcher2.ui.settings

import android.content.Intent
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.toRoute
import de.mm20.launcher2.licenses.AppLicense
import de.mm20.launcher2.licenses.OpenSourceLicenses
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalNavController
import de.mm20.launcher2.ui.locals.LocalWallpaperColors
import de.mm20.launcher2.ui.overlays.OverlayHost
import de.mm20.launcher2.ui.settings.about.AboutSettingsScreen
import de.mm20.launcher2.ui.settings.appearance.AppearanceSettingsScreen
import de.mm20.launcher2.ui.settings.appearance.ExportThemeSettingsScreen
import de.mm20.launcher2.ui.settings.appearance.ImportThemeSettingsRoute
import de.mm20.launcher2.ui.settings.appearance.ImportThemeSettingsScreen
import de.mm20.launcher2.ui.settings.backup.BackupSettingsScreen
import de.mm20.launcher2.ui.settings.breezyweather.BreezyWeatherSettingsScreen
import de.mm20.launcher2.ui.settings.buildinfo.BuildInfoSettingsScreen
import de.mm20.launcher2.ui.settings.calendarsearch.CalendarProviderSettingsScreen
import de.mm20.launcher2.ui.settings.calendarsearch.CalendarSearchSettingsScreen
import de.mm20.launcher2.ui.settings.colorscheme.ColorSchemeSettingsScreen
import de.mm20.launcher2.ui.settings.colorscheme.ColorSchemesSettingsScreen
import de.mm20.launcher2.ui.settings.contacts.ContactsSettingsScreen
import de.mm20.launcher2.ui.settings.crashreporter.CrashReportScreen
import de.mm20.launcher2.ui.settings.crashreporter.CrashReporterScreen
import de.mm20.launcher2.ui.settings.debug.DebugSettingsScreen
import de.mm20.launcher2.ui.settings.easteregg.EasterEggSettingsScreen
import de.mm20.launcher2.ui.settings.favorites.FavoritesSettingsScreen
import de.mm20.launcher2.ui.settings.filesearch.FileSearchSettingsScreen
import de.mm20.launcher2.ui.settings.filterbar.FilterBarSettingsScreen
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
import de.mm20.launcher2.ui.settings.nextcloud.NextcloudSettingsScreen
import de.mm20.launcher2.ui.settings.osm.OsmSettingsScreen
import de.mm20.launcher2.ui.settings.owncloud.OwncloudSettingsScreen
import de.mm20.launcher2.ui.settings.plugins.PluginSettingsScreen
import de.mm20.launcher2.ui.settings.plugins.PluginsSettingsScreen
import de.mm20.launcher2.ui.settings.search.SearchSettingsScreen
import de.mm20.launcher2.ui.settings.searchactions.SearchActionsSettingsScreen
import de.mm20.launcher2.ui.settings.shapes.ShapeSchemeSettingsScreen
import de.mm20.launcher2.ui.settings.shapes.ShapeSchemesSettingsScreen
import de.mm20.launcher2.ui.settings.tags.TagsSettingsScreen
import de.mm20.launcher2.ui.settings.tasks.TasksIntegrationSettingsScreen
import de.mm20.launcher2.ui.settings.transparencies.TransparencySchemeSettingsRoute
import de.mm20.launcher2.ui.settings.transparencies.TransparencySchemeSettingsScreen
import de.mm20.launcher2.ui.settings.transparencies.TransparencySchemesSettingsRoute
import de.mm20.launcher2.ui.settings.transparencies.TransparencySchemesSettingsScreen
import de.mm20.launcher2.ui.settings.typography.TypographiesSettingsRoute
import de.mm20.launcher2.ui.settings.typography.TypographiesSettingsScreen
import de.mm20.launcher2.ui.settings.typography.TypographySettingsRoute
import de.mm20.launcher2.ui.settings.typography.TypographySettingsScreen
import de.mm20.launcher2.ui.settings.unitconverter.UnitConverterHelpSettingsScreen
import de.mm20.launcher2.ui.settings.unitconverter.UnitConverterSettingsScreen
import de.mm20.launcher2.ui.settings.weather.WeatherIntegrationSettingsScreen
import de.mm20.launcher2.ui.settings.wikipedia.WikipediaSettingsScreen
import de.mm20.launcher2.ui.theme.LauncherTheme
import de.mm20.launcher2.ui.theme.wallpaperColorsAsState
import java.net.URLDecoder
import java.util.UUID

class SettingsActivity : BaseActivity() {

    private var route by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val newRoute = getStartRoute(intent)
        route = newRoute

        setContent {
            val navController = rememberNavController()
            val wallpaperColors by wallpaperColorsAsState()
            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalWallpaperColors provides wallpaperColors,
            ) {
                ProvideCompositionLocals {
                    LauncherTheme {
                        val systemBarColor = MaterialTheme.colorScheme.surfaceDim
                        val systemBarColorAlt = MaterialTheme.colorScheme.onSurface
                        val isDarkTheme = LocalDarkTheme.current
                        LaunchedEffect(isDarkTheme, systemBarColor, systemBarColorAlt) {
                            enableEdgeToEdge(
                                if (isDarkTheme) SystemBarStyle.dark(systemBarColor.toArgb())
                                else SystemBarStyle.light(
                                    systemBarColor.toArgb(),
                                    systemBarColorAlt.toArgb()
                                )
                            )
                        }
                        OverlayHost(
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            NavHost(
                                modifier = Modifier.fillMaxSize(),
                                navController = navController,
                                startDestination = route ?: "settings",
                                exitTransition = {
                                    slideOutHorizontally { -it / 4 }
                                },
                                enterTransition = {
                                    slideInHorizontally { it / 2 } + scaleIn(initialScale = 0.9f) + fadeIn()
                                },
                                popEnterTransition = {
                                    slideInHorizontally { -it / 4 }
                                },
                                popExitTransition = {
                                    slideOutHorizontally { it / 2 } + scaleOut(targetScale = 0.9f) + fadeOut()
                                },
                            ) {
                                composable("settings") {
                                    MainSettingsScreen()
                                }
                                composable("settings/appearance") {
                                    AppearanceSettingsScreen()
                                }
                                composable("settings/appearance/export") {
                                    ExportThemeSettingsScreen()
                                }
                                composable<ImportThemeSettingsRoute> {
                                    val route: ImportThemeSettingsRoute = it.toRoute() ?: return@composable
                                    ImportThemeSettingsScreen(route.fromUri.toUri())
                                }
                                composable("settings/homescreen") {
                                    HomescreenSettingsScreen()
                                }
                                composable("settings/icons") {
                                    IconsSettingsScreen()
                                }
                                composable("settings/appearance/colors") {
                                    ColorSchemesSettingsScreen()
                                }
                                composable(
                                    "settings/appearance/colors/{id}",
                                    arguments = listOf(navArgument("id") {
                                        nullable = false
                                    })
                                ) {
                                    val id = it.arguments?.getString("id")?.let {
                                        UUID.fromString(it)
                                    } ?: return@composable
                                    ColorSchemeSettingsScreen(id)
                                }
                                composable("settings/appearance/shapes") {
                                    ShapeSchemesSettingsScreen()
                                }
                                composable(
                                    "settings/appearance/shapes/{id}",
                                    arguments = listOf(navArgument("id") {
                                        nullable = false
                                    })
                                ) {
                                    val id = it.arguments?.getString("id")?.let {
                                        UUID.fromString(it)
                                    } ?: return@composable
                                    ShapeSchemeSettingsScreen(id)
                                }
                                composable<TransparencySchemesSettingsRoute> {
                                    TransparencySchemesSettingsScreen()
                                }
                                composable<TransparencySchemeSettingsRoute> {
                                    val route: TransparencySchemeSettingsRoute = it.toRoute()
                                        ?: return@composable
                                    TransparencySchemeSettingsScreen(UUID.fromString(route.id))
                                }
                                composable<TypographiesSettingsRoute> {
                                    TypographiesSettingsScreen()
                                }
                                composable<TypographySettingsRoute> {
                                    val route: TypographySettingsRoute = it.toRoute()
                                        ?: return@composable
                                    TypographySettingsScreen(UUID.fromString(route.id))
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
                                composable("settings/search/unitconverter/help") {
                                    UnitConverterHelpSettingsScreen()
                                }
                                composable("settings/search/wikipedia") {
                                    WikipediaSettingsScreen()
                                }
                                composable("settings/search/locations") {
                                    LocationsSettingsScreen()
                                }
                                composable("settings/search/locations/osm") {
                                    OsmSettingsScreen()
                                }
                                composable("settings/search/files") {
                                    FileSearchSettingsScreen()
                                }
                                composable("settings/search/calendar") {
                                    CalendarSearchSettingsScreen()
                                }
                                composable("settings/search/calendar/{providerId}") {
                                    CalendarProviderSettingsScreen(
                                        it.arguments?.getString("providerId") ?: return@composable
                                    )
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
                                composable("settings/search/filterbar") {
                                    FilterBarSettingsScreen()
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
                                composable("settings/search/contacts") {
                                    ContactsSettingsScreen()
                                }
                                composable("settings/integrations") {
                                    IntegrationsSettingsScreen()
                                }
                                composable("settings/integrations/nextcloud") {
                                    NextcloudSettingsScreen()
                                }
                                composable("settings/integrations/owncloud") {
                                    OwncloudSettingsScreen()
                                }
                                composable("settings/integrations/tasks") {
                                    TasksIntegrationSettingsScreen()
                                }
                                composable("settings/integrations/breezyweather") {
                                    BreezyWeatherSettingsScreen()
                                }
                                composable("settings/plugins") {
                                    PluginsSettingsScreen()
                                }
                                composable("settings/plugins/{id}") {
                                    PluginSettingsScreen(
                                        it.arguments?.getString("id") ?: return@composable
                                    )
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newRoute = getStartRoute(intent)
        route = newRoute
    }

    private fun getStartRoute(intent: Intent): String? {
        if (intent.data?.host == "kvaesitso.mm20.de") {
            return intent.data?.getQueryParameter("route")
        } else {
            return intent.getStringExtra(EXTRA_ROUTE)
        }
    }

    companion object {
        const val EXTRA_ROUTE = "de.mm20.launcher2.settings.ROUTE"
        const val ROUTE_WEATHER_INTEGRATION = "settings/integrations/weather"
        const val ROUTE_MEDIA_INTEGRATION = "settings/integrations/media"
    }
}