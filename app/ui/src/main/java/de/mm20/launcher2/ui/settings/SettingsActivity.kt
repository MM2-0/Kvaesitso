package de.mm20.launcher2.ui.settings

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import de.mm20.launcher2.applock.SettingsDeepLinkContract
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
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
import de.mm20.launcher2.ui.comms.applyVirtualAppOrientation
import de.mm20.launcher2.ui.overlays.OverlayHost
import de.mm20.launcher2.ui.settings.about.AboutSettingsRoute
import de.mm20.launcher2.ui.settings.about.AboutSettingsScreen
import de.mm20.launcher2.ui.settings.advanced.AdvancedSettingsRoute
import de.mm20.launcher2.ui.settings.advanced.AdvancedSettingsScreen
import de.mm20.launcher2.ui.settings.freeze.SmartFreezeDashboardRoute
import de.mm20.launcher2.ui.settings.freeze.SmartFreezeDashboardScreen
import de.mm20.launcher2.ui.settings.appearance.AppearanceSettingsRoute
import de.mm20.launcher2.ui.settings.appearance.AppearanceSettingsScreen
import de.mm20.launcher2.ui.settings.appearance.ExportThemeSettingsRoute
import de.mm20.launcher2.ui.settings.appearance.ExportThemeSettingsScreen
import de.mm20.launcher2.ui.settings.appearance.ImportThemeSettingsRoute
import de.mm20.launcher2.ui.settings.appearance.ImportThemeSettingsScreen
import de.mm20.launcher2.ui.settings.appearance.presets.PresetsSettingsRoute
import de.mm20.launcher2.ui.settings.appearance.presets.PresetsSettingsScreen
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
import de.mm20.launcher2.ui.settings.applock.AppLockAppsRoute
import de.mm20.launcher2.ui.settings.applock.AppLockAppsScreen
import de.mm20.launcher2.ui.settings.applock.AppLockSettingsRoute
import de.mm20.launcher2.ui.settings.applock.AppLockWebAppsRoute
import de.mm20.launcher2.ui.settings.applock.AppLockWebAppsScreen
import de.mm20.launcher2.ui.settings.applock.IntruderPhotosRoute
import de.mm20.launcher2.ui.settings.applock.IntruderPhotosScreen
import de.mm20.launcher2.ui.settings.applock.AppLockSettingsScreen
import de.mm20.launcher2.ui.settings.workprofile.WorkProfileSettingsRoute
import de.mm20.launcher2.ui.settings.workprofile.WorkProfileSettingsScreen
import de.mm20.launcher2.ui.settings.crashreporter.CrashReporterScreen
import de.mm20.launcher2.ui.settings.debug.DebugSettingsRoute
import de.mm20.launcher2.ui.settings.debug.DebugSettingsScreen
import de.mm20.launcher2.ui.settings.freeze.FreezeDashboardRoute
import de.mm20.launcher2.ui.settings.freeze.FreezeDashboardScreen
import de.mm20.launcher2.ui.settings.freeze.FreezeSettingsRoute
import de.mm20.launcher2.ui.settings.freeze.FreezeSettingsScreen
import de.mm20.launcher2.ui.settings.freeze.DeviceOwnerSetupRoute
import de.mm20.launcher2.ui.settings.freeze.DeviceOwnerSetupScreen
import de.mm20.launcher2.ui.settings.contextprofiles.ContextProfilesSettingsRoute
import de.mm20.launcher2.ui.settings.contextprofiles.ContextProfilesSettingsScreen
import de.mm20.launcher2.ui.settings.desktopmode.DesktopModeSettingsRoute
import de.mm20.launcher2.ui.settings.desktopmode.DesktopModeSettingsScreen
import de.mm20.launcher2.ui.settings.floating.FloatingLauncherSettingsRoute
import de.mm20.launcher2.ui.settings.floating.FloatingLauncherSettingsScreen
import de.mm20.launcher2.ui.settings.dynamicisland.DynamicIslandSettingsRoute
import de.mm20.launcher2.ui.settings.dynamicisland.DynamicIslandSettingsScreen
import de.mm20.launcher2.ui.settings.homescreen.dock.DockSettingsRoute
import de.mm20.launcher2.ui.settings.homescreen.dock.DockSettingsScreen
import de.mm20.launcher2.ui.settings.homescreen.wallpaper.WallpaperLibraryRoute
import de.mm20.launcher2.ui.settings.homescreen.wallpaper.WallpaperLibraryScreen
import de.mm20.launcher2.ui.settings.homescreen.wallpaper.WallpaperSettingsRoute
import de.mm20.launcher2.ui.settings.homescreen.wallpaper.WallpaperSettingsScreen
import de.mm20.launcher2.ui.settings.webapps.WebAppsSettingsRoute
import de.mm20.launcher2.ui.settings.webapps.WebAppsSettingsScreen
import de.mm20.launcher2.ui.settings.art.ArtOptimizerRoute
import de.mm20.launcher2.ui.settings.art.ArtOptimizerScreen
import de.mm20.launcher2.ui.settings.stealth.StealthCloakRoute
import de.mm20.launcher2.ui.settings.stealth.StealthCloakScreen
import de.mm20.launcher2.ui.settings.security.TelosSecurityHubRoute
import de.mm20.launcher2.ui.settings.security.TelosSecurityHubScreen
import de.mm20.launcher2.ui.settings.performance.PerformanceSettingsRoute
import de.mm20.launcher2.ui.settings.performance.PerformanceSettingsScreen
import de.mm20.launcher2.ui.settings.protection.ProtectedSettingsScreen
import de.mm20.launcher2.ui.settings.debug.StringNormalizerTestRoute
import de.mm20.launcher2.ui.settings.debug.StringNormalizerTestScreen
import de.mm20.launcher2.ui.settings.easteregg.EasterEggSettingsRoute
import de.mm20.launcher2.ui.settings.easteregg.EasterEggSettingsScreen
import de.mm20.launcher2.ui.settings.favorites.FavoritesSettingsRoute
import de.mm20.launcher2.ui.settings.favorites.FavoritesSettingsScreen
import de.mm20.launcher2.ui.settings.feed.FeedIntegrationSettingsRoute
import de.mm20.launcher2.ui.settings.feed.FeedIntegrationSettingsScreen
import de.mm20.launcher2.ui.settings.filesearch.ExcludedFoldersSettingsRoute
import de.mm20.launcher2.ui.settings.filesearch.ExcludedFoldersSettingsScreen
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
import de.mm20.launcher2.ui.settings.folders.CreateFolderRoute
import de.mm20.launcher2.ui.settings.folders.CreateFolderScreen
import de.mm20.launcher2.ui.settings.icons.IconsSettingsRoute
import de.mm20.launcher2.ui.settings.icons.IconsSettingsScreen
import de.mm20.launcher2.ui.comms.CommsDashboardRoute
import de.mm20.launcher2.ui.comms.CommsDashboardScreen
import de.mm20.launcher2.ui.settings.comms.CommsSettingsRoute
import de.mm20.launcher2.ui.settings.comms.CommsSettingsScreen
import de.mm20.launcher2.ui.store.AppDetailsRoute
import de.mm20.launcher2.ui.comms.ContactDetailsRoute
import de.mm20.launcher2.ui.comms.ContactDetailsScreen
import de.mm20.launcher2.ui.store.AppDetailsScreen
import de.mm20.launcher2.ui.store.StoreDashboardRoute
import de.mm20.launcher2.ui.store.StoreDashboardScreen
import de.mm20.launcher2.ui.settings.integrations.IntegrationsSettingsRoute
import de.mm20.launcher2.ui.settings.integrations.IntegrationsSettingsScreen
import de.mm20.launcher2.ui.settings.license.LicenseRoute
import de.mm20.launcher2.ui.settings.license.LicenseScreen
import de.mm20.launcher2.ui.settings.locale.CalendarSettingsRoute
import de.mm20.launcher2.ui.settings.locale.CalendarSettingsScreen
import de.mm20.launcher2.ui.settings.locale.CurrencySettingsRoute
import de.mm20.launcher2.ui.settings.locale.CurrencySettingsScreen
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
import de.mm20.launcher2.ui.theme.transparency.transparency
import de.mm20.launcher2.ui.theme.wallpaperColorsAsState

