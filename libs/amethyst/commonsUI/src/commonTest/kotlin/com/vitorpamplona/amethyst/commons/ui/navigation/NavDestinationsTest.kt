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
package com.vitorpamplona.amethyst.commons.ui.navigation

import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavFamily
import com.vitorpamplona.amethyst.commons.ui.navigation.host.composable
import com.vitorpamplona.amethyst.commons.ui.navigation.host.composableFromBottom
import com.vitorpamplona.amethyst.commons.ui.navigation.host.composableFromEndArgs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavDestinationsTest {
    private val destinations =
        NavDestinations().apply {
            composable<Route.Home> { }
            composableFromEndArgs<Route.Profile> { }
            composableFromBottom<Route.Message> { }
        }

    @Test
    fun screenTimeNamesAreTheRoutesSerialNames() {
        // Resolved lazily (and reflectively), so check it still lands on the serial name for both a
        // data-class route and an object route rather than an obfuscatable class name.
        assertEquals("com.vitorpamplona.amethyst.commons.model.navigation.Route.Profile", destinations.serialNameOf(Route.Profile("p")))
        assertEquals("com.vitorpamplona.amethyst.commons.model.navigation.Route.Home", destinations.serialNameOf(Route.Home))
        assertNull(destinations.serialNameOf(Route.Search()))
    }

    @Test
    fun theBuilderPicksTheMotion() {
        assertEquals(NavFamily.NONE, destinations.of(Route.Home).family)
        assertEquals(NavFamily.END, destinations.of(Route.Profile("p")).family)
        assertEquals(NavFamily.BOTTOM, destinations.of(Route.Message).family)
    }

    @Test
    fun aRouteWithNoScreenIsAnErrorUnlessTheFrontEndHasAnUnavailableScreen() {
        // Android registers every screen, so an unknown route stays a programming error there.
        assertFailsWith<IllegalStateException> { destinations.of(Route.MyFitness) }
        assertFalse(destinations.has(Route.MyFitness))
        assertTrue(destinations.has(Route.Home))

        // The desktop carries only part of the app: a route it lacks shows its notice instead.
        val desktop =
            NavDestinations().apply {
                composable<Route.Home> { }
                unavailable { }
            }
        assertEquals(NavFamily.END, desktop.of(Route.MyFitness).family)
        assertFalse(desktop.has(Route.MyFitness), "the notice is not a screen of its own: entry points still hide")
        assertEquals(NavFamily.NONE, desktop.of(Route.Home).family, "registered screens keep their own")
    }
}
