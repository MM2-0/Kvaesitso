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
package com.vitorpamplona.amethyst.commons.ui.navigation.bottombars

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import com.vitorpamplona.amethyst.commons.model.navigation.Route

/**
 * Bridges the shell-level [AppNavigationRail] to the per-screen re-tap behaviors that live in
 * each screen's [AppBottomBar] onClick lambda (scroll-to-top, feed refresh, ...).
 *
 * On phones the bottom bar invokes the screen's lambda directly. On large screens the bar
 * renders nothing, but it still registers the screen's lambda here; when the user taps the
 * rail item that is already selected, the rail routes the tap back through [reselect] so the
 * exact same per-screen logic runs.
 */
@Stable
class TabReselectCoordinator {
    private var currentRoute: (() -> Route?)? = null
    private var currentHandler: ((Route) -> Unit)? = null

    fun register(
        route: () -> Route?,
        handler: (Route) -> Unit,
    ) {
        currentRoute = route
        currentHandler = handler
    }

    /** Unregisters only if [handler] is still the active one, so a newly composed screen's
     * registration is not torn down by the outgoing screen's dispose during a transition. */
    fun unregister(handler: (Route) -> Unit) {
        if (currentHandler === handler) {
            currentHandler = null
            currentRoute = null
        }
    }

    /** Invokes the active tab root's handler if it owns [route]. Returns true if handled. */
    fun reselect(route: Route): Boolean {
        val handler = currentHandler ?: return false
        if (currentRoute?.invoke() != route) return false
        handler(route)
        return true
    }
}

val LocalTabReselectCoordinator = staticCompositionLocalOf { TabReselectCoordinator() }