open class SettingsActivity : BaseActivity() {

    private var initialRoute by mutableStateOf<NavKey?>(null)

    override fun onResume() {
        super.onResume()
        applyTaskLabel(intent)
        val koin = org.koin.mp.KoinPlatform.getKoin()
        if (koin.get<de.mm20.launcher2.network.api.NetworkSettings>().current.engineWanted) {
            koin.get<de.mm20.launcher2.network.NetworkEngine>().restoreIfWanted()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (redirectVirtualApp(intent)) {
            finishRedirectHost()
            return
        }
        WindowCompat.enableEdgeToEdge(window)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))

        applyTaskLabel(intent)
        applyVirtualAppOrientation(
            intent.getStringExtra(EXTRA_ROUTE),
            intent.getStringExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB),
            allowDialerLandscape = false,
        )
        val newRoute = getStartRoute(intent)
        // Only apply the deep link on a fresh start; after rotation the restored back stack must be kept.
        initialRoute = if (savedInstanceState == null) newRoute else null

        val entryProvider = entryProvider {
            entry<MainRoute> {
                MainSettingsScreen()
            }
            entry<SmartFreezeDashboardRoute> {
                FreezeSettingsScreen()
            }
            entry<AdvancedSettingsRoute> {
                de.mm20.launcher2.ui.system.SystemDashboardScreen()
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
            entry<StoreDashboardRoute> {
                StoreDashboardScreen(it.addUrl, it.initialQuery)
            }
            entry<ContactDetailsRoute> {
                ContactDetailsScreen(it.contactId, it.phoneNumber)
            }
            entry<AppDetailsRoute> {
                AppDetailsScreen(it.itemId)
            }
            entry<CommsDashboardRoute> {
                // === TELOS_PENDING_REVIEW_START: comms_virtual_apps ===
                CommsDashboardScreen(it.initialTab, it.initialNumber, it.initialBody, it.initialAttachments)
                // === TELOS_PENDING_REVIEW_END: comms_virtual_apps ===
            }
            entry<de.mm20.launcher2.ui.files.remote.ConnectionsRoute> {
                de.mm20.launcher2.ui.files.remote.ConnectionsScreen()
            }
            entry<de.mm20.launcher2.ui.files.FilesRoute> {
                de.mm20.launcher2.ui.files.FilesScreen()
            }
            entry<de.mm20.launcher2.ui.screenshot.ScreenshotRoute> {
                de.mm20.launcher2.ui.screenshot.ScreenshotScreen()
            }
            entry<de.mm20.launcher2.ui.screenshot.ScreenshotSettingsRoute> {
                de.mm20.launcher2.ui.screenshot.ScreenshotSettingsScreen()
            }
            entry<de.mm20.launcher2.ui.screenrec.ScreenRecorderRoute> {
                de.mm20.launcher2.ui.screenrec.ScreenRecorderScreen()
            }
            entry<de.mm20.launcher2.ui.screenrec.ScreenRecorderSettingsRoute> {
                de.mm20.launcher2.ui.screenrec.ScreenRecorderSettingsScreen()
            }
            entry<de.mm20.launcher2.ui.voice.VoiceRecorderRoute> {
                de.mm20.launcher2.ui.voice.VoiceRecorderScreen()
            }
            entry<de.mm20.launcher2.ui.voice.VoiceSettingsRoute> {
                de.mm20.launcher2.ui.voice.VoiceSettingsScreen()
            }
            entry<de.mm20.launcher2.ui.downloads.DownloadsRoute> {
                de.mm20.launcher2.ui.downloads.DownloadsScreen(it.initialUrls)
            }
            entry<de.mm20.launcher2.ui.downloads.DownloadsSettingsRoute> {
                de.mm20.launcher2.ui.downloads.DownloadsSettingsScreen()
            }
            entry<de.mm20.launcher2.ui.ermis.ErmisRoute> {
                de.mm20.launcher2.ui.ermis.ErmisScreen()
            }
            entry<de.mm20.launcher2.ui.network.NetworkRoute> {
                de.mm20.launcher2.ui.network.NetworkHomeScreen()
            }
            entry<de.mm20.launcher2.ui.network.PrivateDnsRoute> {
                de.mm20.launcher2.ui.network.PrivateDnsScreen()
            }
            entry<de.mm20.launcher2.ui.network.NetworkSpeedTestRoute> {
                de.mm20.launcher2.ui.network.NetworkSpeedTestScreen()
            }
            entry<de.mm20.launcher2.ui.network.NetworkScanRoute> {
                de.mm20.launcher2.ui.network.NetworkScanScreen()
            }
            entry<de.mm20.launcher2.ui.settings.tweaks.TweaksSettingsRoute> {
                de.mm20.launcher2.ui.settings.tweaks.TweaksSettingsScreen()
            }
            entry<de.mm20.launcher2.ui.network.NetworkPrivacyRoute> {
                de.mm20.launcher2.ui.network.NetworkPrivacyScreen()
            }
            entry<de.mm20.launcher2.ui.network.NetworkTorAppsRoute> {
                de.mm20.launcher2.ui.network.NetworkTorAppsScreen()
            }
            entry<de.mm20.launcher2.ui.network.NetworkSettingsRoute> {
                de.mm20.launcher2.ui.network.NetworkSettingsScreen()
            }
            entry<de.mm20.launcher2.ui.network.nextdns.NextDnsRoute> {
                de.mm20.launcher2.ui.network.nextdns.NextDnsScreen()
            }
            entry<de.mm20.launcher2.ui.network.dns.NetworkDnsRoute> {
                de.mm20.launcher2.ui.network.dns.NetworkDnsScreen()
            }
            entry<de.mm20.launcher2.ui.network.wireguard.NetworkWireguardRoute> {
                de.mm20.launcher2.ui.network.wireguard.NetworkWireguardScreen()
            }
            entry<de.mm20.launcher2.ui.network.wireguard.NetworkWireguardAppsRoute> {
                de.mm20.launcher2.ui.network.wireguard.NetworkWireguardAppsScreen()
            }
            entry<de.mm20.launcher2.ui.network.firewall.NetworkFirewallRoute> {
                de.mm20.launcher2.ui.network.firewall.NetworkFirewallScreen()
            }
            entry<de.mm20.launcher2.ui.network.firewall.NetworkUniversalRulesRoute> {
                de.mm20.launcher2.ui.network.firewall.NetworkUniversalRulesScreen()
            }
            entry<de.mm20.launcher2.ui.network.firewall.NetworkAppRulesRoute> { key -> de.mm20.launcher2.ui.network.firewall.NetworkCustomRulesScreen(key.appId) }
        entry<de.mm20.launcher2.ui.system.SystemAppOpsRoute> { key -> de.mm20.launcher2.ui.system.SystemAppOpsScreen(key.packageName) }
        entry<de.mm20.launcher2.ui.system.SystemAppDetailsRoute> { key -> de.mm20.launcher2.ui.system.SystemAppDetailsScreen(key.packageName) }
        entry<de.mm20.launcher2.ui.network.firewall.NetworkPackageFirewallRoute> { key -> de.mm20.launcher2.ui.network.firewall.NetworkFirewallScreen(key.packageName) }
            entry<de.mm20.launcher2.ui.network.firewall.NetworkCustomRulesRoute> {
                de.mm20.launcher2.ui.network.firewall.NetworkCustomRulesScreen()
            }
            entry<de.mm20.launcher2.ui.network.blocklists.NetworkBlocklistsRoute> {
                de.mm20.launcher2.ui.network.blocklists.NetworkBlocklistsScreen()
            }
            entry<de.mm20.launcher2.ui.network.NetworkProxiesRoute> { de.mm20.launcher2.ui.network.NetworkProxiesScreen() }
        entry<de.mm20.launcher2.ui.network.NetworkStatsRoute> { de.mm20.launcher2.ui.network.NetworkStatsScreen() }
            entry<de.mm20.launcher2.ui.network.logs.NetworkLogsRoute> {
                de.mm20.launcher2.ui.network.logs.NetworkLogsScreen()
            }
            entry<de.mm20.launcher2.ui.system.SystemDashboardRoute> {
                de.mm20.launcher2.ui.system.SystemDashboardScreen(it.tab)
            }
            entry<de.mm20.launcher2.ui.weather.WeatherRoute> {
                de.mm20.launcher2.ui.weather.WeatherScreen()
            }
            entry<de.mm20.launcher2.ui.trash.TrashUnlockRoute> {
                de.mm20.launcher2.ui.trash.TrashUnlockScreen()
            }
            entry<de.mm20.launcher2.ui.media.akou.AkouRoute> {
                de.mm20.launcher2.ui.media.akou.AkouScreen()
            }
            entry<de.mm20.launcher2.ui.clock.ClockRoute> {
                de.mm20.launcher2.ui.clock.ClockScreen()
            }
            entry<de.mm20.launcher2.ui.camera.CameraRoute> {
                de.mm20.launcher2.ui.camera.CameraScreen()
            }
            entry<de.mm20.launcher2.ui.tools.MagnifierRoute> {
                de.mm20.launcher2.ui.tools.MagnifierScreen()
            }
            entry<de.mm20.launcher2.ui.tools.FlashlightRoute> {
                de.mm20.launcher2.ui.tools.FlashlightScreen()
            }
            entry<de.mm20.launcher2.ui.notes.NotesRoute> {
                de.mm20.launcher2.ui.notes.NotesScreen()
            }
            entry<de.mm20.launcher2.ui.calendar.CalendarRoute> {
                de.mm20.launcher2.ui.calendar.CalendarScreen()
            }
            entry<de.mm20.launcher2.ui.calculator.CalculatorRoute> {
                de.mm20.launcher2.ui.calculator.CalculatorScreen()
            }
            entry<de.mm20.launcher2.ui.media.photos.PhotosRoute> {
                // Photos is a tab of Telos Viewer
                de.mm20.launcher2.ui.media.docs.ViewerScreen()
            }
            entry<de.mm20.launcher2.ui.media.docs.ViewerRoute> {
                de.mm20.launcher2.ui.media.docs.ViewerScreen()
            }
            entry<de.mm20.launcher2.ui.media.hub.MediaHubRoute> {
                de.mm20.launcher2.ui.media.hub.MediaHubScreen(
                    it.space, it.link, it.requestId, it.stremioDetailType, it.stremioDetailId,
                )
            }
            entry<de.mm20.launcher2.ui.media.video.VideoRoute> {
                de.mm20.launcher2.ui.media.video.VideoScreen()
            }
            entry<de.mm20.launcher2.ui.media.music.MusicRoute> {
                de.mm20.launcher2.ui.media.music.MusicScreen()
            }
            entry<de.mm20.launcher2.ui.comms.radio.RadioDashboardRoute> {
                de.mm20.launcher2.ui.comms.radio.RadioDashboardScreen()
            }
        entry<de.mm20.launcher2.ui.settings.comms.CallerPrivacyRoute> { de.mm20.launcher2.ui.settings.comms.CallerPrivacyScreen() }
            entry<CommsSettingsRoute> {
                CommsSettingsScreen()
            }
            entry<de.mm20.launcher2.ui.comms.FakeCallSettingsRoute> {
                de.mm20.launcher2.ui.comms.FakeCallSettingsScreen()
            }
            entry<de.mm20.launcher2.ui.comms.DuplicateContactsRoute> {
                de.mm20.launcher2.ui.comms.DuplicateContactsScreen()
            }
            entry<de.mm20.launcher2.ui.comms.CallRecordingsRoute> {
                de.mm20.launcher2.ui.comms.CallRecordingsScreen()
            }
            entry<de.mm20.launcher2.ui.comms.CallerNotesRoute> {
                de.mm20.launcher2.ui.comms.CallerNotesScreen()
            }
            entry<de.mm20.launcher2.ui.comms.HiddenContactsRoute> {
                de.mm20.launcher2.ui.comms.HiddenContactsScreen()
            }
            entry<de.mm20.launcher2.ui.comms.ContactGroupsRoute> {
                de.mm20.launcher2.ui.comms.ContactGroupsScreen()
            }
            entry<de.mm20.launcher2.ui.comms.ScheduledSmsRoute> {
                de.mm20.launcher2.ui.comms.ScheduledSmsScreen()
            }
            entry<de.mm20.launcher2.ui.settings.comms.ScrobbleSettingsRoute> {
                de.mm20.launcher2.ui.settings.comms.ScrobbleSettingsScreen()
            }
            entry<CreateFolderRoute> {
                CreateFolderScreen()
            }
            entry<ColorSchemesSettingsRoute> {
                ColorSchemesSettingsScreen()
            }
            entry<ColorSchemeSettingsRoute> {
                ColorSchemeSettingsScreen(it.id)
            }
            entry<PresetsSettingsRoute> {
                PresetsSettingsScreen()
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
            entry<de.mm20.launcher2.ui.settings.agents.AgentsSettingsRoute> {
                de.mm20.launcher2.ui.settings.agents.AgentsSettingsScreen()
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
            entry<de.mm20.launcher2.ui.help.HelpDashboardRoute> {
                de.mm20.launcher2.ui.help.HelpDashboardScreen()
            }
            entry<de.mm20.launcher2.ui.help.HelpLessonRoute> {
                de.mm20.launcher2.ui.help.HelpLessonScreen(it.topicId)
            }
            entry<de.mm20.launcher2.ui.help.HelpAppDetailRoute> {
                de.mm20.launcher2.ui.help.HelpAppDetailScreen(it.appKey)
            }
            entry<de.mm20.launcher2.ui.keyboard.TelosKeyboardRoute> {
                de.mm20.launcher2.ui.keyboard.TelosKeyboardSettingsScreen()
            }
            entry<de.mm20.launcher2.ui.keepass.KeePassDashboardRoute> {
                de.mm20.launcher2.ui.keepass.KeePassDashboardScreen()
            }
            entry<de.mm20.launcher2.ui.fit.FitDashboardRoute> {
                de.mm20.launcher2.ui.fit.FitDashboardScreen()
            }
            entry<de.mm20.launcher2.ui.aether.AetherDashboardRoute> {
                de.mm20.launcher2.ui.aether.AetherDashboardScreen()
            }
            entry<de.mm20.launcher2.ui.sync.SyncRoute> {
                de.mm20.launcher2.ui.sync.SyncScreen()
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
            entry<ExcludedFoldersSettingsRoute> {
                ProtectedSettingsScreen {
                    ExcludedFoldersSettingsScreen()
                }
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
                ProtectedSettingsScreen {
                    HiddenItemsSettingsScreen()
                }
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
            entry<AppLockSettingsRoute> {
                AppLockSettingsScreen()
            }
            entry<AppLockAppsRoute> {
                AppLockAppsScreen()
            }
            entry<AppLockWebAppsRoute> {
                AppLockWebAppsScreen()
            }
            entry<IntruderPhotosRoute> {
                IntruderPhotosScreen()
            }
            entry<WorkProfileSettingsRoute> {
                WorkProfileSettingsScreen()
            }
            entry<FreezeSettingsRoute> {
                FreezeSettingsScreen()
            }
            entry<PerformanceSettingsRoute> {
                PerformanceSettingsScreen()
            }
            entry<ArtOptimizerRoute> {
                ArtOptimizerScreen()
            }
            entry<StealthCloakRoute> {
                StealthCloakScreen()
            }
            entry<TelosSecurityHubRoute> {
                TelosSecurityHubScreen()
            }
            entry<FloatingLauncherSettingsRoute> {
                FloatingLauncherSettingsScreen()
            }
            entry<WebAppsSettingsRoute> {
                WebAppsSettingsScreen()
            }
            entry<DynamicIslandSettingsRoute> {
                DynamicIslandSettingsScreen()
            }
            entry<ContextProfilesSettingsRoute> {
                ContextProfilesSettingsScreen()
            }
            entry<DesktopModeSettingsRoute> {
                DesktopModeSettingsScreen()
            }
            entry<WallpaperSettingsRoute> {
                WallpaperSettingsScreen()
            }
            entry<WallpaperLibraryRoute> { key ->
                WallpaperLibraryScreen(appendVideo = key.appendVideo)
            }
            entry<DockSettingsRoute> {
                DockSettingsScreen()
            }
            entry<FreezeDashboardRoute> {
                FreezeDashboardScreen()
            }
            entry<DeviceOwnerSetupRoute> {
                DeviceOwnerSetupScreen()
            }
            entry<StringNormalizerTestRoute> {
                StringNormalizerTestScreen()
            }
            entry<LocaleSettingsRoute> {
                LocaleSettingsScreen()
            }
            entry<CalendarSettingsRoute> {
                CalendarSettingsScreen()
            }
            entry<CurrencySettingsRoute> {
                CurrencySettingsScreen()
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
            val containerSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
            val backStack = rememberNavBackStack(MainRoute)
            val navigation = androidx.compose.runtime.remember(backStack) {
                if (this@SettingsActivity is TelosAppActivity) {
                    BackgroundOnRootBackStack(backStack) {
                        this@SettingsActivity.moveTaskToBack(true)
                    }
                } else backStack
            }

            androidx.activity.compose.BackHandler(
                enabled = this@SettingsActivity is TelosAppActivity && backStack.size == 1,
            ) {
                this@SettingsActivity.moveTaskToBack(true)
            }

            LaunchedEffect(initialRoute) {
                if (initialRoute != null) {
                    backStack.clear()
                    backStack.add(initialRoute!!)
                }
            }

            if (this@SettingsActivity is TelosAppActivity) {
                initialRoute?.let { de.mm20.launcher2.ui.component.PersistNavigation(backStack, it) }
            }
            val wallpaperColors by wallpaperColorsAsState()
            CompositionLocalProvider(
                de.mm20.launcher2.ui.locals.LocalWindowSize provides androidx.compose.ui.geometry.Size(
                    containerSize.width.toFloat(), containerSize.height.toFloat(),
                ),
                LocalWallpaperColors provides wallpaperColors,
                LocalBackStack provides navigation,
            ) {
                ProvideCompositionLocals {
                    LauncherTheme {
                        val commsSettings: de.mm20.launcher2.preferences.comms.CommsSettings = org.koin.compose.koinInject()
                        val allowDialerLandscape by commsSettings.allowDialerLandscape.collectAsState(false)
                        LaunchedEffect(intent, allowDialerLandscape) {
                            applyVirtualAppOrientation(
                                intent.getStringExtra(EXTRA_ROUTE),
                                intent.getStringExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB),
                                allowDialerLandscape,
                            )
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

                        val fontKey = de.mm20.launcher2.applock.SettingsDeepLinkContract.virtualAppTaskId(
                            intent.getStringExtra(EXTRA_ROUTE),
                            intent.getStringExtra(de.mm20.launcher2.applock.SettingsDeepLinkContract.EXTRA_COMMS_TAB),
                        )
                        de.mm20.launcher2.ui.component.ProvidePerAppFontScale(fontKey) {
                        OverlayHost(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .imePadding()
                                .background(de.mm20.launcher2.ui.component.virtualAppSurface())
                        ) {
                            // a Telos app opened directly is the only entry: going back from it closes the activity
                            // (an empty back stack must never reach NavDisplay)
                            LaunchedEffect(backStack.size) { if (backStack.isEmpty()) activity?.finish() }
                            val pageEffects = de.mm20.launcher2.ui.component.rememberVirtualAppPageEffects()
                            if (backStack.isNotEmpty()) NavDisplay(
                                backStack = backStack,
                                onBack = { navigation.removeLastOrNull() },
                                entryProvider = entryProvider,
                                transitionSpec = {
                                    de.mm20.launcher2.ui.component.telosPageTransition(pageEffects)
                                },
                                popTransitionSpec = {
                                    de.mm20.launcher2.ui.component.telosPagePopTransition(pageEffects)
                                },
                                predictivePopTransitionSpec = {
                                    de.mm20.launcher2.ui.component.telosPagePopTransition(pageEffects)
                                },
                            )
                        }
                        }
                    }
                }
            }
        }
    }

    /**
     * The Telos apps run inside this activity: the recents screen shows the name and icon of the app that
     * was opened ("Telos Media") instead of "Settings".
     */
    private fun applyTaskLabel(intent: Intent) {
        val route = intent.getStringExtra(EXTRA_ROUTE)
            ?: intent.data?.takeIf { it.host == "kvaesitso.mm20.de" }?.getQueryParameter("route")
            ?: return
        val domain = when (route) {
            ROUTE_COMMS -> if (intent.getStringExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB) == "messages")
                "telos_messages_app" else "telos_phone_app"
            ROUTE_MEDIA -> "telos_media_app"
            ROUTE_MUSIC -> "telos_music_app"
            ROUTE_RADIO -> "telos_radio_app"
            ROUTE_VIDEO -> "telos_video_app"
            SettingsDeepLinkContract.ROUTE_TV -> "telos_tv_app"
            SettingsDeepLinkContract.ROUTE_STREMIO -> "telos_stremio_app"
            SettingsDeepLinkContract.ROUTE_YOUTUBE -> "telos_youtube_app"
            SettingsDeepLinkContract.ROUTE_YOUTUBE_MUSIC -> "telos_youtube_music_app"
            ROUTE_PHOTOS, SettingsDeepLinkContract.ROUTE_VIEWER -> "telos_viewer_app"
            ROUTE_FILES -> "telos_files_app"
            ROUTE_NETWORK, SettingsDeepLinkContract.ROUTE_NETWORK_SPEED_TEST,
            SettingsDeepLinkContract.ROUTE_NETWORK_PRIVATE_DNS -> "telos_network_app"
            SettingsDeepLinkContract.ROUTE_SYSTEM, ROUTE_SECURITY_HUB -> "telos_system_app"
            SettingsDeepLinkContract.ROUTE_WEATHER -> "telos_weather_app"
            SettingsDeepLinkContract.ROUTE_SYNC -> "telos_sync_app"
            SettingsDeepLinkContract.ROUTE_BACKUP -> "telos_backup_app"
            SettingsDeepLinkContract.ROUTE_TRASH -> "telos_trash_app"
            ROUTE_CALCULATOR -> "telos_calculator_app"
            ROUTE_VOICE_RECORDER -> "telos_voice_recorder_app"
            SettingsDeepLinkContract.ROUTE_CAMERA -> "telos_camera_app"
            ROUTE_SCREEN_RECORDER -> "telos_screen_recorder_app"
            ROUTE_SCREENSHOT -> "telos_screenshot_app"
            ROUTE_NOTES -> "telos_notes_app"
            SettingsDeepLinkContract.ROUTE_ERMIS -> "telos_ermis_app"
            ROUTE_CALENDAR -> "telos_calendar_app"
            ROUTE_DOWNLOADS -> "telos_downloads_app"
            ROUTE_STORE -> "telos_store_app"
            "settings/clock" -> "telos_clock_app"
            "settings/akou" -> "telos_akou_app"
            "settings/magnifier" -> "telos_magnifier_app"
            "settings/flashlight" -> "telos_flashlight_app"
            SettingsDeepLinkContract.ROUTE_FIT -> "telos_fit_app"
            SettingsDeepLinkContract.ROUTE_KEYBOARD -> "telos_keyboard_app"
            SettingsDeepLinkContract.ROUTE_KEEPASS -> "telos_keepass_app"
            SettingsDeepLinkContract.ROUTE_AETHER -> "telos_aether_app"
            else -> return
        }
        de.mm20.launcher2.search.VirtualAppAppearance.applyRecents(this, domain)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (redirectVirtualApp(intent)) {
            finishRedirectHost()
            return
        }
        setIntent(intent)
        applyTaskLabel(intent)
        applyVirtualAppOrientation(
            intent.getStringExtra(EXTRA_ROUTE),
            intent.getStringExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB),
            allowDialerLandscape = false,
        )
        val newRoute = getStartRoute(intent)
        initialRoute = newRoute
    }

    /** A redirect-only root must not remain as a blank Settings card with a replayable base intent. */
    private fun finishRedirectHost() {
        if (isTaskRoot) finishAndRemoveTask() else finish()
    }

    /** Keep every existing entry point (drawer, notifications, shares) on the same app task. */
    private fun redirectVirtualApp(source: Intent): Boolean {
        if (this is TelosAppActivity) return false
        val route = if (source.data?.host == "kvaesitso.mm20.de")
            source.data?.getQueryParameter("route") else source.getStringExtra(EXTRA_ROUTE)
        val id = SettingsDeepLinkContract.virtualAppTaskId(
            route, source.getStringExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB),
        ) ?: return false
        val appActivity = SettingsDeepLinkContract.virtualAppActivityClassName(
            route, source.getStringExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB),
        ) ?: return false
        startActivity(Intent(source).apply {
            setClassName(this@SettingsActivity, appActivity)
            putExtra(EXTRA_ROUTE, route)
            data = android.net.Uri.parse("telos-task://app/$id")
            // Task matching uses component + data, not extras. Never force a duplicate task.
            flags = (flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS).inv()) or
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        })
        return true
    }

    private fun getStartRoute(intent: Intent): NavKey? {
        val routeName = if (intent.data?.host == "kvaesitso.mm20.de") {
            intent.data?.getQueryParameter("route") ?: return null
        } else {
            intent.getStringExtra(EXTRA_ROUTE) ?: return null
        }
        return when(routeName) {
            ROUTE_WEATHER_INTEGRATION -> WeatherIntegrationSettingsRoute
            // === TELOS_PENDING_REVIEW_START: desktop_context_menu_actions ===
            ROUTE_DESKTOP_MODE -> DesktopModeSettingsRoute
            // === TELOS_PENDING_REVIEW_END: desktop_context_menu_actions ===
            ROUTE_MEDIA_INTEGRATION -> MediaIntegrationSettingsRoute
            ROUTE_SEARCH_ACTIONS -> SearchActionsSettingsRoute
            ROUTE_HIDDEN_ITEMS -> HiddenItemsSettingsRoute
            ROUTE_FLOATING_LAUNCHER -> FloatingLauncherSettingsRoute
            ROUTE_SECURITY_HUB -> de.mm20.launcher2.ui.system.SystemDashboardRoute("shield")
            ROUTE_WALLPAPER -> WallpaperSettingsRoute
            ROUTE_WEB_APPS -> WebAppsSettingsRoute
            ROUTE_INTRUDER_PHOTOS -> IntruderPhotosRoute
            ROUTE_STORE -> StoreDashboardRoute(addUrl = intent.getStringExtra(SettingsDeepLinkContract.EXTRA_STORE_URL).orEmpty())
            // === TELOS_PENDING_REVIEW_START: comms_virtual_apps ===
            ROUTE_COMMS -> {
                val tab = intent.getStringExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB) ?: "recents"
                val number = intent.getStringExtra(SettingsDeepLinkContract.EXTRA_DIAL_NUMBER).orEmpty()
                CommsDashboardRoute(
                    initialTab = tab,
                    initialNumber = number,
                    initialBody = intent.getStringExtra(SettingsDeepLinkContract.EXTRA_SMS_BODY).orEmpty(),
                    initialAttachments = intent.getStringArrayListExtra(SettingsDeepLinkContract.EXTRA_SMS_ATTACHMENTS).orEmpty(),
                )
            }
            ROUTE_RADIO -> de.mm20.launcher2.ui.media.hub.MediaHubRoute("radio")
            ROUTE_MUSIC -> de.mm20.launcher2.ui.media.hub.MediaHubRoute("music")
            SettingsDeepLinkContract.ROUTE_YOUTUBE -> de.mm20.launcher2.ui.media.hub.MediaHubRoute(
                "youtube", intent.getStringExtra(SettingsDeepLinkContract.EXTRA_YOUTUBE_LINK)?.takeIf {
                    de.mm20.launcher2.comms.youtube.YouTubeWebLinks.musicTarget(it) != null
                }, intent.getLongExtra(SettingsDeepLinkContract.EXTRA_YOUTUBE_LINK_REQUEST, 0L),
            )
            SettingsDeepLinkContract.ROUTE_YOUTUBE_MUSIC -> de.mm20.launcher2.ui.media.hub.MediaHubRoute(
                "youtube_music", intent.getStringExtra(SettingsDeepLinkContract.EXTRA_YOUTUBE_LINK)?.takeIf {
                    de.mm20.launcher2.comms.youtube.YouTubeWebLinks.musicTarget(it) != null
                }, intent.getLongExtra(SettingsDeepLinkContract.EXTRA_YOUTUBE_LINK_REQUEST, 0L),
            )
            ROUTE_VIDEO -> de.mm20.launcher2.ui.media.hub.MediaHubRoute("video")
            SettingsDeepLinkContract.ROUTE_TV -> de.mm20.launcher2.ui.media.hub.MediaHubRoute("tv")
            SettingsDeepLinkContract.ROUTE_STREMIO -> de.mm20.launcher2.ui.media.hub.MediaHubRoute(
                "stremio",
                intent.getStringExtra(SettingsDeepLinkContract.EXTRA_STREMIO_ADDON),
                intent.getLongExtra(SettingsDeepLinkContract.EXTRA_YOUTUBE_LINK_REQUEST, 0L),
                intent.getStringExtra(SettingsDeepLinkContract.EXTRA_STREMIO_DETAIL_TYPE),
                intent.getStringExtra(SettingsDeepLinkContract.EXTRA_STREMIO_DETAIL_ID),
            )
            ROUTE_MEDIA -> de.mm20.launcher2.ui.media.hub.MediaHubRoute(
                intent.getStringExtra(SettingsDeepLinkContract.EXTRA_MEDIA_SPACE).orEmpty()
            )
            ROUTE_PHOTOS -> de.mm20.launcher2.ui.media.docs.ViewerRoute
            SettingsDeepLinkContract.ROUTE_VIEWER -> de.mm20.launcher2.ui.media.docs.ViewerRoute
            ROUTE_FILES -> de.mm20.launcher2.ui.files.FilesRoute
            "settings/akou" -> de.mm20.launcher2.ui.media.akou.AkouRoute
            "settings/clock" -> de.mm20.launcher2.ui.clock.ClockRoute
            SettingsDeepLinkContract.ROUTE_CAMERA -> de.mm20.launcher2.ui.camera.CameraRoute
            SettingsDeepLinkContract.ROUTE_ERMIS -> de.mm20.launcher2.ui.ermis.ErmisRoute
            "settings/magnifier" -> de.mm20.launcher2.ui.tools.MagnifierRoute
            "settings/flashlight" -> de.mm20.launcher2.ui.tools.FlashlightRoute
            ROUTE_NETWORK -> de.mm20.launcher2.ui.network.NetworkRoute
            SettingsDeepLinkContract.ROUTE_NETWORK_SPEED_TEST -> de.mm20.launcher2.ui.network.NetworkSpeedTestRoute
            SettingsDeepLinkContract.ROUTE_NETWORK_PRIVATE_DNS -> de.mm20.launcher2.ui.network.PrivateDnsRoute
            SettingsDeepLinkContract.ROUTE_SYSTEM -> de.mm20.launcher2.ui.system.SystemDashboardRoute(
                intent.getStringExtra(SettingsDeepLinkContract.EXTRA_SYSTEM_TAB).orEmpty(),
            )
            SettingsDeepLinkContract.ROUTE_WEATHER -> de.mm20.launcher2.ui.weather.WeatherRoute
            SettingsDeepLinkContract.ROUTE_TRASH -> de.mm20.launcher2.ui.trash.TrashUnlockRoute
            ROUTE_CALCULATOR -> de.mm20.launcher2.ui.calculator.CalculatorRoute
            ROUTE_VOICE_RECORDER -> de.mm20.launcher2.ui.voice.VoiceRecorderRoute
            SettingsDeepLinkContract.ROUTE_KEYBOARD -> de.mm20.launcher2.ui.keyboard.TelosKeyboardRoute
            SettingsDeepLinkContract.ROUTE_KEEPASS -> de.mm20.launcher2.ui.keepass.KeePassDashboardRoute
            SettingsDeepLinkContract.ROUTE_AETHER -> de.mm20.launcher2.ui.aether.AetherDashboardRoute()
            SettingsDeepLinkContract.ROUTE_BACKUP -> de.mm20.launcher2.ui.backup.BackupRoute
            SettingsDeepLinkContract.ROUTE_SYNC -> de.mm20.launcher2.ui.sync.SyncRoute
            SettingsDeepLinkContract.ROUTE_FIT -> de.mm20.launcher2.ui.fit.FitDashboardRoute
            ROUTE_SCREEN_RECORDER -> de.mm20.launcher2.ui.screenrec.ScreenRecorderRoute
            ROUTE_SCREENSHOT -> de.mm20.launcher2.ui.screenshot.ScreenshotRoute
            ROUTE_NOTES -> de.mm20.launcher2.ui.notes.NotesRoute
            ROUTE_CALENDAR -> de.mm20.launcher2.ui.calendar.CalendarRoute
            ROUTE_DOWNLOADS -> de.mm20.launcher2.ui.downloads.DownloadsRoute(
                initialUrls = intent.getStringArrayListExtra(SettingsDeepLinkContract.EXTRA_DOWNLOAD_URLS).orEmpty(),
            )
            SettingsDeepLinkContract.ROUTE_TELOS_PAGE -> {
                val topic = intent.getStringExtra(SettingsDeepLinkContract.EXTRA_HELP_TOPIC)
                if (topic.isNullOrEmpty()) de.mm20.launcher2.ui.help.HelpDashboardRoute
                else de.mm20.launcher2.ui.help.HelpLessonRoute(topic)
            }
            "settings/appearance" -> AppearanceSettingsRoute
            "settings/search" -> SearchSettingsRoute
            "settings/gestures" -> GesturesSettingsRoute
            "settings/homescreen" -> HomescreenSettingsRoute
            // === TELOS_PENDING_REVIEW_END: comms_virtual_apps ===
            ROUTE_CRASH_REPORT if (intent.hasExtra(EXTRA_CRASH_REPORT_PATH)) -> {
                CrashReportRoute(intent.getStringExtra(EXTRA_CRASH_REPORT_PATH)!!)
            }
            else -> null
        }
    }

    companion object {
        const val EXTRA_ROUTE = SettingsDeepLinkContract.EXTRA_ROUTE
        const val ROUTE_WEATHER_INTEGRATION = "settings/integrations/weather"
        const val ROUTE_MEDIA_INTEGRATION = "settings/integrations/media"
        const val ROUTE_SEARCH_ACTIONS = "settings/search/searchactions"
        const val ROUTE_HIDDEN_ITEMS = "settings/search/hiddenitems"
        const val ROUTE_FLOATING_LAUNCHER = "settings/floatinglauncher"
        const val ROUTE_SECURITY_HUB = "settings/security-hub"
        const val ROUTE_WALLPAPER = "settings/wallpaper"
        const val ROUTE_WEB_APPS = "settings/webapps"
        // === TELOS_PENDING_REVIEW_START: desktop_context_menu_actions ===
        const val ROUTE_DESKTOP_MODE = "settings/desktopmode"
        // === TELOS_PENDING_REVIEW_END: desktop_context_menu_actions ===
        const val ROUTE_INTRUDER_PHOTOS = SettingsDeepLinkContract.ROUTE_INTRUDER_PHOTOS
        const val ROUTE_STORE = SettingsDeepLinkContract.ROUTE_STORE
        // === TELOS_PENDING_REVIEW_START: comms_virtual_apps ===
        const val ROUTE_COMMS = SettingsDeepLinkContract.ROUTE_COMMS
        const val ROUTE_RADIO = SettingsDeepLinkContract.ROUTE_RADIO
        const val ROUTE_MUSIC = SettingsDeepLinkContract.ROUTE_MUSIC
        const val ROUTE_VIDEO = SettingsDeepLinkContract.ROUTE_VIDEO
        const val ROUTE_MEDIA = SettingsDeepLinkContract.ROUTE_MEDIA
        const val ROUTE_PHOTOS = SettingsDeepLinkContract.ROUTE_PHOTOS
        const val ROUTE_FILES = SettingsDeepLinkContract.ROUTE_FILES
        const val ROUTE_CALCULATOR = SettingsDeepLinkContract.ROUTE_CALCULATOR
        const val ROUTE_VOICE_RECORDER = SettingsDeepLinkContract.ROUTE_VOICE_RECORDER
        const val ROUTE_SCREEN_RECORDER = SettingsDeepLinkContract.ROUTE_SCREEN_RECORDER
        const val ROUTE_SCREENSHOT = SettingsDeepLinkContract.ROUTE_SCREENSHOT
        const val ROUTE_NOTES = SettingsDeepLinkContract.ROUTE_NOTES
        const val ROUTE_CALENDAR = SettingsDeepLinkContract.ROUTE_CALENDAR
        const val ROUTE_DOWNLOADS = SettingsDeepLinkContract.ROUTE_DOWNLOADS
        const val ROUTE_NETWORK = SettingsDeepLinkContract.ROUTE_NETWORK
        // === TELOS_PENDING_REVIEW_END: comms_virtual_apps ===
        const val ROUTE_CRASH_REPORT = "settings/debug/crashreport"
        const val EXTRA_CRASH_REPORT_PATH = "crash_report_path"
    }
}
