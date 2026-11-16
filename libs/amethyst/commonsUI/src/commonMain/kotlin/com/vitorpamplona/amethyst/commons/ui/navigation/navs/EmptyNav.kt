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
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

@Stable
class EmptyNav : INav {
    override val navigationScope: CoroutineScope get() = TODO("Not yet implemented")
    override val drawerState = DrawerState(DrawerValue.Closed)

    override fun closeDrawer() {
        // no-op: EmptyNav is a preview/test stub; animating the drawer needs a frame clock it has not got.
    }

    override fun openDrawer() {
        // no-op: EmptyNav is a preview/test stub; animating the drawer needs a frame clock it has not got.
    }

    // All navigation methods are intentionally no-op; this is a stub for previews and tests
    override fun nav(route: Route) {
        // no-op: EmptyNav is a preview/test stub with no navigation host.
    }

    override fun nav(computeRoute: suspend () -> Route?) {
        // no-op: EmptyNav is a preview/test stub with no navigation host.
    }

    override fun newStack(route: Route) {
        // no-op: EmptyNav is a preview/test stub with no back stack to reset.
    }

    override fun navBottomBar(route: Route) {
        // no-op: EmptyNav is a preview/test stub with no bottom-bar host.
    }

    @Composable
    override fun canPop(): Boolean = false

    override fun popBack() {
        // no-op: EmptyNav is a preview/test stub with no back stack to pop.
    }

    override fun <T : Route> popUpTo(
        route: Route,
        klass: KClass<T>,
    ) {
        // no-op: EmptyNav is a preview/test stub with no back stack.
    }
}
