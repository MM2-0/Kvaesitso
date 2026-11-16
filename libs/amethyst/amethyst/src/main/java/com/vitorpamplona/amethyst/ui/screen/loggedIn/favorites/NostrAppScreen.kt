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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.favorites

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.ui.pill.AccessInfoSheet
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPillEvent
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPillUi
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.favoriteIds
import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_unsupported
import com.vitorpamplona.amethyst.commons.resources.favorite_app_still_loading
import com.vitorpamplona.amethyst.commons.resources.favorite_app_unavailable
import com.vitorpamplona.amethyst.commons.resources.favorite_apps
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.favorites.FavoriteAppLauncher
import com.vitorpamplona.amethyst.napplet.NsiteNetworkRegistry
import com.vitorpamplona.amethyst.napplethost.NappletHostContract
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabChrome
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabFactory
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabHost

/**
 * A **Nostr app** — an nSite or nApplet, reached by [coordinate] (favorited or not) — rendered as an
 * **in-app tab**. The verified-blob sandbox surface (hosted in
 * the keyless `:napplet` process by `NappletHostService`) is drawn by the persistent [EmbeddedTabHost]
 * layer, which keeps the session warm across tab swaps — the surface stays attached and just moves over
 * the area this screen reserves. The **trusted chrome** (sandbox shield, app name, "what it can access")
 * is drawn here in the main process; the sandbox must never draw chrome the user is meant to trust. The
 * pop-out hands the app to the full-screen `NappletHostActivity`.
 *
 * Only bottom-row favorites stay warm; otherwise the session is evicted (restarted) when the screen
 * leaves. Requires API 30+ for the cross-process surface.
 */
