/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.ui.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.account.AccountState
import com.vitorpamplona.amethyst.commons.account.ui.LoginOrSignupScreen
import com.vitorpamplona.amethyst.commons.account.ui.RemoteSignerAuthPrompt
import com.vitorpamplona.amethyst.commons.appupdate.ui.AppUpdateHost
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.NavigationLayoutType
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.NavBackStacks
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.arrangement
import com.vitorpamplona.amethyst.commons.model.navigation.isSameRoute
import com.vitorpamplona.amethyst.commons.model.navigation.lockScope
import com.vitorpamplona.amethyst.commons.privacylock.LocalPrivacyLockState
import com.vitorpamplona.amethyst.commons.privacylock.LockScope
import com.vitorpamplona.amethyst.commons.relayClient.authCommand.compose.RelayAuthPromptHost
import com.vitorpamplona.amethyst.commons.relayClient.authCommand.compose.RelayAuthSubscription
import com.vitorpamplona.amethyst.commons.relayClient.event.LocalEventFinder
import com.vitorpamplona.amethyst.commons.relayClient.notifyCommand.compose.DisplayNotifyMessages
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountForegroundFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.user.LocalUserFinder
import com.vitorpamplona.amethyst.commons.relayClient.user.LocalUserFinderAccount
import com.vitorpamplona.amethyst.commons.relayClient.user.SelectedListAuthorsSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.loading_account
import com.vitorpamplona.amethyst.commons.service.crashreports.DisplayCrashMessages
import com.vitorpamplona.amethyst.commons.ui.actions.mediaServers.DisplayBlossomSyncProgress
import com.vitorpamplona.amethyst.commons.ui.broadcast.DisplayBroadcastProgress
import com.vitorpamplona.amethyst.commons.ui.components.rememberCoarseLocationPermission
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.toasts.DisplayErrorMessages
import com.vitorpamplona.amethyst.commons.ui.keystorage.KeyStorageHost
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.LocalTabReselectCoordinator
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.TabReselectCoordinator
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.AccountSwitchBottomSheet
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.AccountSwitchMenu
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.DrawerContent
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.PermanentDrawerContent
import com.vitorpamplona.amethyst.commons.ui.navigation.host.LocalNavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavTransitionTier
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavigationHost
import com.vitorpamplona.amethyst.commons.ui.navigation.host.sharedDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.openInBackgroundOnModifiedClick
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.rememberNav
import com.vitorpamplona.amethyst.commons.ui.navigation.routes.getRouteWithArguments
import com.vitorpamplona.amethyst.commons.ui.navigation.shell.AppShellLayout
import com.vitorpamplona.amethyst.commons.ui.navigation.shell.rememberLayoutChrome
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.privacylock.PrivacyLockGate
import com.vitorpamplona.amethyst.commons.ui.privacylock.PrivacyLockHost
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.BottomBarFeedPreloaders
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.BuzzDmDiscoveryPreload
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelPreload
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.BuzzDmJoinedChatTailPreload
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupJoinedChatTailPreload
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupJoinedStatePreload
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.favorites.FavoriteAppManifestPreloader
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPanelHost
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPanelScope
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * The whole app below the platform's window: the login screens while logged off, and the logged-in
 * shell (drawer, bars, navigation host and every shared screen) for the current account. [root]
 * supplies what only the front end has; see [AppRoot].
 */
@Composable
fun AmethystApp(
    sessionManager: AccountSessionManager,
    root: AppRoot,
) {
    root.AppEffects()

    val accountState by sessionManager.accountContent.collectAsStateWithLifecycle()

    Log.d("ActivityLifecycle") { "AmethystApp $accountState $sessionManager" }

    // Outside the privacy lock: unlocking the keys comes before the account (and its lock) loads.
    KeyStorageHost(root.keyStorage) {
        PrivacyLockHost(root.privacyLockSettings, root.blurWalletWhenUnfocused) {
            // Inside the privacy lock: a locked app shows nothing, an update prompt included.
            val appUpdates by root.appUpdates.collectAsStateWithLifecycle()
            AppUpdateHost(appUpdates) {
                Crossfade(
                    targetState = accountState,
                    animationSpec = tween(durationMillis = 100),
                ) { state ->
                    when (state) {
                        is AccountState.Loading -> LoadingSetup()
                        is AccountState.LoggedOff -> LoggedOffSetup(sessionManager)
                        is AccountState.LoggedIn -> LoggedInSetup(state, sessionManager, root)
                    }
                }
            }
            // Over the login screen and the logged-in app alike: a challenge can come from either.
            RemoteSignerAuthPrompt(sessionManager.remoteSignerAuthPrompts)
        }
    }
}

