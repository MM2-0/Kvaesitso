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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

@Stable
interface INav {
    val navigationScope: CoroutineScope
    val drawerState: DrawerState

    /**
     * True while the shell renders the drawer permanently docked (Expanded windows). In that
     * mode [drawerState] never transitions — it stays [androidx.compose.material3.DrawerValue.Closed]
     * while the drawer is visibly on screen — so drawer consumers (edge swipes, open buttons,
     * close-driven effects) should consult this instead of inferring from [drawerState].
     */
    val isDrawerDocked: Boolean get() = false

    fun closeDrawer()

    fun openDrawer()

    fun nav(route: Route)

    fun nav(computeRoute: suspend () -> Route?)

    fun newStack(route: Route)

    fun navBottomBar(route: Route)

    /**
     * Opens [route] as a top-level destination picked from the navigation drawer. It still
     * stacks on top of the current screen (so it [canPop]), but unlike an in-app push it keeps
     * the bottom bar — see [showsBottomBar].
     */
    fun navDrawer(route: Route) = nav(route)

    /** [navDrawer], for a route that has to be resolved first. */
    fun navDrawer(computeRoute: suspend () -> Route?) = nav(computeRoute)

    @Composable
    fun canPop(): Boolean

    /**
     * Whether this screen renders the bottom bar: tab roots, the start destination and
     * destinations opened from the drawer ([navDrawer]) do; in-app pushes don't.
     */
    @Composable
    fun showsBottomBar(): Boolean = !canPop()

    /**
     * Whether something outside the screen already names it (a tab in the strip, a window's title
     * bar), so a top bar holding only that name and no back arrow can be left out.
     */
    @Composable
    fun titleShownOutside(): Boolean = false

    fun popBack()

    fun <T : Route> popUpTo(
        route: Route,
        klass: KClass<T>,
    )
}
