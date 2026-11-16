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

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.vitorpamplona.amethyst.commons.model.navigation.NavBackStacks
import com.vitorpamplona.amethyst.commons.model.navigation.NavRequest
import com.vitorpamplona.amethyst.commons.model.navigation.NavStackEntry
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * The back-stack entry of the screen being composed, provided by the navigation display to each
 * destination. Null outside one (the shell chrome, the drawer).
 */
val LocalNavStackEntry = staticCompositionLocalOf<NavStackEntry?> { null }

/**
 * The navigation every screen calls, for any layout: each verb becomes one [NavRequest] that the
 * window's [NavBackStacks] model reads its own way (a push, a tab, a window). Requests run after the
 * keyboard settles.
 */
@Stable
class Nav(
    val stacks: NavBackStacks,
    override val navigationScope: CoroutineScope,
    /**
     * Awaited before every transition below. Leaving a screen while the soft keyboard is still
     * animating strands `imePadding()` app-wide; see [ImeSettler]. Every in-app navigation goes
     * through this class, so this is the one place that has to get it right — no screen, top bar
     * or back handler needs to think about the keyboard on its way out.
     */
    private val ime: ImeSettler = ImeSettler.None,
) : INav {
    override val drawerState = DrawerState(DrawerValue.Closed)

    /** Set by the shell when the layout tier docks the drawer permanently. */
    override var isDrawerDocked: Boolean by mutableStateOf(false)

    /** The screen on top. Snapshot state: composables reading it recompose when it changes. */
    val currentRoute: Route get() = stacks.topRoute

    /**
     * Set by the shell when the press that is about to navigate held Ctrl or Cmd: the next request
     * then opens in the background (a tab or window that does not take the focus). Read once.
     */
    var openNextInBackground: Boolean = false

    /** What the navigation host displays: the stack, or the one screen of a tab or section. */
    fun shownEntries(): List<NavStackEntry> = stacks.shown(wide = isDrawerDocked)

    /** Runs [request] once the keyboard settles: the one way every navigation reaches the model. */
    fun open(request: NavRequest) {
        val asked = if (openNextInBackground) request.copy(background = true) else request
        openNextInBackground = false
        navigationScope.launch {
            ime.settle()
            stacks.open(asked)
        }
    }

    /** [open] for a route that has to be resolved first; nothing opens when it resolves to null. */
    fun open(
        kind: NavRequest.Kind,
        origin: Long? = null,
        computeRoute: suspend () -> Route?,
    ) {
        val background = openNextInBackground
        openNextInBackground = false
        navigationScope.launch {
            ime.settle()
            computeRoute()?.let { stacks.open(NavRequest(it, kind, origin, background)) }
        }
    }

    override fun closeDrawer() {
        navigationScope.launch { drawerState.close() }
    }

    override fun openDrawer() {
        // Nothing renders the modal drawer while it is docked; opening the state would
        // only strand an Open value for the next modal tier to trip over.
        if (isDrawerDocked) return
        navigationScope.launch { drawerState.open() }
    }

    override fun nav(route: Route) = open(NavRequest(route))

    override fun nav(computeRoute: suspend () -> Route?) = open(NavRequest.Kind.Push, computeRoute = computeRoute)

    /** Opened from the drawer: a top-level place that keeps the bar (see [showsBottomBar]). */
    override fun navDrawer(route: Route) = open(NavRequest(route, NavRequest.Kind.Drawer))

    override fun navDrawer(computeRoute: suspend () -> Route?) = open(NavRequest.Kind.Drawer, computeRoute = computeRoute)

    override fun newStack(route: Route) = open(NavRequest(route, NavRequest.Kind.Restart))

    /**
     * A nav-bar tap asks for a section, never for whatever the user pushed on top of one. On phones
     * the bar shows only on tab roots and on drawer screens, but the large-screen rail stays on
     * screen the whole time and is routinely tapped from three deep.
     */
    override fun navBottomBar(route: Route) = open(NavRequest(route, NavRequest.Kind.Section))

    override fun <T : Route> popUpTo(
        route: Route,
        klass: KClass<T>,
    ) = open(NavRequest(route, NavRequest.Kind.Replace(klass)))

    override fun popBack() = back(null)

    /** Back from [from] (the screen on top when null), once the keyboard settles. */
    fun back(from: NavStackEntry?) {
        navigationScope.launch {
            ime.settle()
            stacks.back(from)
        }
    }

    /** The navigation of the screen [entry]: what its destination draws with. */
    fun forEntry(entry: NavStackEntry): INav = EntryNav(this, entry)

    @Composable
    override fun canPop(): Boolean {
        // Decide the back arrow / bottom-bar visibility from THIS screen's own entry, not the top
        // of the stack. A pop commits the moment it is accepted (most visibly during a predictive
        // back-swipe, whose exit animation is long and finger-driven): the top flips to the
        // destination while the screen being dismissed is still on screen, sliding out and still
        // composing its top bar. Reading the top there dropped the arrow before the outgoing screen
        // had finished leaving. An entry is intrinsic to its screen, so the arrow stays put until
        // the screen itself is gone. Outside a destination (shell chrome, drawer) use the top.
        return stacks.model.canGoBack(LocalNavStackEntry.current ?: stacks.top, wide = isDrawerDocked)
    }

    /** Tab roots, the start screen and drawer screens show the bar; a screen window never does. */
    @Composable
    override fun showsBottomBar(): Boolean = stacks.model.showsBar(LocalNavStackEntry.current ?: stacks.top, wide = isDrawerDocked)
}

/**
 * One screen's navigation: [host]'s, with every request marked as coming from [entry]. A tab or
 * window then opens beside the screen that asked, a replace acts on it, back closes or pops it, and
 * its back arrow and bar answer for it rather than for whatever is on top.
 */
@Stable
private class EntryNav(
    private val host: Nav,
    private val entry: NavStackEntry,
) : INav by host {
    private fun request(
        route: Route,
        kind: NavRequest.Kind = NavRequest.Kind.Push,
    ) = host.open(NavRequest(route, kind, origin = entry.id))

    override fun nav(route: Route) = request(route)

    override fun nav(computeRoute: suspend () -> Route?) = host.open(NavRequest.Kind.Push, entry.id, computeRoute)

    override fun navDrawer(route: Route) = request(route, NavRequest.Kind.Drawer)

    override fun navDrawer(computeRoute: suspend () -> Route?) = host.open(NavRequest.Kind.Drawer, entry.id, computeRoute)

    override fun newStack(route: Route) = request(route, NavRequest.Kind.Restart)

    override fun navBottomBar(route: Route) = request(route, NavRequest.Kind.Section)

    override fun <T : Route> popUpTo(
        route: Route,
        klass: KClass<T>,
    ) = request(route, NavRequest.Kind.Replace(klass))

    override fun popBack() = host.back(entry)

    @Composable
    override fun canPop(): Boolean = host.stacks.model.canGoBack(entry, wide = host.isDrawerDocked)

    @Composable
    override fun showsBottomBar(): Boolean = host.stacks.model.showsBar(entry, wide = host.isDrawerDocked)

    @Composable
    override fun titleShownOutside(): Boolean = host.stacks.model.titleShownOutside(entry, wide = host.isDrawerDocked)
}
