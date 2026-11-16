package de.mm20.launcher2.ui.launcher.scaffold.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import de.mm20.launcher2.ui.comms.CallRecordingsRoute
import de.mm20.launcher2.ui.comms.CallRecordingsScreen
import de.mm20.launcher2.ui.comms.CallerNotesRoute
import de.mm20.launcher2.ui.comms.CallerNotesScreen
import de.mm20.launcher2.ui.comms.CommsDashboardRoute
import de.mm20.launcher2.ui.comms.CommsDashboardScreen
import de.mm20.launcher2.ui.comms.ContactDetailsRoute
import de.mm20.launcher2.ui.comms.ContactDetailsScreen
import de.mm20.launcher2.ui.comms.ContactGroupsRoute
import de.mm20.launcher2.ui.comms.ContactGroupsScreen
import de.mm20.launcher2.ui.comms.DuplicateContactsRoute
import de.mm20.launcher2.ui.comms.DuplicateContactsScreen
import de.mm20.launcher2.ui.comms.FakeCallSettingsRoute
import de.mm20.launcher2.ui.comms.FakeCallSettingsScreen
import de.mm20.launcher2.ui.comms.HiddenContactsRoute
import de.mm20.launcher2.ui.comms.HiddenContactsScreen
import de.mm20.launcher2.ui.comms.ScheduledSmsRoute
import de.mm20.launcher2.ui.comms.ScheduledSmsScreen
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffoldState
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.comms.CommsSettingsRoute
import de.mm20.launcher2.ui.settings.comms.CommsSettingsScreen

/**
 * Which Telos apps can be shown as a page inside the launcher, and the screens they can navigate
 * to from there. The apps are the same screens that the settings activity hosts
 * (see SettingsActivity), just in a back stack of their own.
 *
 * Not embedded (they keep the normal "open" behaviour): the Store, because it is the way back to
 * everything else, installs apps and has many deep links of its own.
 */
internal object TelosPages {

    /** The first screen of the app with the virtual app [key], or null if it cannot be embedded */
    fun rootRoute(key: String): NavKey? = when (key) {
        "telos_phone_app://phone" -> CommsDashboardRoute(initialTab = "recents")
        "telos_messages_app://messages" -> CommsDashboardRoute(initialTab = "messages")
        "telos_media_app://media" -> de.mm20.launcher2.ui.media.hub.MediaHubRoute()
        "telos_radio_app://radio" -> de.mm20.launcher2.ui.media.hub.MediaHubRoute("radio")
        "telos_music_app://music" -> de.mm20.launcher2.ui.media.hub.MediaHubRoute("music")
        "telos_video_app://video" -> de.mm20.launcher2.ui.media.hub.MediaHubRoute("video")
        "telos_tv_app://tv" -> de.mm20.launcher2.ui.media.hub.MediaHubRoute("tv")
        "telos_stremio_app://stremio" -> de.mm20.launcher2.ui.media.hub.MediaHubRoute("stremio")
        "telos_photos_app://photos" -> de.mm20.launcher2.ui.media.docs.ViewerRoute
        "telos_viewer_app://viewer" -> de.mm20.launcher2.ui.media.docs.ViewerRoute
        "telos_files_app://files" -> de.mm20.launcher2.ui.files.FilesRoute
        "telos_calculator_app://calculator" -> de.mm20.launcher2.ui.calculator.CalculatorRoute
        "telos_notes_app://notes" -> de.mm20.launcher2.ui.notes.NotesRoute
        "telos_calendar_app://calendar" -> de.mm20.launcher2.ui.calendar.CalendarRoute
        "telos_downloads_app://downloads" -> de.mm20.launcher2.ui.downloads.DownloadsRoute()
        "telos_network_app://network" -> de.mm20.launcher2.ui.network.NetworkRoute
        "telos_system_app://system" -> de.mm20.launcher2.ui.system.SystemDashboardRoute()
        "telos_voice_recorder_app://voice_recorder" -> de.mm20.launcher2.ui.voice.VoiceRecorderRoute
        "telos_screen_recorder_app://screen_recorder" -> de.mm20.launcher2.ui.screenrec.ScreenRecorderRoute
        "telos_screenshot_app://screenshot" -> de.mm20.launcher2.ui.screenshot.ScreenshotRoute
        "telos_sync_app://sync" -> de.mm20.launcher2.ui.sync.SyncRoute
        "telos_backup_app://backup" -> de.mm20.launcher2.ui.backup.BackupRoute
        "telos_help_app://help" -> de.mm20.launcher2.ui.help.HelpDashboardRoute
        else -> null
    }

