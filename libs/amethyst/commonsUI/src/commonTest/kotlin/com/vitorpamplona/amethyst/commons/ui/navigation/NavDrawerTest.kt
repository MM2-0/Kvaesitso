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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A screen opened from the navigation drawer is a top-level section: it keeps the bottom bar
 * (issue #4141, where Pictures and every other drawer destination lost it). It still sits on top of
 * the screen the drawer was opened over, so it keeps its back arrow too; the bar is told apart
 * from an in-app push by the drawer-root mark these tests pin down.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavDrawerTest {
    @Test
    fun marksTheEntryItOpens() =
        runTest {
            val stacks = NavBackStacks()
            Nav(stacks, this).navDrawer(Route.Pictures())
            advanceUntilIdle()

            assertEquals(Route.Pictures(), stacks.topRoute)
            assertTrue(stacks.top.drawerRoot)
            assertFalse(stacks.top.tabRoot)
        }

    @Test
    fun marksTheEntryOfAResolvedRoute() =
        runTest {
            val stacks = NavBackStacks()
            Nav(stacks, this).navDrawer { Route.Articles }
            advanceUntilIdle()

            assertEquals(Route.Articles, stacks.topRoute)
            assertTrue(stacks.top.drawerRoot)
        }

    @Test
    fun plainPushesAreNotMarked() =
        runTest {
            val stacks = NavBackStacks()
            Nav(stacks, this).nav(Route.Pictures())
            advanceUntilIdle()

            assertEquals(Route.Pictures(), stacks.topRoute)
            assertFalse(stacks.top.drawerRoot)
        }

    @Test
    fun aTabTappedFromADrawerScreenDropsItFirst() =
        runTest {
            // Home > Pictures (from the drawer): the bar now shows on Pictures, so it can be tapped
            // from there. The drawer entry is not a tab root and must not be saved into the tab.
            val stacks = NavBackStacks()
            val nav = Nav(stacks, this)
            nav.navDrawer(Route.Pictures())
            nav.navBottomBar(Route.Message)
            advanceUntilIdle()

            assertEquals(listOf(Route.Home, Route.Message), stacks.stack.map { it.route })
            assertTrue(stacks.savedTabs.isEmpty())
        }
}