@Composable
fun NostrAppScreen(
    coordinate: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        EmbeddedNostrAppTab(coordinate, accountViewModel, nav)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringRes(Res.string.browser_unsupported),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmbeddedNostrAppTab(
    coordinate: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val appStillLoadingStr = stringRes(Res.string.favorite_app_still_loading)
    val context = LocalContext.current
    // Matches FavoriteApp.NostrApp.id, so warm-keep membership lines up with the bottom-bar favorites.
    val id = "nostr:$coordinate"

    // Mint the verified launch params (a fresh token per resolve); null until the event loads. Re-minted
    // on a theme flip (the params carry the resolved theme into the sandbox host's WebView).
    // Bumped when the user re-routes an nSite (Tor ↔ open web): the session is rebuilt with the new route,
    // as the full-screen host relaunches itself.
    var networkEpoch by remember(coordinate) { mutableIntStateOf(0) }
    val params = remember(coordinate, EmbeddedTabHost.rebuildEpoch, networkEpoch) { FavoriteAppLauncher.embedParams(context, coordinate) }
    if (params == null) {
        UnavailableTab(coordinate, accountViewModel, nav)
        return
    }

    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

    val title = params.getString(NappletHostContract.EXTRA_TITLE).orEmpty()
    val capLabels = params.getStringArrayList(NappletHostContract.EXTRA_CAP_LABELS).orEmpty()
    val profile = HostProfile.fromName(params.getString(NappletHostContract.EXTRA_HOST_PROFILE))
    val useTor = params.getBoolean(NappletHostContract.EXTRA_USE_TOR, true)
    // Only nSites have a route of their own to choose, and only when Tor is on.
    val torType by Amethyst.instance.torPrefs.torType
        .collectAsStateWithLifecycle()
    val torOn = if (profile.exposesNetwork && torType != TorType.OFF) useTor else null

    var showAccess by remember { mutableStateOf(false) }

    val apps by Amethyst.instance.favoriteApps.favorites
        .collectAsStateWithLifecycle()
    val isFavorite = remember(apps, coordinate) { apps.any { it.id == "nostr:$coordinate" } }

    val controller =
        remember(id, EmbeddedTabHost.rebuildEpoch, networkEpoch) {
            EmbeddedTabFactory.acquireNostrApp(context, coordinate, params, backgroundColor)
        }
    // Seeded from the controller, which outlives this screen (it leaves composition on every bottom-bar
    // switch), so coming back keeps Back working inside the app and the pill showing the zoom in effect.
    var canGoBack by remember(controller) { mutableStateOf(controller.lastCanGoBack) }
    var textZoom by remember(controller) { mutableIntStateOf(controller.currentTextZoom) }

    // Keep the controller callbacks fresh (cheap, need the latest closures).
    SideEffect {
        controller.onStateChanged = { canGoBack = it }
    }

    // The permission-ledger key is the addressable coordinate without its kind prefix (`pubkey:dtag`),
    // matching how the Connected Apps screen keys napplet/nsite grants (see NappletIdentity.coordinate).
    val permissionCoordinate = remember(coordinate) { coordinate.substringAfter(':') }

    // Off for this site, yet on Tor because another open page needs it (Tor always wins in `:napplet`).
    val torForced = controller.isTorForced

    // Stable per app (title/coordinate/isFavorite don't change often), so the tab layer isn't recomposed every frame.
    val chrome =
        remember(title, coordinate, isFavorite, torOn, torForced, textZoom, controller) {
            EmbeddedTabChrome(
                ui =
                    BrowserPillUi(
                        title = title.ifBlank { coordinate },
                        chrome =
                            BrowserChrome.State(
                                surface = if (profile == HostProfile.WEBSITE) BrowserChrome.Surface.NSITE else BrowserChrome.Surface.NAPPLET,
                                presentation = BrowserChrome.Presentation.EMBEDDED,
                                url = "",
                                startUrl = "",
                                torOn = torOn,
                                torForced = torForced,
                                hasAccessInfo = true,
                            ),
                        isFavorite = isFavorite,
                        textZoom = textZoom,
                    ),
                onEvent = { event ->
                    if (event is BrowserPillEvent.TextZoom) {
                        textZoom = event.percent
                        controller.setTextZoom(event.percent)
                    }
                    when ((event as? BrowserPillEvent.Action)?.action) {
                        BrowserChrome.Action.RELOAD -> controller.reload()
                        BrowserChrome.Action.OPEN_FULL_SCREEN ->
                            FavoriteAppLauncher.launch(context, FavoriteApp.NostrApp(coordinate, title, System.currentTimeMillis()), appStillLoadingStr)
                        BrowserChrome.Action.ACCESS_INFO -> showAccess = true
                        BrowserChrome.Action.TOR -> {
                            // Persist the new route, then rebuild the session so it loads that way.
                            NsiteNetworkRegistry.set(permissionCoordinate, !useTor)
                            EmbeddedTabHost.rebuild(id)
                            networkEpoch++
                        }
                        BrowserChrome.Action.SITE_SETTINGS -> nav.nav(Route.ConnectedAppDetail(permissionCoordinate))
                        BrowserChrome.Action.FAVORITE -> {
                            val favId = "nostr:$coordinate"
                            val favorites = Amethyst.instance.favoriteApps
                            if (favorites.isFavorite(favId)) {
                                favorites.remove(favId)
                            } else {
                                favorites.add(FavoriteApp.NostrApp(coordinate, title, System.currentTimeMillis()))
                            }
                        }
                        else -> Unit
                    }
                },
            )
        }
    // Publish the top-sheet controls to the tab layer (drawn over the z-below surface). In a SideEffect
    // so it runs after [setActive]; the host short-circuits the identical remembered instance.
    SideEffect { EmbeddedTabHost.setActiveChrome(id, chrome) }

    val bottomBarFlow = accountViewModel.account.settings.syncedSettings.navigation.bottomBarItems
    val entryLifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(id) {
        val token = EmbeddedTabHost.setActive(id)
        EmbeddedTabHost.hold(id)
        onDispose {
            EmbeddedTabHost.clearActiveIfOwner(token)
            EmbeddedTabHost.clearActiveChrome(id)
            // Only bottom-row apps stay warm; anything else restarts once the user actually leaves it — not
            // when a screen is merely pushed on top, and not when a re-navigation to this same tab already
            // composed a new screen on the same session.
            if (EmbeddedTabHost.release(id)) {
                EmbeddedTabHost.releaseWhenGone(id, entryLifecycle) { id in bottomBarFlow.value.favoriteIds() }
            }
        }
    }

    // No lifecycle handling here: the tab layer tells every warm tab when the app leaves the screen
    // (EmbeddedTabHost.onAppStopped), which holds the applet's acting requests at once and pauses its page on
    // the relays' 30 s schedule.

    PlatformBackHandler(enabled = canGoBack) { controller.back() }

    if (showAccess) {
        Dialog(onDismissRequest = { showAccess = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                AccessInfoSheet(
                    title = title.ifBlank { coordinate },
                    isWebsite = profile == HostProfile.WEBSITE,
                    capabilities = capLabels,
                    torOn = torOn,
                    onManagePermissions = {
                        showAccess = false
                        nav.nav(Route.ConnectedAppDetail(permissionCoordinate))
                    },
                    onDone = { showAccess = false },
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
        }
    }

    Scaffold(
        bottomBar = {
            AppBottomBar(Route.NostrApp(coordinate), nav, accountViewModel) { route -> nav.navBottomBar(route) }
        },
    ) { padding ->
        // Reserve the full content area; the warm surface, its top sheet, and the loading/error overlay
        // are all drawn over these bounds by the tab layer.
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { EmbeddedTabHost.reportBounds(it.boundsInWindow()) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnavailableTab(
    coordinate: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringRes(Res.string.favorite_apps)) }) },
        bottomBar = {
            AppBottomBar(Route.NostrApp(coordinate), nav, accountViewModel) { route -> nav.navBottomBar(route) }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringRes(Res.string.favorite_app_unavailable),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
