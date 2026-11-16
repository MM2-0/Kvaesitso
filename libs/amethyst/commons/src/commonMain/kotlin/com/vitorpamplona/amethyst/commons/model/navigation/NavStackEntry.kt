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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One screen. Two entries for the same [route] are still two screens (a profile opened twice), so
 * identity is the [id], never the route; [contentKey] is what Navigation 3 keys the screen's saved
 * UI state and ViewModels by.
 *
 * Everything else is state because it can change on an entry already on screen: a restart hands
 * the screen on top new arguments in place, and tapping the tab the user is standing on marks it a
 * tab root. What a screen means to one layout only (a pinned tab, a tab moved to a window) lives in
 * that layout's model, not here.
 */
@Stable
class NavStackEntry(
    route: Route,
    val id: Long,
    tabRoute: Route? = null,
    drawerRoot: Boolean = false,
    routeVersion: Int = 0,
) {
    /** What this screen shows. Replaced in place when the same destination is reopened with other arguments. */
    var route by mutableStateOf(route)
        private set

    /**
     * How many times [route] was replaced in place. The screen is built anew for each version, with
     * ViewModels and saved UI state of its own, so the previous arguments' ViewModels are cleared
     * instead of living on in the entry until it is popped (which, for a tab root, is never).
     */
    var routeVersion by mutableStateOf(routeVersion)
        private set

    /** Hands this screen other arguments, as a new version of it. */
    internal fun reArgue(newRoute: Route) {
        if (newRoute == route) return
        route = newRoute
        routeVersion++
    }

    /**
     * The bottom-bar route this entry is the root of, or null when it is not a tab root. Kept apart
     * from [route] because the two can diverge — a deep link reopens the Notifications tab as
     * `Notification(eventId)` — while the tab must still be found again under the route the bar asks for.
     */
    var tabRoute by mutableStateOf(tabRoute)

    /** Reached from the bottom bar or rail: no back arrow, and tab switches fade instead of slide. */
    val tabRoot: Boolean get() = tabRoute != null

    /** Opened from the navigation drawer: it can pop, but keeps the bottom bar like a tab root. */
    var drawerRoot by mutableStateOf(drawerRoot)

    val contentKey: String get() = "nav-$id"

    override fun equals(other: Any?): Boolean = other is NavStackEntry && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "NavStackEntry($id, $route, v$routeVersion, tabRoute=$tabRoute, drawerRoot=$drawerRoot)"
}

/** Hands out entry ids, unique across every layout a window switches through, so state keyed by them never mixes. */
class EntryIds(
    next: Long,
) {
    var next: Long = next
        private set

    fun entry(
        route: Route,
        tabRoute: Route? = null,
        drawerRoot: Boolean = false,
    ) = NavStackEntry(route, next++, tabRoute, drawerRoot)
}
