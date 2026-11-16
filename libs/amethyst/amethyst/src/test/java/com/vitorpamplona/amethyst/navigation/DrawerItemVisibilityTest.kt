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
package com.vitorpamplona.amethyst.navigation

import com.vitorpamplona.amethyst.commons.model.navigation.DrawerItemVisibility
import com.vitorpamplona.amethyst.commons.model.navigation.DrawerSectionId
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.DrawerSectionVisibility
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.DrawerSections
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.DrawerSettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The show/hide rules behind the Side Menu settings screen, exercised without the UI.
 *
 * The preference stores what is *hidden*, so the interesting cases are the empty set (a stock drawer,
 * and the state every new install and every newly shipped destination starts in) and the mandatory
 * rows, which no code path may switch off.
 */
class DrawerItemVisibilityTest {
    private val you = DrawerSections.first { it.id == DrawerSectionId.YOU }
    private val system = DrawerSections.first { it.id == DrawerSectionId.SYSTEM }

    @Test
    fun nothingHiddenMeansEverythingVisible() {
        val visible = DrawerSectionVisibility.visibleItems(you, emptySet())

        assertEquals(you.items, visible)
        assertEquals(0, DrawerSectionVisibility.hiddenCount(you, emptySet()))
    }

    @Test
    fun toggleHidesThenShows() {
        val once = DrawerItemVisibility.toggle(emptySet(), NavBarItem.DRAFTS)
        assertEquals(setOf(NavBarItem.DRAFTS), once)
        assertFalse(DrawerItemVisibility.isVisible(once, NavBarItem.DRAFTS))

        val twice = DrawerItemVisibility.toggle(once, NavBarItem.DRAFTS)
        assertEquals(emptySet<NavBarItem>(), twice)
        assertTrue(DrawerItemVisibility.isVisible(twice, NavBarItem.DRAFTS))
    }

    @Test
    fun aHiddenRowDropsOutOfItsSectionKeepingTheOrderOfTheRest() {
        val hidden = setOf(NavBarItem.DRAFTS)
        val visible = DrawerSectionVisibility.visibleItems(you, hidden)

        assertEquals(you.items.filter { it != NavBarItem.DRAFTS }, visible)
        assertEquals(1, DrawerSectionVisibility.hiddenCount(you, hidden))
    }

    @Test
    fun settingsCannotBeHidden() {
        // The escape hatch: hiding Settings would leave no route back to the screen that hides rows.
        assertEquals(emptySet<NavBarItem>(), DrawerItemVisibility.toggle(emptySet(), NavBarItem.SETTINGS))
        assertTrue(DrawerItemVisibility.isVisible(setOf(NavBarItem.SETTINGS), NavBarItem.SETTINGS))
    }

    @Test
    fun sanitizeStripsMandatoryItemsSyncedFromElsewhere() {
        // Another client (or an older build) could put Settings in the set; reading it back must not
        // strand the row as hidden-yet-unhideable.
        val sanitized = DrawerItemVisibility.sanitize(setOf(NavBarItem.SETTINGS, NavBarItem.DRAFTS))

        assertEquals(setOf(NavBarItem.DRAFTS), sanitized)
    }

    @Test
    fun sanitizeKeepsIdsThisDeviceDoesNotRender() {
        // Favorite Apps is gated off below API 30. Editing the menu on such a device must not clear
        // the choice the same account made on a newer one.
        val sanitized = DrawerItemVisibility.sanitize(setOf(NavBarItem.FAVORITE_APPS))

        assertEquals(setOf(NavBarItem.FAVORITE_APPS), sanitized)
    }

    @Test
    fun hideAllLeavesTheMandatoryRowsOfASection() {
        val hidden = DrawerSectionVisibility.hideAll(emptySet(), system)

        assertTrue(DrawerItemVisibility.isVisible(hidden, NavBarItem.SETTINGS))
        assertEquals(0, DrawerSectionVisibility.hiddenCount(system, hidden))
    }

    @Test
    fun aSectionOfOnlyMandatoryRowsHasNothingToHide() {
        // What gates the section's bulk Show all / Hide all actions.
        assertFalse(DrawerSectionVisibility.hasHideableRows(system))
        assertTrue(DrawerSectionVisibility.hasHideableRows(you))
    }

    @Test
    fun hideAllThenShowAllRoundTripsASection() {
        val hidden = DrawerSectionVisibility.hideAll(emptySet(), you)
        assertEquals(you.items.size, DrawerSectionVisibility.hiddenCount(you, hidden))

        val shown = DrawerSectionVisibility.showAll(hidden, you)
        assertEquals(emptySet<NavBarItem>(), shown)
    }

    @Test
    fun showAllOnlyTouchesItsOwnSection() {
        val hidden = DrawerSectionVisibility.hideAll(DrawerSectionVisibility.hideAll(emptySet(), you), system)

        val shown = DrawerSectionVisibility.showAll(hidden, system)

        assertEquals(you.items.size, DrawerSectionVisibility.hiddenCount(you, shown))
    }

    @Test
    fun stateHolderPersistsEveryEditAndRestoresDefaults() {
        val saved = mutableListOf<Set<NavBarItem>>()
        val state = DrawerSettingsState(emptySet()) { saved.add(it) }

        state.toggle(NavBarItem.DRAFTS)
        state.toggle(NavBarItem.BOOKMARKS)

        assertEquals(listOf(setOf(NavBarItem.DRAFTS), setOf(NavBarItem.DRAFTS, NavBarItem.BOOKMARKS)), saved)
        assertEquals(2, state.totalHidden())

        state.restoreDefault()

        assertEquals(emptySet<NavBarItem>(), state.hidden)
        assertEquals(0, state.totalHidden())
        assertEquals(emptySet<NavBarItem>(), saved.last())
    }

    @Test
    fun stateHolderDoesNotRepublishANoOpEdit() {
        // Tapping a mandatory row must not republish the account's NIP-78 settings event.
        val saved = mutableListOf<Set<NavBarItem>>()
        val state = DrawerSettingsState(emptySet()) { saved.add(it) }

        state.toggle(NavBarItem.SETTINGS)
        state.restoreDefault()

        assertTrue(saved.isEmpty())
    }
}
