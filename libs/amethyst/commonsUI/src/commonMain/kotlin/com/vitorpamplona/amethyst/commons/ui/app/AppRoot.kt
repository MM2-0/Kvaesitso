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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.appupdate.AppUpdateController
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageController
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.NavigationLayoutType
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings
import com.vitorpamplona.amethyst.commons.ui.layouts.ScreenLayoutSpec
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberScreenLayoutSpec
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.navigation.windows.ScreenWindowSpec
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What [AmethystApp] needs from the front end that hosts it: how to build the logged-in account's
 * view model, the screens only that platform has, and the platform's own effects and layers around
 * the shared shell. Android's Activity and the desktop window each hand one in.
 */
@Stable
interface AppRoot {
    /** Builds the logged-in account's view model. Runs once per account: its store keeps the result. */
    fun createAccountViewModel(account: Account): AccountViewModel

    /**
     * The navigation tier for the window as the platform measures it. The default reads the
     * composition's container, which on a desktop window is the window's content area.
     */
    @Composable
    fun rememberScreenLayoutSpec(): ScreenLayoutSpec {
        val size = LocalWindowInfo.current.containerSize
        val density = LocalDensity.current
        val widthDp =
            remember(size.width, density) {
                with(density) {
                    size.width
                        .toDp()
                        .value
                        .toInt()
                }
            }
        val heightDp =
            remember(size.height, density) {
                with(density) {
                    size.height
                        .toDp()
                        .value
                        .toInt()
                }
            }
        return rememberScreenLayoutSpec(widthDp, heightDp)
    }

    /** Effects for as long as the app is on screen, logged in or not: relay and HTTP lifecycles, Tor alerts. */
    @Composable
    fun AppEffects() {}

    /** Effects that follow the logged-in account: push registration, the signer app's launcher. */
    @Composable
    fun LoggedInEffects(accountViewModel: AccountViewModel) {}

    /** Registers the destinations whose screens only this platform has. */
    fun registerDestinations(
        destinations: NavDestinations,
        accountViewModel: AccountViewModel,
    ) {}

    /** Layers drawn over the navigation host inside the shell, such as embedded app surfaces. */
    @Composable
    fun ShellOverlay(accountViewModel: AccountViewModel) {}

    /**
     * One OS window holding [content]: a screen of the windows layout, or a tab moved out. Only a
     * front end that offers [NavigationLayoutType.WINDOWS] (see `AppPlatform.navigationLayouts`)
     * draws it.
     */
    @Composable
    fun ScreenWindow(
        window: ScreenWindowSpec,
        content: @Composable () -> Unit,
    ) {}

    /** Whether the drawer's edge swipe should wait, e.g. while an embedded surface drags a selection. */
    fun suspendEdgeSwipe(): Boolean = false

    /** Hosts beside the shell: routing what the app was opened with, call screens, platform alerts. */
    @Composable
    fun NavigationEffects(
        accountViewModel: AccountViewModel,
        nav: Nav,
        sessionManager: AccountSessionManager,
    ) {}

    /** The destination on top, by its route's serial name without arguments; null once none is. */
    fun onScreen(serialName: String?) {}

    /** The privacy lock's settings, loaded before the first frame; null runs the app without a lock. */
    val privacyLockSettings: PrivacyLockSettings? get() = null

    /** Whether the wallet blurs while the window is not focused, once its lock is on (the desktop). */
    val blurWalletWhenUnfocused: Boolean get() = false

    /**
     * Key storage the user deals with (the desktop's OS keyring or password file): its status,
     * the questions it asks, Key Backup. Null where keys are kept without asking.
     */
    val keyStorage: KeyStorageController? get() = null

    /**
     * The app's own updater, where the app updates itself (the desktop); null where a store does,
     * and until the platform has built it (off the main thread).
     */
    val appUpdates: StateFlow<AppUpdateController?> get() = NoAppUpdates
}

private val NoAppUpdates: StateFlow<AppUpdateController?> = MutableStateFlow(null)
