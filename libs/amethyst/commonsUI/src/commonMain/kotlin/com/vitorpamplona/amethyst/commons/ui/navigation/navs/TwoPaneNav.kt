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
package com.vitorpamplona.amethyst.commons.ui.navigation.navs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * The list pane's nav of a list/detail screen: a route [isDetailRoute] accepts opens in the detail
 * pane ([innerNav]) instead of pushing a full screen; everything else goes to [nav] unchanged.
 */
class TwoPaneNav(
    private val nav: INav,
    override val navigationScope: CoroutineScope,
    private val isDetailRoute: (Route) -> Boolean,
    /** The chat open in the detail pane; from [rememberDetailPaneSelection]. */
    val innerNav: MutableState<Route?>,
) : INav by nav {
    override fun nav(route: Route) {
        if (isDetailRoute(route)) {
            innerNav.value = route
        } else {
            nav.nav(route)
        }
    }

    override fun nav(computeRoute: suspend () -> Route?) {
        navigationScope.launch {
            val route = computeRoute()
            if (route != null) {
                if (isDetailRoute(route)) {
                    innerNav.value = route
                } else {
                    nav.nav(route)
                }
            }
        }
    }
}

/**
 * The detail pane's nav of a list/detail screen. The screen in the pane is not on the back stack:
 * it shows no back arrow and no bottom bar, and a pop (leaving a group, deleting it) closes the
 * pane instead of popping the list underneath it. Other navigation goes through [twoPane], so a
 * link to a sibling chat swaps the pane and anything else pushes a full screen.
 */
class DetailPaneNav(
    private val twoPane: TwoPaneNav,
) : INav by twoPane {
    @Composable
    override fun canPop(): Boolean = false

    @Composable
    override fun showsBottomBar(): Boolean = false

    override fun popBack() {
        twoPane.innerNav.value = null
    }
}

private val selectionJson = Json { ignoreUnknownKeys = true }

/** Saves the open chat as its route's JSON; restores nothing when it can't be read (an app update renamed a route). */
private val DetailPaneSelectionSaver =
    Saver<MutableState<Route?>, String>(
        save = { state -> state.value?.let { selectionJson.encodeToString(Route.serializer(), it) } },
        restore = { mutableStateOf(runCatching { selectionJson.decodeFromString(Route.serializer(), it) }.getOrNull()) },
    )

/**
 * The chat open in a list/detail screen's detail pane. Saveable, not a plain `remember`: the nav host
 * only keeps saved state for an entry that leaves the composition, so the open chat survives a push
 * on top of the list (a profile, a member list) and the trip back, as well as configuration changes.
 */
@Composable
fun rememberDetailPaneSelection(): MutableState<Route?> = rememberSaveable(saver = DetailPaneSelectionSaver) { mutableStateOf(null) }
