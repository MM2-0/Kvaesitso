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

import com.vitorpamplona.amethyst.commons.model.NavigationLayoutType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavBackStacksLayoutsTest {
    private fun NavBackStacks.routes() = stack.map { it.route }

    private fun tabs() = NavBackStacks().apply { changeMode(NavigationLayoutType.TABS) }

    private fun windows() = NavBackStacks().apply { changeMode(NavigationLayoutType.WINDOWS) }

    // The scenarios read in the user's terms; these reach the model of the layout in use.
    private fun NavBackStacks.changeMode(layout: NavigationLayoutType) = changeArrangement(layout.arrangement())

    private val NavBackStacks.tabsModel get() = model as TabsModel

    private val NavBackStacks.single get() = model as SingleScreenModel

    private fun NavBackStacks.tabs() = tabsModel.strip()

    private fun NavBackStacks.open(
        route: Route,
        background: Boolean = false,
        forceNew: Boolean = false,
        openerId: Long? = null,
    ) = open(NavRequest(route, origin = openerId, background = background, forceNew = forceNew))

    private fun NavBackStacks.activate(id: Long) = single.activate(id)

    private fun NavBackStacks.openerOf(entry: NavStackEntry) = single.openerOf(entry)

    private val NavBackStacks.attention get() = single.attention

    private val NavBackStacks.windowFocus get() = model.windowFocus

    private val NavBackStacks.canReopen get() = single.canReopen

    private fun NavBackStacks.reopenClosed() = single.reopenClosed()

    private fun NavBackStacks.openedBy(id: Long) = single.openedBy(id)

    private fun NavBackStacks.closeOpenedBy(id: Long) = single.closeOpenedBy(id)

    private fun NavBackStacks.closeOthers(id: Long) = tabsModel.closeOthers(id)

    private fun NavBackStacks.closeToTheRight(id: Long) = tabsModel.closeToTheRight(id)

    private fun NavBackStacks.move(
        id: Long,
        delta: Int,
    ) = tabsModel.move(id, delta)

    private fun NavBackStacks.cycle(delta: Int) = tabsModel.cycle(delta)

    private fun NavBackStacks.setDetached(
        id: Long,
        out: Boolean,
    ) = tabsModel.setDetached(id, out)

    @Test
    fun eachScreenOpensAsATabNextToTheOneThatOpenedIt() {
        val stacks = tabs()
        stacks.push(Route.Hashtag("nostr"))
        stacks.switchTab(Route.Home)
        stacks.push(Route.Note("n"))

        assertEquals(listOf(Route.Home, Route.Note("n"), Route.Hashtag("nostr")), stacks.routes())
        assertEquals(Route.Note("n"), stacks.topRoute)
        assertEquals(Route.Home, stacks.openerOf(stacks.top)?.route)
    }

    @Test
    fun openingAnOpenScreenFocusesItInsteadOfDuplicating() {
        val stacks = tabs()
        stacks.push(Route.Note("n"))
        stacks.switchTab(Route.Home)
        stacks.push(Route.Note("n"))

        assertEquals(listOf(Route.Home, Route.Note("n")), stacks.routes())
        assertEquals(Route.Note("n"), stacks.topRoute)
    }

    @Test
    fun aFreshSearchOpensEvenWhenOneIsOpen() {
        val stacks = tabs()
        stacks.open(Route.Search(), forceNew = true)
        stacks.open(Route.Search(), forceNew = true)

        assertEquals(3, stacks.stack.size)
    }

    @Test
    fun goingBackClosesTheTabAndShowsItsOpener() {
        val stacks = tabs()
        stacks.push(Route.Hashtag("nostr"))
        stacks.push(Route.Note("n"))
        stacks.switchTab(Route.Home)
        stacks.activate(stacks.stack.first { it.route == Route.Note("n") }.id)

        assertTrue(stacks.pop())

        assertEquals(listOf(Route.Home, Route.Hashtag("nostr")), stacks.routes())
        assertEquals(Route.Hashtag("nostr"), stacks.topRoute)
    }

    @Test
    fun closingATabHandsItsChildrenToItsOpener() {
        val stacks = tabs()
        stacks.push(Route.Hashtag("nostr"))
        stacks.push(Route.Note("n"))
        stacks.close(stacks.stack.first { it.route == Route.Hashtag("nostr") }.id)

        assertEquals(Route.Home, stacks.openerOf(stacks.stack.first { it.route == Route.Note("n") })?.route)
    }

    @Test
    fun closingTheLastTabLeavesHome() {
        val stacks = tabs()
        assertTrue(stacks.pop())

        assertEquals(listOf<Route>(Route.Home), stacks.routes())
    }

    @Test
    fun theNextStepOfAFlowReplacesTheScreenInPlace() {
        val stacks = tabs()
        stacks.push(Route.Note("n"))
        val id = stacks.top.id
        stacks.popUpTo(Route.Note("m"), Route.Note::class)

        assertEquals(id, stacks.top.id)
        assertEquals(Route.Note("m"), stacks.topRoute)
        assertEquals(2, stacks.stack.size)
    }

    @Test
    fun aNarrowTabsWindowShowsTheChainOfOpeners() {
        val stacks = tabs()
        stacks.push(Route.Hashtag("nostr"))
        stacks.push(Route.Note("n"))

        assertEquals(listOf(Route.Note("n")), stacks.shown(wide = true).map { it.route })
        assertEquals(listOf(Route.Home, Route.Hashtag("nostr"), Route.Note("n")), stacks.shown(wide = false).map { it.route })
    }

    @Test
    fun switchingBetweenStackAndTabsKeepsTheScreens() {
        val stacks = NavBackStacks()
        stacks.push(Route.Hashtag("nostr"))
        stacks.push(Route.Note("n"))
        stacks.changeMode(NavigationLayoutType.TABS)

        assertEquals(Route.Note("n"), stacks.topRoute)
        assertEquals(Route.Hashtag("nostr"), stacks.openerOf(stacks.top)?.route)

        stacks.switchTab(Route.Home)
        stacks.push(Route.Profile("a"))
        stacks.changeMode(NavigationLayoutType.STACK)

        assertEquals(listOf(Route.Home, Route.Profile("a")), stacks.routes())
    }

    @Test
    fun inWindowsSectionsStayInTheMainWindowAndScreensGetWindows() {
        val stacks = windows()
        stacks.switchTab(Route.Message)
        stacks.push(Route.Note("n"))
        stacks.push(Route.Profile("a"))

        assertEquals(Route.Message, stacks.topRoute)
        assertEquals(listOf(Route.Note("n"), Route.Profile("a")), stacks.windows().map { it.route })
        assertEquals(stacks.windows().last().id, stacks.windowFocus?.id)
    }

    @Test
    fun aScreenOpenInAWindowIsRaisedNotOpenedAgain() {
        val stacks = windows()
        stacks.push(Route.Note("n"))
        val first = assertNotNull(stacks.windowFocus)
        stacks.push(Route.Profile("a"))
        stacks.push(Route.Note("n"))

        assertEquals(2, stacks.windows().size)
        assertEquals(first.id, stacks.windowFocus?.id)
        assertTrue(stacks.windowFocus!!.serial > first.serial)
    }

    @Test
    fun theMainWindowNeverPopsAndClosingAWindowLeavesIt() {
        val stacks = windows()
        stacks.push(Route.Note("n"))

        assertFalse(stacks.pop())
        stacks.close(stacks.windows().single().id)

        assertEquals(listOf<Route>(Route.Home), stacks.routes())
        assertEquals(Route.Home, stacks.topRoute)
    }

    @Test
    fun theLayoutSurvivesSavingAndRestoring() {
        val stacks = tabs()
        stacks.push(Route.Hashtag("nostr"))
        stacks.push(Route.Note("n"))

        val restored = assertNotNull(NavBackStacks.decode(stacks.encode()))

        assertEquals(NavArrangement.TABS, restored.arrangement)
        assertEquals(Route.Note("n"), restored.topRoute)
        assertEquals(Route.Hashtag("nostr"), restored.openerOf(restored.top)?.route)
        assertNull(restored.windowFocus)
    }

    @Test
    fun sectionsArePinnedAtTheStartOfTheStrip() {
        val stacks = tabs()
        stacks.push(Route.Note("n"))
        stacks.switchTab(Route.Message)

        assertEquals(listOf(Route.Home, Route.Message, Route.Note("n")), stacks.tabs().map { it.route })
        assertTrue(stacks.tabs().take(2).all { stacks.tabsModel.isPinned(it) })
    }

    @Test
    fun aClosedTabComesBack() {
        val stacks = tabs()
        stacks.push(Route.Note("n"))
        stacks.pop()

        assertTrue(stacks.canReopen)
        assertTrue(stacks.reopenClosed())
        assertEquals(Route.Note("n"), stacks.topRoute)
        assertFalse(stacks.canReopen)
    }

    @Test
    fun theBulkClosesLeaveThePinnedTabs() {
        val stacks = tabs()
        stacks.push(Route.Note("a"))
        stacks.push(Route.Note("b"))
        stacks.push(Route.Note("c"))
        val b = stacks.tabs().first { it.route == Route.Note("b") }.id

        stacks.closeToTheRight(b)
        assertEquals(listOf(Route.Home, Route.Note("a"), Route.Note("b")), stacks.routes())

        stacks.closeOthers(b)
        assertEquals(listOf(Route.Home, Route.Note("b")), stacks.routes())
        assertEquals(Route.Note("b"), stacks.topRoute)
    }

    @Test
    fun closingWhatATabOpenedKeepsTheTab() {
        val stacks = tabs()
        stacks.push(Route.Hashtag("nostr"))
        val hashtag = stacks.top.id
        stacks.push(Route.Note("n"))
        stacks.push(Route.Profile("p"))
        stacks.activate(hashtag)

        assertEquals(2, stacks.openedBy(hashtag))
        stacks.closeOpenedBy(hashtag)
        assertEquals(listOf(Route.Home, Route.Hashtag("nostr")), stacks.routes())
    }

    @Test
    fun aBackgroundTabOpensWithoutTakingFocus() {
        val stacks = tabs()
        stacks.open(Route.Note("n"), background = true)

        assertEquals(Route.Home, stacks.topRoute)
        assertEquals(stacks.tabs().last().id, stacks.attention?.id)
    }

    @Test
    fun tabsMoveAndCycleWithinTheirGroup() {
        val stacks = tabs()
        stacks.push(Route.Note("a"))
        stacks.switchTab(Route.Home)
        stacks.push(Route.Note("b"))
        val a = stacks.tabs().first { it.route == Route.Note("a") }.id

        stacks.move(a, -5)
        assertEquals(listOf(Route.Home, Route.Note("a"), Route.Note("b")), stacks.routes())

        stacks.cycle(1)
        assertEquals(Route.Home, stacks.topRoute)
        stacks.cycle(-1)
        assertEquals(Route.Note("b"), stacks.topRoute)
    }

    @Test
    fun aTabMovesOutToAWindowAndBack() {
        val stacks = tabs()
        stacks.push(Route.Note("n"))
        val note = stacks.top.id
        stacks.setDetached(note, true)

        assertEquals(listOf(Route.Note("n")), stacks.windows().map { it.route })
        assertEquals(listOf<Route>(Route.Home), stacks.tabs().map { it.route })
        assertEquals(Route.Home, stacks.topRoute)
        assertEquals(note, stacks.windowFocus?.id)

        stacks.setDetached(note, false)
        assertEquals(Route.Note("n"), stacks.topRoute)
        assertTrue(stacks.windows().isEmpty())
    }

    @Test
    fun aWindowRemembersWhichWindowOpenedIt() {
        val stacks = windows()
        stacks.push(Route.Note("n"))
        val note = stacks.windows().single()
        stacks.open(Route.Profile("p"), openerId = note.id)

        assertEquals(note.id, stacks.openerOf(stacks.windows().last())?.id)
    }

    @Test
    fun aSectionOrDrawerScreenHasNoOpener() {
        val stacks = tabs()
        stacks.push(Route.Note("n"))
        stacks.switchTab(Route.Message)
        stacks.push(Route.Bookmarks, drawerRoot = true)

        assertNull(stacks.openerOf(stacks.stack.first { it.route == Route.Message }))
        assertNull(stacks.openerOf(stacks.stack.first { it.route == Route.Bookmarks }))
    }

    @Test
    fun theDeckKeepsAPlainStackInTheMainScreen() {
        val stacks = NavBackStacks()
        stacks.push(Route.Note("n"))
        stacks.changeMode(NavigationLayoutType.DECK)

        assertEquals(NavArrangement.STACK, stacks.arrangement)
        assertFalse(stacks.isSingleScreen)
        stacks.push(Route.Profile("p"))
        assertEquals(listOf(Route.Home, Route.Note("n"), Route.Profile("p")), stacks.routes())
    }

    @Test
    fun aReplaceFromAWindowActsOnThatWindow() {
        val stacks = windows()
        stacks.push(Route.Note("a"))
        stacks.push(Route.Note("b"))
        val a = stacks.windows().first { it.route == Route.Note("a") }

        stacks.open(NavRequest(Route.Note("a2"), NavRequest.Kind.Replace(Route.Note::class), origin = a.id))

        assertEquals(listOf(Route.Note("a2"), Route.Note("b")), stacks.windows().map { it.route })
        assertEquals(a.id, stacks.windows().first().id)
    }

    @Test
    fun aRestartInTabsShowsTheOpenTabOrOpensOne() {
        val stacks = tabs()
        stacks.push(Route.Note("n"))
        stacks.switchTab(Route.Home)
        stacks.newStack(Route.Note("n"))

        assertEquals(Route.Note("n"), stacks.topRoute)
        assertEquals(2, stacks.tabs().size)
    }

    @Test
    fun aMovedOutTabHasNoBackArrowAndNoBar() {
        val stacks = tabs()
        stacks.push(Route.Note("n"))
        val note = stacks.top
        stacks.setDetached(note.id, true)

        // Even where the main window is too narrow for the strip, a window holds one screen.
        assertFalse(stacks.model.canGoBack(note, wide = false))
        assertFalse(stacks.model.showsBar(note, wide = false))
        assertTrue(stacks.model.titleShownOutside(note, wide = false))
    }

    @Test
    fun theStripOrTheTitleBarNamesTheScreenOnlyWhereThereIsOne() {
        val tabs = tabs()
        tabs.push(Route.Note("n"))
        assertTrue(tabs.model.titleShownOutside(tabs.top, wide = true))
        // Too narrow for the strip: the screen's own top bar names it, with a back arrow to its opener.
        assertFalse(tabs.model.titleShownOutside(tabs.top, wide = false))
        assertTrue(tabs.model.canGoBack(tabs.top, wide = false))

        val windows = windows()
        windows.push(Route.Note("n"))
        assertTrue(windows.model.titleShownOutside(windows.windows().single(), wide = true))

        val stack = NavBackStacks()
        stack.push(Route.Note("n"))
        assertFalse(stack.model.titleShownOutside(stack.top, wide = true))
    }

    @Test
    fun aDeckColumnsFirstScreenHasNoBackArrow() {
        val column = NavBackStacks(Route.Notification())
        assertFalse(column.model.canGoBack(column.top, wide = true))
        column.push(Route.Note("n"))
        assertTrue(column.model.canGoBack(column.top, wide = true))
    }

    @Test
    fun aTabOpenedFromAMovedOutWindowRaisesTheMainWindow() {
        val stacks = tabs()
        stacks.push(Route.Note("n"))
        val note = stacks.top.id
        stacks.setDetached(note, true)

        stacks.open(NavRequest(Route.Profile("p"), origin = note))

        val profile = stacks.tabs().single { it.route == Route.Profile("p") }
        assertEquals(profile, stacks.top)
        assertEquals(profile.id, stacks.windowFocus?.id)
    }

    @Test
    fun aTabOpenedFromTheMainWindowDoesNotRaiseIt() {
        val stacks = tabs()
        val before = stacks.windowFocus
        stacks.open(NavRequest(Route.Profile("p"), origin = stacks.top.id))

        assertEquals(before, stacks.windowFocus)
    }

    @Test
    fun sectionsAndDrawerScreensFromAStackHaveNoOpener() {
        val stacks = NavBackStacks()
        stacks.switchTab(Route.Message)
        stacks.push(Route.Note("n"))
        stacks.changeArrangement(NavArrangement.TABS)

        val message = stacks.tabs().single { it.route == Route.Message }
        val note = stacks.tabs().single { it.route == Route.Note("n") }
        assertNull(stacks.openerOf(message))
        assertEquals(message, stacks.openerOf(note))
        // A narrow window: the pinned section has nothing to go back to.
        assertFalse(stacks.model.canGoBack(message, wide = false))
    }

    @Test
    fun aScreenNoLongerOnTopDoesNotPopAnother() {
        val stacks = NavBackStacks()
        stacks.push(Route.Note("a"))
        val a = stacks.top
        stacks.push(Route.Note("b"))

        assertFalse(stacks.back(a))
        assertEquals(Route.Note("b"), stacks.topRoute)
        assertTrue(stacks.back(stacks.top))
        assertEquals(Route.Note("a"), stacks.topRoute)
    }

    @Test
    fun aSavedStateEncodesLikeTheLiveModel() {
        val stacks = tabs()
        stacks.push(Route.Note("n"))
        val state = stacks.savedState()
        stacks.push(Route.Profile("p"))

        val restored = NavBackStacks.decode(NavBackStacks.encode(state))
        assertNotNull(restored)
        assertEquals(listOf<Route>(Route.Home, Route.Note("n")), restored.stack.map { it.route })
    }
}