@Composable
fun LoadingSetup() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringRes(Res.string.loading_account))
        }
    }
}

@Composable
fun LoggedOffSetup(sessionManager: AccountSessionManager) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LoginOrSignupScreen(null, sessionManager, isFirstLogin = true)
    }
}

@Composable
private fun LoggedInSetup(
    state: AccountState.LoggedIn,
    sessionManager: AccountSessionManager,
    root: AppRoot,
) {
    // Consume the one-shot route hint set by sign-in/account-switch so it can't re-fire when the
    // page re-composes after the window is recreated (rotation, dark-mode toggle, background).
    val initialRoute = remember(state) { state.route.also { state.route = null } }
    AccountScopedViewModelStore(state.account.signer.pubKey) {
        // Inside the store: the lock screen replaces the screens, not the account's ViewModels
        // and subscriptions, which an unlock would otherwise rebuild from scratch.
        PrivacyLockGate(LockScope.App) {
            LoggedInPage(state.account, initialRoute, sessionManager, root)
        }
    }
}

@Composable
private fun LoggedInPage(
    account: Account,
    route: Route?,
    sessionManager: AccountSessionManager,
    root: AppRoot,
) {
    val accountViewModel: AccountViewModel = rememberViewModel(key = "AccountViewModel") { root.createAccountViewModel(account) }

    // Adds this account to the authentication procedures for relays.
    RelayAuthSubscription(accountViewModel)

    // Loads account information + DMs and Notifications from Relays.
    AccountFilterAssemblerSubscription(accountViewModel)

    // Preloads every joined Concord community's Control planes app-wide (their wraps are addressed to
    // derived stream keys, so the always-on DM tail can't pick them up) — so communities fold, and
    // their channels/metadata/icon appear, without waiting for a Concord screen to be opened.
    ConcordChannelPreload(accountViewModel)

    // Keeps joined NIP-29 groups' relay-signed state (metadata/roster/roles/pins) always current, and
    // their recent chat live for Messages-list previews — app-wide, so opening a group lands on cached
    // content. See amethyst/plans/2026-07-18-nip29-group-chat-subscriptions.md.
    RelayGroupJoinedStatePreload(accountViewModel)
    RelayGroupJoinedChatTailPreload(accountViewModel)

    // Discover the viewer's Buzz DM channels (44100 #p=me) across joined workspaces and keep their
    // messages warm app-wide, so a Buzz DM shows on the Notifications tab / in push without opening it.
    BuzzDmDiscoveryPreload(accountViewModel)
    BuzzDmJoinedChatTailPreload(accountViewModel)

    // Foreground-only loaders: follows-outbox finder + random-relay notifications.
    // Pauses on ON_STOP, resumes on ON_START.
    AccountForegroundFilterAssemblerSubscription(accountViewModel)

    // Relay lists and metadata of the members of every list picked in a top bar, on any screen, so
    // those feeds can reach members nobody follows. Swaps as selections change; pauses on ON_STOP.
    SelectedListAuthorsSubscription(accountViewModel)

    // Pre-loads the feed for every icon the user has pinned to the bottom bar.
    // Subscriptions follow the user's chosen list reactively, not the default 5.
    BottomBarFeedPreloaders(accountViewModel)

    // Updates local cache of the anti-spam filter choice of this user.
    ObserveAntiSpamFilterSettings(accountViewModel)

    WatchLocationPermissions()

    root.LoggedInEffects(accountViewModel)

    // One emoji panel per window. It docks in the view that owns the composer: the main content,
    // a deck column, or a two-pane screen's conversation pane (each an EmojiPanelHost).
    EmojiPanelScope {
        AppNavigation(accountViewModel, route, sessionManager, root)
    }
}

