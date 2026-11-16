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

import com.vitorpamplona.amethyst.commons.model.navigation.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

class ObservableNav(
    private val sourceNav: INav,
    override val navigationScope: CoroutineScope,
    val onBeforeNavigate: () -> Unit,
) : INav by sourceNav {
    override fun nav(route: Route) {
        navigationScope.launch {
            onBeforeNavigate()
        }
        sourceNav.nav(route)
    }

    override fun nav(computeRoute: suspend () -> Route?) {
        navigationScope.launch {
            onBeforeNavigate()
        }
        sourceNav.nav(computeRoute)
    }

    override fun newStack(route: Route) {
        navigationScope.launch {
            onBeforeNavigate()
        }
        sourceNav.newStack(route)
    }

    override fun navBottomBar(route: Route) {
        navigationScope.launch {
            onBeforeNavigate()
        }
        sourceNav.navBottomBar(route)
    }

    override fun popBack() {
        navigationScope.launch {
            onBeforeNavigate()
        }
        sourceNav.popBack()
    }

    override fun <T : Route> popUpTo(
        route: Route,
        klass: KClass<T>,
    ) {
        navigationScope.launch {
            onBeforeNavigate()
        }
        sourceNav.popUpTo(route, klass)
    }
}
