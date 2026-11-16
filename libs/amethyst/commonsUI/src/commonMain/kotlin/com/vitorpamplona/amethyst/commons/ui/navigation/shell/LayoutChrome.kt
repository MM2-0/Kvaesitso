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
package com.vitorpamplona.amethyst.commons.ui.navigation.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.model.NavigationLayoutType
import com.vitorpamplona.amethyst.commons.model.navigation.NavArrangement
import com.vitorpamplona.amethyst.commons.model.navigation.showsDeck
import com.vitorpamplona.amethyst.commons.ui.app.AppRoot
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.navigation.deck.DeckArea
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.navigation.tabs.ScreenTabStrip
import com.vitorpamplona.amethyst.commons.ui.navigation.windows.ScreenWindows
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * What a navigation layout adds around the main screen, so the shell asks once instead of testing
 * the layout at each slot: a [topStrip] over everything right of the docked drawer (the tabs), the
 * [deck]'s columns beside the main screen, and the [windows] the main window declares (screens in
 * windows of their own). Each is null where the layout has none.
 */
@Immutable
class LayoutChrome(
    val topStrip: (@Composable () -> Unit)? = null,
    val deck: (@Composable (Modifier) -> Unit)? = null,
    val windows: (@Composable () -> Unit)? = null,
) {
    companion object {
        val None = LayoutChrome()
    }
}

/**
 * The chrome of [layout] for [nav]. The tab strip and the windows follow what [nav] holds, which
 * catches up with a new layout one frame later; the deck shows only where the window has room for
 * the notification panel it replaces (about 1,200 dp), and a narrower one keeps the plain stack.
 */
@Composable
fun rememberLayoutChrome(
    layout: NavigationLayoutType,
    nav: Nav,
    accountViewModel: AccountViewModel,
    root: AppRoot,
): LayoutChrome {
    val arrangement = nav.stacks.arrangement
    val deck = layout.showsDeck && LocalScreenLayout.current.hasRoomForNotificationPanel
    return remember(arrangement, deck, nav, accountViewModel, root) {
        when {
            arrangement == NavArrangement.TABS ->
                LayoutChrome(
                    topStrip = { ScreenTabStrip(nav, accountViewModel) },
                    // A tab can be moved out to a window of its own.
                    windows = { ScreenWindows(nav, accountViewModel, root) },
                )
            arrangement == NavArrangement.WINDOWS -> LayoutChrome(windows = { ScreenWindows(nav, accountViewModel, root) })
            deck -> LayoutChrome(deck = { modifier -> DeckArea(accountViewModel = accountViewModel, mainNav = nav, modifier = modifier) })
            else -> LayoutChrome.None
        }
    }
}