@Composable
private fun WatchLocationPermissions() {
    val locationPermissionState = rememberCoarseLocationPermission()

    val locationSource = LocalAppServices.current.deviceLocation
    LaunchedEffect(locationPermissionState.isGranted) {
        locationSource.setLocationPermission(locationPermissionState.isGranted)
    }
}

@Composable
private fun ObserveAntiSpamFilterSettings(accountViewModel: AccountViewModel) {
    val isSpamActive by accountViewModel.account.settings.syncedSettings.security.filterSpamFromStrangers
        .collectAsStateWithLifecycle(true)

    LocalCache.antiSpam.active = isSpamActive
}

@OptIn(FlowPreview::class)
@Composable
private fun AppNavigation(
    accountViewModel: AccountViewModel,
    firstRoute: Route?,
    sessionManager: AccountSessionManager,
    root: AppRoot,
) {
    // Stack, tabs or windows, as the user picked, among the layouts this front end offers.
    val layoutChoice by accountViewModel.settings.uiSettingsFlow.navigationLayout
        .collectAsState()
    val layouts = LocalAppPlatform.current.navigationLayouts
    val layout = if (layoutChoice in layouts) layoutChoice else NavigationLayoutType.STACK

    // The tabs or windows the last run left open come back, in the layout they were opened in.
    val accountSettings = accountViewModel.account.settings
    val nav =
        rememberNav {
            accountSettings.openScreens.value
                ?.let { NavBackStacks.decode(it) }
                ?.takeIf { it.isSingleScreen && it.arrangement == layout.arrangement() }
        }
    LaunchedEffect(nav, layout) { nav.stacks.changeArrangement(layout.arrangement()) }
    LaunchedEffect(nav) {
        // A cheap copy on every change; the JSON only once they settle, off the main thread.
        snapshotFlow { if (nav.stacks.isSingleScreen) nav.stacks.savedState() else null }
            .debounce(OPEN_SCREENS_SAVE_DEBOUNCE_MS)
            .collect { state ->
                val encoded = state?.let { withContext(Dispatchers.Default) { NavBackStacks.encode(it) } }
                accountSettings.updateOpenScreens(encoded)
            }
    }

    // Shows the "log in to this relay?" dialog when a NIP-42 challenge needs the user to decide.
    // Hosted here rather than per account so one dialog serves the whole shell: challenges
    // arrive off the shared relay socket, not from whatever screen happens to be on top.
    RelayAuthPromptHost(accountViewModel)

    // One layout decision per window size for the whole shell: bottom bar vs rail vs
    // permanent drawer, plus the docked notification panel. Every screen, bar and panel
    // below reads the same spec through LocalScreenLayout. The provider wraps this whole
    // function body so anything added here later is inside it by construction.
    val screenLayout = root.rememberScreenLayoutSpec()
    val tabReselectCoordinator = remember { TabReselectCoordinator() }

    // Mirror the tier for the nav-transition specs, which run outside composition and so
    // can't read LocalScreenLayout (see NavTransitionTier).
    SideEffect { NavTransitionTier.isLargeScreen = screenLayout.isLargeScreen }

    // Built here rather than inside the navigation host, so the drawer, bars and menus can ask it
    // which screens exist before offering a way into one.
    val destinations =
        remember(accountViewModel) {
            NavDestinations().apply {
                sharedDestinations(accountViewModel)
                root.registerDestinations(this, accountViewModel)
            }
        }

    CompositionLocalProvider(
        LocalScreenLayout provides screenLayout,
        LocalNavDestinations provides destinations,
        LocalTabReselectCoordinator provides tabReselectCoordinator,
        // The finders shared composables reach for through the no-arg observeUser* /
        // EventFinderFilterAssemblerSubscription(note) overloads; they error() when unprovided.
        LocalUserFinder provides accountViewModel.dataSources().userFinder,
        LocalUserFinderAccount provides accountViewModel.account,
        LocalEventFinder provides accountViewModel.dataSources().eventFinder,
    ) {
        val chrome = rememberLayoutChrome(layout, nav, accountViewModel, root)

        // A Ctrl/Cmd-click opens what it reaches in the background (tabs and windows layouts).
        Box(Modifier.fillMaxSize().openInBackgroundOnModifiedClick(nav)) {
            AppShellLayout(
                accountViewModel = accountViewModel,
                nav = nav,
                drawerContent = { openAccountSwitcher -> DrawerContent(nav, openAccountSwitcher, accountViewModel) },
                permanentDrawerContent = { openAccountSwitcher ->
                    PermanentDrawerContent(nav, openAccountSwitcher, accountViewModel) { expanded, onDismiss ->
                        AccountSwitchMenu(expanded, onDismiss, accountViewModel, sessionManager)
                    }
                },
                accountSwitcherContent = { AccountSwitchBottomSheet(accountViewModel, sessionManager) },
                suspendEdgeSwipe = root::suspendEdgeSwipe,
                chrome = chrome,
            ) {
                // Inside the providers, so the panel reads the window's layout (its hover tooltips are
                // for large screens).
                EmojiPanelHost {
                    Box(Modifier.fillMaxSize()) {
                        BuildNavigation(nav, destinations, root)
                        // Each pinned nsite/napplet's manifest, pulled into LocalCache and kept on the device,
                        // so a favorite opens, and serves as an intent handler, on every platform.
                        FavoriteAppManifestPreloader(accountViewModel)
                        root.ShellOverlay(accountViewModel)
                    }
                }
            }
        }

        // Screens in windows of their own: the windows layout, or tabs moved out.
        chrome.windows?.invoke()

        OpenFirstRoute(firstRoute, nav)

        root.NavigationEffects(accountViewModel, nav, sessionManager)

        DisplayErrorMessages(accountViewModel.toastManager, accountViewModel, nav)
        DisplayNotifyMessages(accountViewModel, nav)
        DisplayCrashMessages(accountViewModel, nav)
        // Background work, a front page printing elsewhere included: one popup for all of it.
        DisplayBroadcastProgress(accountViewModel, nav)
        DisplayBlossomSyncProgress()
    }
}

