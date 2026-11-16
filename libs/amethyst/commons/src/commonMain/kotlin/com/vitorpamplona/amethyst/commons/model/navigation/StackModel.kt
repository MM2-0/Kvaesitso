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
package com.vitorpamplona.amethyst.commons.model.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlin.reflect.KClass

/**
 * The stack layout (and the deck's main screen): [stack] is what is on screen, bottom to top. It is
 * never empty and normally starts at [Route.Home]. [savedTabs] holds the root entry of every
 * bottom-bar tab the user left: a tab survives being left — its ViewModels and scroll position —
 * because its entry stays here, and so stays in the list whose state Navigation 3 keeps alive.
 * Nothing *above* a tab root is ever saved: switching tabs drops it first, so returning to a tab
 * always lands on the tab itself.
 */
@Stable
class StackModel(
    ids: EntryIds,
    initialStack: List<NavStackEntry>,
    initialSavedTabs: Map<Route, NavStackEntry> = emptyMap(),
) : NavModel(ids) {
    val stack: SnapshotStateList<NavStackEntry> = mutableStateListOf<NavStackEntry>().apply { addAll(initialStack) }

    val savedTabs: SnapshotStateMap<Route, NavStackEntry> = mutableStateMapOf<Route, NavStackEntry>().apply { putAll(initialSavedTabs) }

    override val arrangement: NavArrangement get() = NavArrangement.STACK

    override val entries: List<NavStackEntry> get() = stack

    override val top: NavStackEntry get() = stack.last()

    override fun open(request: NavRequest) {
        when (val kind = request.kind) {
            NavRequest.Kind.Push -> push(request.route)
            NavRequest.Kind.Drawer -> push(request.route, drawerRoot = true)
            NavRequest.Kind.Section -> switchTab(request.route)
            NavRequest.Kind.Restart -> newStack(request.route)
            is NavRequest.Kind.Replace -> popUpTo(request.route, kind.klass)
        }
    }

    /** Pops the top. Asked by a screen that is no longer on top (already popped, or covered since), does nothing. */
    override fun back(from: NavStackEntry?): Boolean {
        if (from != null && from != top) return false
        return pop()
    }

    /**
     * Hidden on tab roots (reached via the bottom nav), on Home (the start destination) and on the
     * screen at the bottom of the stack (a deck column's first screen): nothing sits below any of
     * them that a back arrow could return to, and [pop] never pops the last screen. A screen already
     * popped is out of the stack and keeps its arrow while it animates away.
     */
    override fun canGoBack(
        entry: NavStackEntry,
        wide: Boolean,
    ): Boolean = !entry.tabRoot && entry.route != Route.Home && stack.firstOrNull() != entry

    override fun shown(wide: Boolean): List<NavStackEntry> = stack.toList()

    override fun retained(): List<NavStackEntry> = stack + savedTabs.values

    /** Pushes [route], unless it is already the screen on top. Returns whether it pushed. */
    fun push(
        route: Route,
        drawerRoot: Boolean = false,
    ): Boolean {
        if (top.route == route) return false
        stack.add(ids.entry(route, drawerRoot = drawerRoot))
        return true
    }

    /** Pops the top screen; the last one is never popped. Returns whether it popped. */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /**
     * Opens [route] as the start of a fresh stretch of history: drops the most recent copy of
     * [route] and everything above it, then shows [route] once. When the screen left on top is the
     * same destination with other arguments, that screen takes the new arguments in place rather
     * than stacking a second copy — the same entry, so its root flags carry over and nothing
     * animates, as Navigation 2's single-top launch behaved. Its content is a new
     * [NavStackEntry.routeVersion], though: the previous arguments' ViewModels are cleared.
     */
    fun newStack(route: Route) {
        val existing = stack.indexOfLast { it.route == route }
        if (existing >= 0) removeFrom(existing)
        if (stack.isNotEmpty() && top.route::class == route::class) {
            top.reArgue(route)
        } else {
            stack.add(ids.entry(route))
        }
    }

    /** Drops the most recent screen of [klass] and everything above it, then pushes [route]. */
    fun popUpTo(
        route: Route,
        klass: KClass<out Route>,
    ) {
        val existing = stack.indexOfLast { klass.isInstance(it.route) }
        if (existing >= 0) removeFrom(existing)
        stack.add(ids.entry(route))
    }

    /**
     * A bottom-bar or rail tap: lands on [route]'s tab itself, never on something pushed on top of a
     * tab. The tab being left keeps its root (and so its state) in [savedTabs]; [Route.Home] stays at
     * the bottom so back from any tab returns to Home, and back from Home leaves the app.
     */
    fun switchTab(route: Route) {
        popPushesAboveTabRoot()

        // Dropping those pushes is often the whole job: re-tapping the tab the user is inside, and
        // every tap of Home from somewhere inside Home, end here.
        if (isTab(top, route)) {
            top.tabRoute = route
            return
        }

        val homeIndex = stack.indexOfFirst { it.route == Route.Home }
        if (homeIndex >= 0) {
            // Only a tab root can sit above Home now; keep it, with its state, for the next visit,
            // filed under the route the bar will ask for it by.
            while (stack.lastIndex > homeIndex) {
                val left = stack.removeAt(stack.lastIndex)
                left.tabRoute?.let { savedTabs[it] = left }
            }
        }

        if (isTab(top, route)) {
            top.tabRoute = route
            return
        }

        stack.add(savedTabs.remove(route)?.also { it.tabRoute = route } ?: ids.entry(route, tabRoute = route))
    }

    /** Whether [entry] is [route]'s tab: the screen itself, or the tab root the bar opened as [route]. */
    private fun isTab(
        entry: NavStackEntry,
        route: Route,
    ) = entry.route == route || entry.tabRoute == route

    /**
     * Drops every screen the user pushed on top of the tab root they are in (or on top of Home,
     * which is a tab root the user may never have tapped because the app starts there).
     */
    private fun popPushesAboveTabRoot() {
        while (stack.size > 1) {
            val top = top
            if (top.tabRoot || top.route == Route.Home) return
            stack.removeAt(stack.lastIndex)
        }
    }

    /** Removes [index] and everything above it; every caller then adds a screen, so the stack never stays empty. */
    private fun removeFrom(index: Int) {
        while (stack.lastIndex >= index) stack.removeAt(stack.lastIndex)
    }
}
