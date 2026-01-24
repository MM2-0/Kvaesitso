package de.mm20.launcher2.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.app.GrammaticalInflectionManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalWallpaperColors
import de.mm20.launcher2.ui.overlays.OverlayHost
import de.mm20.launcher2.ui.settings.about.AboutSettingsRoute
import de.mm20.launcher2.ui.settings.about.AboutSettingsScreen
import de.mm20.launcher2.ui.settings.appearance.AppearanceSettingsRoute
import de.mm20.launcher2.ui.settings.appearance.AppearanceSettingsScreen
import de.mm20.launcher2.ui.settings.appearance.ExportThemeSettingsRoute
import de.mm20.launcher2.ui.settings.appearance.ExportThemeSettingsScreen
import de.mm20.launcher2.ui.settings.appearance.ImportThemeSettingsRoute
import de.mm20.launcher2.ui.settings.appearance.ImportThemeSettingsScreen
import de.mm20.launcher2.ui.settings.apps.AppSearchSettingsRoute
import de.mm20.launcher2.ui.settings.apps.AppSearchSettingsScreen
import de.mm20.launcher2.ui.settings.backup.BackupSettingsRoute
import de.mm20.launcher2.ui.settings.backup.BackupSettingsScreen
import de.mm20.launcher2.ui.settings.breezyweather.BreezyWeatherSettingsRoute
import de.mm20.launcher2.ui.settings.breezyweather.BreezyWeatherSettingsScreen
import de.mm20.launcher2.ui.settings.buildinfo.BuildInfoSettingsRoute
import de.mm20.launcher2.ui.settings.buildinfo.BuildInfoSettingsScreen
import de.mm20.launcher2.ui.settings.calendarsearch.CalendarProviderSettingsRoute
import de.mm20.launcher2.ui.settings.calendarsearch.CalendarProviderSettingsScreen
import de.mm20.launcher2.ui.settings.calendarsearch.CalendarSearchSettingsRoute
import de.mm20.launcher2.ui.settings.calendarsearch.CalendarSearchSettingsScreen
import de.mm20.launcher2.ui.settings.colorscheme.ColorSchemeSettingsRoute
import de.mm20.launcher2.ui.settings.colorscheme.ColorSchemeSettingsScreen
import de.mm20.launcher2.ui.settings.colorscheme.ColorSchemesSettingsRoute
import de.mm20.launcher2.ui.settings.colorscheme.ColorSchemesSettingsScreen
import de.mm20.launcher2.ui.settings.contacts.ContactsSettingsRoute
import de.mm20.launcher2.ui.settings.contacts.ContactsSettingsScreen
import de.mm20.launcher2.ui.settings.crashreporter.CrashReportRoute
import de.mm20.launcher2.ui.settings.crashreporter.CrashReportScreen
import de.mm20.launcher2.ui.settings.crashreporter.CrashReporterRoute
import de.mm20.launcher2.ui.settings.crashreporter.CrashReporterScreen
import de.mm20.launcher2.ui.settings.debug.DebugSettingsRoute
import de.mm20.launcher2.ui.settings.debug.DebugSettingsScreen
import de.mm20.launcher2.ui.settings.easteregg.EasterEggSettingsRoute
import de.mm20.launcher2.ui.settings.easteregg.EasterEggSettingsScreen
import de.mm20.launcher2.ui.settings.favorites.FavoritesSettingsRoute
import de.mm20.launcher2.ui.settings.favorites.FavoritesSettingsScreen
import de.mm20.launcher2.ui.settings.feed.FeedIntegrationSettingsRoute
import de.mm20.launcher2.ui.settings.feed.FeedIntegrationSettingsScreen
import de.mm20.launcher2.ui.settings.filesearch.FileSearchSettingsRoute
import de.mm20.launcher2.ui.settings.filesearch.FileSearchSettingsScreen
import de.mm20.launcher2.ui.settings.filterbar.FilterBarSettingsRoute
import de.mm20.launcher2.ui.settings.filterbar.FilterBarSettingsScreen
import de.mm20.launcher2.ui.settings.gestures.GestureSettingsScreen
import de.mm20.launcher2.ui.settings.gestures.GesturesSettingsRoute
import de.mm20.launcher2.ui.settings.hiddenitems.HiddenItemsSettingsRoute
import de.mm20.launcher2.ui.settings.hiddenitems.HiddenItemsSettingsScreen
import de.mm20.launcher2.ui.settings.homescreen.HomescreenSettingsRoute
import de.mm20.launcher2.ui.settings.homescreen.HomescreenSettingsScreen
import de.mm20.launcher2.ui.settings.icons.IconsSettingsRoute
import de.mm20.launcher2.ui.settings.icons.IconsSettingsScreen
import de.mm20.launcher2.ui.settings.integrations.IntegrationsSettingsRoute
import de.mm20.launcher2.ui.settings.integrations.IntegrationsSettingsScreen
import de.mm20.launcher2.ui.settings.license.LicenseRoute
import de.mm20.launcher2.ui.settings.license.LicenseScreen
import de.mm20.launcher2.ui.settings.locale.LocaleSettingsRoute
import de.mm20.launcher2.ui.settings.locale.LocaleSettingsScreen
import de.mm20.launcher2.ui.settings.locations.LocationsSettingsRoute
import de.mm20.launcher2.ui.settings.locations.LocationsSettingsScreen
import de.mm20.launcher2.ui.settings.log.LogRoute
import de.mm20.launcher2.ui.settings.log.LogScreen
import de.mm20.launcher2.ui.settings.main.MainRoute
import de.mm20.launcher2.ui.settings.main.MainSettingsScreen
import de.mm20.launcher2.ui.settings.media.MediaIntegrationSettingsRoute
import de.mm20.launcher2.ui.settings.media.MediaIntegrationSettingsScreen
import de.mm20.launcher2.ui.settings.nextcloud.NextcloudSettingsRoute
import de.mm20.launcher2.ui.settings.nextcloud.NextcloudSettingsScreen
import de.mm20.launcher2.ui.settings.osm.OsmSettingsRoute
import de.mm20.launcher2.ui.settings.osm.OsmSettingsScreen
import de.mm20.launcher2.ui.settings.owncloud.OwncloudSettingsRoute
import de.mm20.launcher2.ui.settings.owncloud.OwncloudSettingsScreen
import de.mm20.launcher2.ui.settings.plugins.PluginSettingsRoute
import de.mm20.launcher2.ui.settings.plugins.PluginSettingsScreen
import de.mm20.launcher2.ui.settings.plugins.PluginsSettingsRoute
import de.mm20.launcher2.ui.settings.plugins.PluginsSettingsScreen
import de.mm20.launcher2.ui.settings.search.SearchSettingsRoute
import de.mm20.launcher2.ui.settings.search.SearchSettingsScreen
import de.mm20.launcher2.ui.settings.searchactions.SearchActionsSettingsRoute
import de.mm20.launcher2.ui.settings.searchactions.SearchActionsSettingsScreen
import de.mm20.launcher2.ui.settings.shapes.ShapeSchemeSettingsRoute
import de.mm20.launcher2.ui.settings.shapes.ShapeSchemeSettingsScreen
import de.mm20.launcher2.ui.settings.shapes.ShapeSchemesSettingsRoute
import de.mm20.launcher2.ui.settings.shapes.ShapeSchemesSettingsScreen
import de.mm20.launcher2.ui.settings.smartspacer.SmartspacerSettingsRoute
import de.mm20.launcher2.ui.settings.smartspacer.SmartspacerSettingsScreen
import de.mm20.launcher2.ui.settings.tags.TagsSettingsRoute
import de.mm20.launcher2.ui.settings.tags.TagsSettingsScreen
import de.mm20.launcher2.ui.settings.tasks.TasksIntegrationSettingsRoute
import de.mm20.launcher2.ui.settings.tasks.TasksIntegrationSettingsScreen
import de.mm20.launcher2.ui.settings.transparencies.TransparencySchemeSettingsRoute
import de.mm20.launcher2.ui.settings.transparencies.TransparencySchemeSettingsScreen
import de.mm20.launcher2.ui.settings.transparencies.TransparencySchemesSettingsRoute
import de.mm20.launcher2.ui.settings.transparencies.TransparencySchemesSettingsScreen
import de.mm20.launcher2.ui.settings.typography.TypographiesSettingsRoute
import de.mm20.launcher2.ui.settings.typography.TypographiesSettingsScreen
import de.mm20.launcher2.ui.settings.typography.TypographySettingsRoute
import de.mm20.launcher2.ui.settings.typography.TypographySettingsScreen
import de.mm20.launcher2.ui.settings.unitconverter.UnitConverterHelpSettingsRoute
import de.mm20.launcher2.ui.settings.unitconverter.UnitConverterHelpSettingsScreen
import de.mm20.launcher2.ui.settings.unitconverter.UnitConverterSettingsRoute
import de.mm20.launcher2.ui.settings.unitconverter.UnitConverterSettingsScreen
import de.mm20.launcher2.ui.settings.weather.WeatherIntegrationSettingsRoute
import de.mm20.launcher2.ui.settings.weather.WeatherIntegrationSettingsScreen
import de.mm20.launcher2.ui.settings.wikipedia.WikipediaSettingsRoute
import de.mm20.launcher2.ui.settings.wikipedia.WikipediaSettingsScreen
import de.mm20.launcher2.ui.theme.LauncherTheme
import de.mm20.launcher2.ui.theme.wallpaperColorsAsState