/** Opens the screen a sign-in or account switch asked for (an import-follows step, a linked note), once. */
@Composable
private fun OpenFirstRoute(
    route: Route?,
    nav: Nav,
) {
    // An effect, not a read in composition: the route used to reach here through a field set by
    // an effect, after this had already composed, so it opened only when something else happened
    // to recompose the page (a reload with the key from Key Backup landed on Home).
    LaunchedEffect(route) {
        if (route == null) return@LaunchedEffect
        val currentRoute = Snapshot.withoutReadObservation { getRouteWithArguments(route::class, nav) }
        if (!isSameRoute(currentRoute, route)) {
            nav.newStack(route)
        }
    }
}

@Composable
private fun BuildNavigation(
    nav: Nav,
    destinations: NavDestinations,
    root: AppRoot,
) {
    NavigationHost(nav, destinations)

    TrackScreen(nav, destinations, root)
    RelockOnLeave(nav)
}

/**
 * Closes the messages and wallet locks as soon as the screen on top leaves what they guard. Done
 * here, by the route on top, rather than per screen: moving between two guarded screens (the
 * conversation list into a chat) must not lock the user out.
 */
@Composable
private fun RelockOnLeave(nav: Nav) {
    val states = LocalPrivacyLockState.current
    if (states.isEmpty()) return
    LaunchedEffect(nav, states) {
        snapshotFlow { nav.currentRoute.lockScope() }
            .distinctUntilChanged()
            .collectLatest { onTop ->
                // After the screen that left has animated out: locking it mid-animation would turn
                // it into a lock screen on its way out, and on Android pop a fingerprint prompt over
                // the screen the user went to.
                delay(RELOCK_AFTER_LEAVE_MS)
                states.forEach { (scope, state) ->
                    if (scope != LockScope.App && scope != onTop) state.onLeaveRoute()
                }
            }
    }
}

private const val RELOCK_AFTER_LEAVE_MS = 500L

/** Open tabs and windows are saved this long after the last change, not on every click. */
private const val OPEN_SCREENS_SAVE_DEBOUNCE_MS = 1_000L

/** Tells [root] which screen is on top, by its route's name only: never which profile or note. */
@Composable
private fun TrackScreen(
    nav: Nav,
    destinations: NavDestinations,
    root: AppRoot,
) {
    LaunchedEffect(nav, destinations) {
        snapshotFlow { destinations.serialNameOf(nav.currentRoute) }
            .collect { root.onScreen(it) }
    }
    DisposableEffect(nav) {
        onDispose { root.onScreen(null) }
    }
}
