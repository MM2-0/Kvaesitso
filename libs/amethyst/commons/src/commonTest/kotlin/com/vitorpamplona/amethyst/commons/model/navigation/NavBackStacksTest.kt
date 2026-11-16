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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NavBackStacksTest {
    private fun NavBackStacks.routes() = stack.map { it.route }

    @Test
    fun pushingTheScreenAlreadyOnTopIsANoOp() {
        val stacks = NavBackStacks()
        assertTrue(stacks.push(Route.Note("n")))
        assertFalse(stacks.push(Route.Note("n")))
        assertTrue(stacks.push(Route.Note("other")))

        assertEquals(listOf(Route.Home, Route.Note("n"), Route.Note("other")), stacks.routes())
    }

    @Test
    fun theLastScreenIsNeverPopped() {
        val stacks = NavBackStacks()
        assertFalse(stacks.pop())
        assertEquals(listOf<Route>(Route.Home), stacks.routes())
    }

    @Test
    fun aRouteOpenedTwiceIsTwoScreens() {
        val stacks = NavBackStacks()
        stacks.push(Route.Profile("a"))
        stacks.push(Route.Note("n"))
        stacks.push(Route.Profile("a"))

        assertNotEquals(stacks.stack[1].contentKey, stacks.stack[3].contentKey)
    }

    @Test
    fun newStackDropsTheEarlierCopyAndEverythingAboveIt() {
        val stacks = NavBackStacks()
        stacks.push(Route.Profile("a"))
        stacks.push(Route.Note("n"))
        stacks.newStack(Route.Profile("a"))

        assertEquals(listOf(Route.Home, Route.Profile("a")), stacks.routes())
    }

    @Test
    fun newStackHandsTheSameDestinationOnTopNewArguments() {
        val stacks = NavBackStacks()
        stacks.switchTab(Route.Pictures())
        val feed = stacks.top
        stacks.newStack(Route.Pictures(attachments = listOf("content://shared")))

        assertEquals(listOf(Route.Home, Route.Pictures(attachments = listOf("content://shared"))), stacks.routes())
        // The same entry, as Navigation 2's single-top launch kept it: it stays a tab root and
        // nothing animates. Its content is a new version, so the previous arguments' ViewModels go.
        assertSame(feed, stacks.top)
        assertTrue(stacks.top.tabRoot, "a re-argued tab root stays a tab root")
        assertEquals(1, stacks.top.routeVersion)
    }

    @Test
    fun everyReArgumentIsANewVersion() {
        val stacks = NavBackStacks()
        stacks.push(Route.Note("a"))
        stacks.newStack(Route.Note("b"))
        stacks.newStack(Route.Note("c"))

        assertEquals(listOf(Route.Home, Route.Note("c")), stacks.routes())
        assertEquals(2, stacks.top.routeVersion)
    }

    @Test
    fun aTabReopenedWithArgumentsIsStillFoundByTheBar() {
        // A notification deep link while on the Notifications tab re-argues the tab root. Leaving and
        // returning through the bar, which asks for the plain route, must still land on that entry.
        val stacks = NavBackStacks()
        stacks.switchTab(Route.Notification())
        val notifications = stacks.top
        stacks.newStack(Route.Notification(scrollToEventId = "e1"))

        stacks.switchTab(Route.Home)
        assertEquals(setOf<Route>(Route.Notification()), stacks.savedTabs.keys.toSet())

        stacks.switchTab(Route.Notification())
        assertSame(notifications, stacks.top)
        assertTrue(stacks.savedTabs.isEmpty())
    }

    @Test
    fun reTappingAReArguedTabStaysOnIt() {
        val stacks = NavBackStacks()
        stacks.switchTab(Route.Notification())
        stacks.newStack(Route.Notification(scrollToEventId = "e1"))
        val notifications = stacks.top
        stacks.push(Route.Note("n"))

        stacks.switchTab(Route.Notification())

        assertEquals(listOf(Route.Home, Route.Notification(scrollToEventId = "e1")), stacks.routes())
        assertSame(notifications, stacks.top)
    }

    @Test
    fun popUpToDropsTheLatestOfAKindAndPushes() {
        val stacks = NavBackStacks()
        stacks.push(Route.Pictures(attachments = listOf("1")))
        stacks.push(Route.Note("n"))
        stacks.popUpTo(Route.Pictures(attachments = listOf("2")), Route.Pictures::class)

        assertEquals(listOf(Route.Home, Route.Pictures(attachments = listOf("2"))), stacks.routes())
    }

    @Test
    fun roundTripsThroughItsSavedForm() {
        val stacks = NavBackStacks()
        stacks.switchTab(Route.Message)
        stacks.switchTab(Route.Search("nostr"))
        stacks.push(Route.Note("n"), drawerRoot = true)
        stacks.newStack(Route.Note("m"))

        val restored = NavBackStacks.decode(stacks.encode())!!

        assertEquals(stacks.routes(), restored.routes())
        assertEquals(stacks.stack.map { it.contentKey }, restored.stack.map { it.contentKey })
        assertEquals(stacks.stack.map { it.tabRoute to it.drawerRoot }, restored.stack.map { it.tabRoute to it.drawerRoot })
        // A re-argued screen comes back as the same version, so its saved UI state is found again.
        assertEquals(listOf(0, 0, 1), restored.stack.map { it.routeVersion })
        assertEquals(stacks.savedTabs.keys.toSet(), restored.savedTabs.keys.toSet())

        // Ids keep counting from where they left off, so a new screen never reuses a saved key.
        restored.push(Route.Profile("p"))
        assertEquals(1, restored.retained().count { it.contentKey == restored.top.contentKey })
    }

    @Test
    fun anUnreadableSavedFormIsDropped() {
        assertNull(NavBackStacks.decode("not json"))
        assertNull(NavBackStacks.decode("""{"stack":[],"savedTabs":[],"nextId":3}"""))
    }

    // A tab left after a share re-argued it (newStack hands the same destination new arguments in
    // place) is filed under the route the bar asks for. Restoring after process death must file it
    // the same way, or the next tap misses it and opens a fresh, stateless copy.
    @Test
    fun aReArguedTabLeftBehindSurvivesProcessDeath() {
        val stacks = NavBackStacks()
        stacks.switchTab(Route.Pictures())
        val feed = stacks.top
        stacks.newStack(Route.Pictures(attachments = listOf("content://shared")))
        stacks.switchTab(Route.Message)

        val restored = assertNotNull(NavBackStacks.decode(stacks.encode()))
        restored.switchTab(Route.Pictures())
        assertEquals(feed.id, restored.top.id, "the tab came back as a new screen")
    }
}