    fun canEmbed(key: String): Boolean = rootRoute(key) != null

    /** Same screens as the entries of the same routes in SettingsActivity */
    val entries = entryProvider {
        entry<de.mm20.launcher2.ui.help.HelpDashboardRoute> { de.mm20.launcher2.ui.help.HelpDashboardScreen() }
        entry<de.mm20.launcher2.ui.help.HelpLessonRoute> { de.mm20.launcher2.ui.help.HelpLessonScreen(it.topicId) }
        entry<de.mm20.launcher2.ui.help.HelpAppDetailRoute> { de.mm20.launcher2.ui.help.HelpAppDetailScreen(it.appKey) }
        entry<CommsDashboardRoute> {
            CommsDashboardScreen(it.initialTab, it.initialNumber, it.initialBody, it.initialAttachments)
        }
        entry<ContactDetailsRoute> { ContactDetailsScreen(it.contactId, it.phoneNumber) }
        entry<de.mm20.launcher2.ui.settings.comms.CallerPrivacyRoute> { de.mm20.launcher2.ui.settings.comms.CallerPrivacyScreen() }
        entry<CommsSettingsRoute> { CommsSettingsScreen() }
        entry<FakeCallSettingsRoute> { FakeCallSettingsScreen() }
        entry<DuplicateContactsRoute> { DuplicateContactsScreen() }
        entry<CallRecordingsRoute> { CallRecordingsScreen() }
        entry<CallerNotesRoute> { CallerNotesScreen() }
        entry<HiddenContactsRoute> { HiddenContactsScreen() }
        entry<ContactGroupsRoute> { ContactGroupsScreen() }
        entry<ScheduledSmsRoute> { ScheduledSmsScreen() }
        entry<de.mm20.launcher2.ui.settings.comms.ScrobbleSettingsRoute> {
            de.mm20.launcher2.ui.settings.comms.ScrobbleSettingsScreen()
        }
        entry<de.mm20.launcher2.ui.files.remote.ConnectionsRoute> {
            de.mm20.launcher2.ui.files.remote.ConnectionsScreen()
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
        entry<de.mm20.launcher2.ui.downloads.DownloadsRoute> { de.mm20.launcher2.ui.downloads.DownloadsScreen(it.initialUrls) }
        entry<de.mm20.launcher2.ui.downloads.DownloadsSettingsRoute> { de.mm20.launcher2.ui.downloads.DownloadsSettingsScreen() }
        entry<de.mm20.launcher2.ui.network.NetworkRoute> { de.mm20.launcher2.ui.network.NetworkHomeScreen() }
        entry<de.mm20.launcher2.ui.network.PrivateDnsRoute> { de.mm20.launcher2.ui.network.PrivateDnsScreen() }
        entry<de.mm20.launcher2.ui.network.NetworkSpeedTestRoute> { de.mm20.launcher2.ui.network.NetworkSpeedTestScreen() }
        entry<de.mm20.launcher2.ui.network.NetworkPrivacyRoute> { de.mm20.launcher2.ui.network.NetworkPrivacyScreen() }
        entry<de.mm20.launcher2.ui.network.NetworkTorAppsRoute> { de.mm20.launcher2.ui.network.NetworkTorAppsScreen() }
        entry<de.mm20.launcher2.ui.network.NetworkSettingsRoute> { de.mm20.launcher2.ui.network.NetworkSettingsScreen() }
        entry<de.mm20.launcher2.ui.network.dns.NetworkDnsRoute> { de.mm20.launcher2.ui.network.dns.NetworkDnsScreen() }
        entry<de.mm20.launcher2.ui.network.wireguard.NetworkWireguardRoute> { de.mm20.launcher2.ui.network.wireguard.NetworkWireguardScreen() }
        entry<de.mm20.launcher2.ui.network.wireguard.NetworkWireguardAppsRoute> { de.mm20.launcher2.ui.network.wireguard.NetworkWireguardAppsScreen() }
        entry<de.mm20.launcher2.ui.network.firewall.NetworkFirewallRoute> { de.mm20.launcher2.ui.network.firewall.NetworkFirewallScreen() }
        entry<de.mm20.launcher2.ui.network.firewall.NetworkUniversalRulesRoute> { de.mm20.launcher2.ui.network.firewall.NetworkUniversalRulesScreen() }
        entry<de.mm20.launcher2.ui.network.firewall.NetworkAppRulesRoute> { key -> de.mm20.launcher2.ui.network.firewall.NetworkCustomRulesScreen(key.appId) }
        entry<de.mm20.launcher2.ui.settings.freeze.FreezeSettingsRoute> { de.mm20.launcher2.ui.settings.freeze.FreezeSettingsScreen() }
        entry<de.mm20.launcher2.ui.settings.freeze.SmartFreezeDashboardRoute> { de.mm20.launcher2.ui.settings.freeze.FreezeSettingsScreen() }
        entry<de.mm20.launcher2.ui.settings.freeze.FreezeDashboardRoute> { de.mm20.launcher2.ui.settings.freeze.FreezeDashboardScreen() }
        entry<de.mm20.launcher2.ui.settings.freeze.DeviceOwnerSetupRoute> { de.mm20.launcher2.ui.settings.freeze.DeviceOwnerSetupScreen() }
        entry<de.mm20.launcher2.ui.system.SystemAppOpsRoute> { key -> de.mm20.launcher2.ui.system.SystemAppOpsScreen(key.packageName) }
        entry<de.mm20.launcher2.ui.system.SystemAppDetailsRoute> { key -> de.mm20.launcher2.ui.system.SystemAppDetailsScreen(key.packageName) }
        entry<de.mm20.launcher2.ui.network.firewall.NetworkPackageFirewallRoute> { key -> de.mm20.launcher2.ui.network.firewall.NetworkFirewallScreen(key.packageName) }
        entry<de.mm20.launcher2.ui.network.firewall.NetworkCustomRulesRoute> { de.mm20.launcher2.ui.network.firewall.NetworkCustomRulesScreen() }
        entry<de.mm20.launcher2.ui.network.blocklists.NetworkBlocklistsRoute> { de.mm20.launcher2.ui.network.blocklists.NetworkBlocklistsScreen() }
        entry<de.mm20.launcher2.ui.network.NetworkProxiesRoute> { de.mm20.launcher2.ui.network.NetworkProxiesScreen() }
        entry<de.mm20.launcher2.ui.network.NetworkStatsRoute> { de.mm20.launcher2.ui.network.NetworkStatsScreen() }
        entry<de.mm20.launcher2.ui.network.logs.NetworkLogsRoute> { de.mm20.launcher2.ui.network.logs.NetworkLogsScreen() }
        entry<de.mm20.launcher2.ui.system.SystemDashboardRoute> { de.mm20.launcher2.ui.system.SystemDashboardScreen(it.tab) }
        entry<de.mm20.launcher2.ui.settings.security.TelosSecurityHubRoute> { de.mm20.launcher2.ui.settings.security.TelosSecurityHubScreen() }
        entry<de.mm20.launcher2.ui.trash.TrashUnlockRoute> { de.mm20.launcher2.ui.trash.TrashUnlockScreen() }
        entry<de.mm20.launcher2.ui.notes.NotesRoute> { de.mm20.launcher2.ui.notes.NotesScreen() }
        entry<de.mm20.launcher2.ui.calendar.CalendarRoute> { de.mm20.launcher2.ui.calendar.CalendarScreen() }
        entry<de.mm20.launcher2.ui.calculator.CalculatorRoute> {
            de.mm20.launcher2.ui.calculator.CalculatorScreen()
        }
        entry<de.mm20.launcher2.ui.media.photos.PhotosRoute> {
            de.mm20.launcher2.ui.media.docs.ViewerScreen()
        }
        entry<de.mm20.launcher2.ui.media.docs.ViewerRoute> {
            de.mm20.launcher2.ui.media.docs.ViewerScreen()
        }
        entry<de.mm20.launcher2.ui.media.hub.MediaHubRoute> {
            de.mm20.launcher2.ui.media.hub.MediaHubScreen(it.space)
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
        entry<de.mm20.launcher2.ui.files.FilesRoute> { de.mm20.launcher2.ui.files.FilesScreen() }
    }
}

/**
 * Shows a built-in Telos app as a page of the launcher scaffold (gesture action
 * [de.mm20.launcher2.preferences.GestureAction.TelosPage]).
 *
 * The app's own screen is composed only while this component is the current one, and is disposed
 * when the page is closed. This matters because the scaffold keeps every component alive off
 * screen: without this, all assigned apps would load their data, ask for permissions and mark
 * themselves as "on screen" for the crash guard (VirtualAppGuardEffect) while invisible.
 * It also means the app starts at its first screen each time the page is opened.
 */
internal class TelosAppPageComponent private constructor(
    private val key: String,
) : ScaffoldComponent() {

    companion object {
        private val cache = mutableMapOf<String, TelosAppPageComponent>()

        /** One instance per app, null if the app cannot be embedded */
        fun forKey(key: String): TelosAppPageComponent? {
            if (!TelosPages.canEmbed(key)) return null
            return cache.getOrPut(key) { TelosAppPageComponent(key) }
        }
    }

    // The page shows its own title bar and background
    override val showSearchBar = false

    // Text input in notes, messages, the dialer search, ...
    override val hasIme = true

    // Leaves the app, resets to the first screen
    override val survivesPause = false

    // The scroll position of the app's lists is not known here. Lists consume their own drags
    // first and only pass on what is left over, so closing the page by a drag in the swipe
    // direction does not fight scrolling. Reporting "at the edge" lets such a drag close the page.
    override val isAtTop: State<Boolean?> = mutableStateOf(true)
    override val isAtBottom: State<Boolean?> = mutableStateOf(true)

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState,
    ) {
        // Not on screen: compose nothing (see class doc). Reading the progress here (instead of
        // only checking the component) also keeps the app away from the home page when a drag is
        // cancelled. The back handlers inside are registered after the scaffold's own one, so
        // they get the back gesture first.
        if (state.currentComponent !== this || state.currentProgress <= 0f) return

        val root = remember(key) { TelosPages.rootRoute(key) } ?: return
        val backStack = rememberNavBackStack(root)
        de.mm20.launcher2.ui.component.PersistNavigation(backStack, root)

        // The back button of an app's first screen removes it from the stack, there is nothing
        // below it: close the page then.
        val isEmpty = backStack.isEmpty()
        LaunchedEffect(isEmpty) {
            if (isEmpty) state.navigateBack()
        }

        CompositionLocalProvider(LocalBackStack provides backStack) {
            de.mm20.launcher2.ui.component.ProvidePerAppFontScale(
                de.mm20.launcher2.ui.component.telosFontKeyFromPageKey(key),
            ) {
            de.mm20.launcher2.ui.component.VirtualAppTheme {
                Box(
                    modifier = modifier
                        .background(androidx.compose.ui.graphics.Color.Transparent)
                        // The apps add the system bars themselves. Applied first, so that the
                        // navigation bar is not added a second time while the keyboard is open.
                        .imePadding()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                ) {
                    if (!isEmpty) {
                        val pageEffects = de.mm20.launcher2.ui.component.rememberVirtualAppPageEffects()
                        NavDisplay(
                            backStack = backStack,
                            onBack = { backStack.removeLastOrNull() },
                            entryProvider = TelosPages.entries,
                            transitionSpec = { de.mm20.launcher2.ui.component.telosPageTransition(pageEffects) },
                            popTransitionSpec = { de.mm20.launcher2.ui.component.telosPagePopTransition(pageEffects) },
                            predictivePopTransitionSpec = { de.mm20.launcher2.ui.component.telosPagePopTransition(pageEffects) },
                        )
                    }
                }
            }
            }
        }
    }
}
