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
package com.vitorpamplona.amethyst.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.navigation.favoriteIds
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings
import com.vitorpamplona.amethyst.commons.service.resourceusage.ScreenTimeIntegrator
import com.vitorpamplona.amethyst.commons.ui.app.AppRoot
import com.vitorpamplona.amethyst.commons.ui.layouts.ScreenLayoutSpec
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberScreenLayoutSpec
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.resourceusage.DisplayResourceUsageAlert
import com.vitorpamplona.amethyst.ui.navigation.NavigateIfIntentRequested
import com.vitorpamplona.amethyst.ui.navigation.ObserveIncomingCalls
import com.vitorpamplona.amethyst.ui.navigation.androidDestinations
import com.vitorpamplona.amethyst.ui.screen.ManageRelayServices
import com.vitorpamplona.amethyst.ui.screen.ManageWebOkHttp
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AndroidAccountViewModelHost
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AndroidLoggedInEffects
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedSelectionDrag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabAccountWatcher
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabLayer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabPreloader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabThemeWatcher
import com.vitorpamplona.amethyst.ui.tor.TorConnectionFailureDialog

/** Android's fill of the shared [AppRoot]: the app graph, the Activity's effects and the Android-only screens. */
object AndroidAppRoot : AppRoot {
    override fun createAccountViewModel(account: Account): AccountViewModel =
        AccountViewModel(
            account = account,
            settings = Amethyst.instance.uiState,
            torSettings = Amethyst.instance.torPrefs.value,
            dataSources = Amethyst.instance.sources,
            httpClientBuilder = Amethyst.instance.roleBasedHttpClientBuilder,
            nip05ClientBuilder = { Amethyst.instance.nip05Client },
            host = AndroidAccountViewModelHost(Amethyst.instance),
        )

    /** Android's window size is the configuration's, which already excludes system decorations. */
    @Composable
    override fun rememberScreenLayoutSpec(): ScreenLayoutSpec {
        val configuration = LocalConfiguration.current
        return rememberScreenLayoutSpec(configuration.screenWidthDp, configuration.screenHeightDp)
    }

    @Composable
    override fun AppEffects() {
        // Pauses relay services when the app pauses
        ManageWebOkHttp()
        ManageRelayServices()

        TorConnectionFailureDialog(Amethyst.instance.torManager)
    }

    @Composable
    override fun LoggedInEffects(accountViewModel: AccountViewModel) = AndroidLoggedInEffects(accountViewModel)

    override fun registerDestinations(
        destinations: NavDestinations,
        accountViewModel: AccountViewModel,
    ) = destinations.androidDestinations(accountViewModel)

    @Composable
    override fun ShellOverlay(accountViewModel: AccountViewModel) {
        // The favorites' manifests are kept in LocalCache by the shared shell (FavoriteAppManifestPreloader).
        // Persistent layer that keeps pinned embedded tabs (browser / nsite / napplet) warm by
        // holding their surfaces attached. Below the drawer (drawn by the shell) and below
        // dialogs (separate windows). API 30+ only, matching the embedded-surface feature.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bottomBarItems by accountViewModel.account.settings.syncedSettings.navigation.bottomBarItems
                .collectAsStateWithLifecycle()
            // Move every embedded app to the new account on a switch. Mounted before the layer and
            // the preloader so the previous account's sessions are dropped ahead of the first sweep
            // (an embed WebView's storage profile is fixed at construction, so it must be rebuilt).
            EmbeddedTabAccountWatcher()
            EmbeddedTabLayer(bottomBarItems.favoriteIds())
            // Warm every pinned tab at startup so the first tap is instant (content already local).
            EmbeddedTabPreloader(accountViewModel)
            // Rebuild the warm surfaces in the new theme when the app's DARK/LIGHT preference flips
            // (an embed WebView's theme is fixed at construction, so it can't follow a live switch).
            EmbeddedTabThemeWatcher()
        }
    }

    override fun suspendEdgeSwipe(): Boolean = EmbeddedSelectionDrag.dragging

    @Composable
    override fun NavigationEffects(
        accountViewModel: AccountViewModel,
        nav: Nav,
        sessionManager: AccountSessionManager,
    ) {
        NavigateIfIntentRequested(nav, accountViewModel, sessionManager)
        DisplayResourceUsageAlert(accountViewModel, nav)
        ObserveIncomingCalls(accountViewModel)
    }

    /** Feeds the resource-usage ledger with time-per-screen: the route's name only, never which profile. */
    override fun onScreen(serialName: String?) = Amethyst.instance.screenTime.onScreen(ScreenTimeIntegrator.screenNameOf(serialName))

    override val privacyLockSettings: PrivacyLockSettings get() = Amethyst.instance.privacyLockSettings
}
