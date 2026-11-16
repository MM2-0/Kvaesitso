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

import com.vitorpamplona.amethyst.commons.model.navigation.NavBackStacks
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A nav-bar tap must land on the tab itself, never on whatever the user had pushed on top of one.
 *
 * The phone bottom bar shows only on tab roots and drawer destinations, so it is tapped from at most
 * one screen above a tab. The large-screen navigation rail stays on screen the whole time, which is
 * where the gap showed: tapping Home from a thread three screens deep came back to that thread
 * instead of the feed.
 *
 * So pushes above a tab root are dropped, unsaved, on the way out, and only the tab root itself is
 * kept for the next visit — that is what keeps its ViewModels and scroll position.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavBottomBarStackTest {
    private fun routes(stacks: NavBackStacks) = stacks.stack.map { it.route }

    @Test
    fun dropsWhatTheUserPushedOnTopOfTheTabBeforeSwitchingTabs() =
        runTest {
            val stacks = NavBackStacks()
            val nav = Nav(stacks, this)

            // Home > Note > Profile: two pushes sitting on a tab root.
            nav.nav(Route.Note("n"))
            nav.nav(Route.Profile("p"))
            nav.navBottomBar(Route.Message)
            advanceUntilIdle()

            assertEquals(listOf(Route.Home, Route.Message), routes(stacks))
            assertTrue(stacks.top.tabRoot)
            assertTrue(stacks.savedTabs.isEmpty(), "nothing above a tab root is ever saved")
        }

    @Test
    fun homeNeverComesBackAsAnotherTabsStack() =
        runTest {
            val stacks = NavBackStacks()
            val nav = Nav(stacks, this)

            nav.navBottomBar(Route.Message)
            nav.nav(Route.Note("thread"))
            nav.navBottomBar(Route.Home)
            advanceUntilIdle()

            assertEquals(listOf(Route.Home), routes(stacks))
        }

    @Test
    fun returningToATabRestoresItsOwnEntry() =
        runTest {
            val stacks = NavBackStacks()
            val nav = Nav(stacks, this)

            nav.navBottomBar(Route.Message)
            advanceUntilIdle()
            val messages = stacks.top

            nav.nav(Route.Note("n"))
            nav.navBottomBar(Route.Home)
            advanceUntilIdle()
            assertSame(messages, stacks.savedTabs[Route.Message], "the left tab keeps its entry, and with it its state")

            nav.navBottomBar(Route.Message)
            advanceUntilIdle()

            assertEquals(listOf(Route.Home, Route.Message), routes(stacks))
            assertSame(messages, stacks.top)
            assertTrue(stacks.savedTabs.isEmpty())
        }

    @Test
    fun pinnedTabsOfOneKindEachKeepTheirOwnEntry() =
        runTest {
            // Every pinned web app is a WebApp route. Navigation 2 saved state per destination, so the
            // second app's tap restored the first one's URL; entries here are keyed by the full route.
            val stacks = NavBackStacks()
            val nav = Nav(stacks, this)
            val first = Route.WebApp("https://a.example")
            val second = Route.WebApp("https://b.example")

            nav.navBottomBar(first)
            nav.navBottomBar(second)
            advanceUntilIdle()
            assertEquals(listOf(Route.Home, second), routes(stacks))

            nav.navBottomBar(first)
            advanceUntilIdle()
            assertEquals(listOf(Route.Home, first), routes(stacks))
            assertEquals(setOf<Route>(second), stacks.savedTabs.keys.toSet())
        }

    @Test
    fun retappingTheCurrentTabDropsPushesAndStaysOnIt() =
        runTest {
            val stacks = NavBackStacks()
            val nav = Nav(stacks, this)

            nav.navBottomBar(Route.Message)
            advanceUntilIdle()
            val messages = stacks.top

            nav.nav(Route.Note("n"))
            nav.navBottomBar(Route.Message)
            advanceUntilIdle()

            assertEquals(listOf(Route.Home, Route.Message), routes(stacks))
            assertSame(messages, stacks.top)
        }
}