class SettingsActivity : BaseActivity() {

    private var initialRoute by mutableStateOf<NavKey?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)

        val newRoute = getStartRoute(intent)
        initialRoute = newRoute

        val entryProvider = entryProvider {
            entry<MainRoute> {
                MainSettingsScreen()
            }
            entry<AppearanceSettingsRoute> {
                AppearanceSettingsScreen()
            }
            entry<ExportThemeSettingsRoute> {
                ExportThemeSettingsScreen()
            }
            entry<ImportThemeSettingsRoute> {
                ImportThemeSettingsScreen(it.fromUri)
            }
            entry<HomescreenSettingsRoute> {
                HomescreenSettingsScreen()
            }
            entry<IconsSettingsRoute> {
                IconsSettingsScreen()
            }
            entry<ColorSchemesSettingsRoute> {
                ColorSchemesSettingsScreen()
            }
            entry<ColorSchemeSettingsRoute> {
                ColorSchemeSettingsScreen(it.id)
            }
            entry<ShapeSchemesSettingsRoute> {
                ShapeSchemesSettingsScreen()
            }
            entry<ShapeSchemeSettingsRoute> {
                ShapeSchemeSettingsScreen(it.id)
            }

            entry<TransparencySchemesSettingsRoute> {
                TransparencySchemesSettingsScreen()
            }
            entry<TransparencySchemeSettingsRoute> {
                TransparencySchemeSettingsScreen(it.id)
            }
            entry<TypographiesSettingsRoute> {
                TypographiesSettingsScreen()
            }
            entry<TypographySettingsRoute> {
                TypographySettingsScreen(it.id)
            }
            entry<SearchSettingsRoute> {
                SearchSettingsScreen()
            }
            entry<GesturesSettingsRoute> {
                GestureSettingsScreen()
            }
            entry<UnitConverterSettingsRoute> {
                UnitConverterSettingsScreen()
            }
            entry<UnitConverterHelpSettingsRoute> {
                UnitConverterHelpSettingsScreen()
            }
            entry<WikipediaSettingsRoute> {
                WikipediaSettingsScreen()
            }
            entry<LocationsSettingsRoute> {
                LocationsSettingsScreen()
            }
            entry<OsmSettingsRoute> {
                OsmSettingsScreen()
            }
            entry<FileSearchSettingsRoute> {
                FileSearchSettingsScreen()
            }
            entry<CalendarSearchSettingsRoute> {
                CalendarSearchSettingsScreen()
            }
            entry<CalendarProviderSettingsRoute> {
                CalendarProviderSettingsScreen(it.providerId)
            }
            entry<SearchActionsSettingsRoute> {
                SearchActionsSettingsScreen()
            }
            entry<HiddenItemsSettingsRoute> {
                HiddenItemsSettingsScreen()
            }
            entry<TagsSettingsRoute> {
                TagsSettingsScreen()
            }
            entry<FilterBarSettingsRoute> {
                FilterBarSettingsScreen()
            }
            entry<WeatherIntegrationSettingsRoute> {
                WeatherIntegrationSettingsScreen()
            }
            entry<MediaIntegrationSettingsRoute> {
                MediaIntegrationSettingsScreen()
            }
            entry<FavoritesSettingsRoute> {
                FavoritesSettingsScreen()
            }
            entry<ContactsSettingsRoute> {
                ContactsSettingsScreen()
            }
            entry<IntegrationsSettingsRoute> {
                IntegrationsSettingsScreen()
            }
            entry<NextcloudSettingsRoute> {
                NextcloudSettingsScreen()
            }
            entry<OwncloudSettingsRoute> {
                OwncloudSettingsScreen()
            }
            entry<TasksIntegrationSettingsRoute> {
                TasksIntegrationSettingsScreen()
            }
            entry<BreezyWeatherSettingsRoute> {
                BreezyWeatherSettingsScreen()
            }
            entry<PluginsSettingsRoute> {
                PluginsSettingsScreen()
            }
            entry<PluginSettingsRoute> {
                PluginSettingsScreen(it.pluginId)
            }
            entry<AboutSettingsRoute> {
                AboutSettingsScreen()
            }
            entry<BuildInfoSettingsRoute> {
                BuildInfoSettingsScreen()
            }
            entry<EasterEggSettingsRoute> {
                EasterEggSettingsScreen()
            }
            entry<DebugSettingsRoute> {
                DebugSettingsScreen()
            }
            entry<LocaleSettingsRoute> {
                LocaleSettingsScreen()
            }
            entry<BackupSettingsRoute> {
                BackupSettingsScreen()
            }
            entry<CrashReporterRoute> {
                CrashReporterScreen()
            }
            entry<LogRoute> {
                LogScreen()
            }
            entry<CrashReportRoute> {
                CrashReportScreen(it.fileName)
            }
            entry<LicenseRoute> {
                LicenseScreen(it.libraryName)
            }
            entry<AppSearchSettingsRoute> {
                AppSearchSettingsScreen()
            }
            entry<SmartspacerSettingsRoute> {
                SmartspacerSettingsScreen()
            }
            entry<FeedIntegrationSettingsRoute> {
                FeedIntegrationSettingsScreen()
            }
        }


        setContent {
            val backStack = rememberNavBackStack(MainRoute)

            LaunchedEffect(initialRoute) {
                if (initialRoute != null) {
                    backStack.clear()
                    backStack.add(initialRoute!!)
                }
            }

            val isDarkTheme = LocalDarkTheme.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val activity = LocalActivity.current
            val view = LocalView.current
            LaunchedEffect(isDarkTheme) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    val insetsController = WindowInsetsControllerCompat(activity!!.window, view)
                    insetsController.isAppearanceLightStatusBars = !isDarkTheme
                    insetsController.isAppearanceLightNavigationBars = !isDarkTheme
                }
            }

            val wallpaperColors by wallpaperColorsAsState()
            CompositionLocalProvider(
                LocalWallpaperColors provides wallpaperColors,
                LocalBackStack provides backStack,
            ) {
                ProvideCompositionLocals {
                    LauncherTheme {
                        OverlayHost(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            NavDisplay(
                                backStack = backStack,
                                onBack = { backStack.removeLastOrNull() },
                                entryProvider = entryProvider,
                                transitionSpec = {
                                    (slideInHorizontally { it / 2 } + scaleIn(initialScale = 0.9f) + fadeIn())
                                        .togetherWith(
                                            slideOutHorizontally { -it / 4 }
                                        )
                                },
                                popTransitionSpec = {
                                    (slideInHorizontally { -it / 4 })
                                        .togetherWith(
                                            slideOutHorizontally { it / 2 } + scaleOut(targetScale = 0.9f) + fadeOut()
                                        )
                                },
                                predictivePopTransitionSpec = {
                                    (slideInHorizontally { -it / 4 })
                                        .togetherWith(
                                            slideOutHorizontally { it / 2 } + scaleOut(targetScale = 0.9f) + fadeOut()
                                        )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newRoute = getStartRoute(intent)
        initialRoute = newRoute
    }

    private fun getStartRoute(intent: Intent): NavKey? {
        val routeName = if (intent.data?.host == "kvaesitso.mm20.de") {
            intent.data?.getQueryParameter("route") ?: return null
        } else {
            intent.getStringExtra(EXTRA_ROUTE) ?: return null
        }
        return when(routeName) {
            ROUTE_WEATHER_INTEGRATION -> WeatherIntegrationSettingsRoute
            ROUTE_MEDIA_INTEGRATION -> MediaIntegrationSettingsRoute
            ROUTE_SEARCH_ACTIONS -> SearchActionsSettingsRoute
            ROUTE_HIDDEN_ITEMS -> HiddenItemsSettingsRoute
            ROUTE_CRASH_REPORT if (intent.hasExtra(EXTRA_CRASH_REPORT_PATH)) -> {
                CrashReportRoute(intent.getStringExtra(EXTRA_CRASH_REPORT_PATH)!!)
            }
            else -> null
        }
    }

    companion object {
        const val EXTRA_ROUTE = "de.mm20.launcher2.settings.ROUTE"
        const val ROUTE_WEATHER_INTEGRATION = "settings/integrations/weather"
        const val ROUTE_MEDIA_INTEGRATION = "settings/integrations/media"
        const val ROUTE_SEARCH_ACTIONS = "settings/search/searchactions"
        const val ROUTE_HIDDEN_ITEMS = "settings/search/hiddenitems"
        const val ROUTE_CRASH_REPORT = "settings/debug/crashreport"
        const val EXTRA_CRASH_REPORT_PATH = "crash_report_path"
    }
}
