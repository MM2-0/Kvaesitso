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
package com.vitorpamplona.amethyst.commons.ui.navigation.drawer

import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.DrawerSectionId
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drawer_section_feeds
import com.vitorpamplona.amethyst.commons.resources.drawer_section_you
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawerSectionRowsTest {
    private val you =
        DrawerSection(DrawerSectionId.YOU, Res.string.drawer_section_you, MaterialSymbols.AccountCircle, listOf(NavBarItem.PROFILE, NavBarItem.BOOKMARKS))
    private val system =
        DrawerSection(DrawerSectionId.SYSTEM, Res.string.drawer_section_feeds, MaterialSymbols.Settings, listOf(NavBarItem.WEB_BOOKMARKS), hasFixedRows = true)

    @Test
    fun aRowListedTwiceShowsOnce() {
        // The lazy list keys rows by section and item: a repeat would crash the drawer.
        val twice = you.copy(items = listOf(NavBarItem.PROFILE, NavBarItem.BOOKMARKS, NavBarItem.PROFILE))
        val rows = drawerSectionRows(listOf(twice), hidden = emptySet(), collapsed = emptySet(), hasFixedRows = listOf(false))
        assertEquals(listOf(NavBarItem.PROFILE, NavBarItem.BOOKMARKS), rows.single().rows)
    }

    @Test
    fun aSectionWithEveryRowHiddenIsLeftOutUnlessItHasFixedRows() {
        val hidden = setOf(NavBarItem.PROFILE, NavBarItem.BOOKMARKS, NavBarItem.WEB_BOOKMARKS)
        val rows = drawerSectionRows(listOf(you, system), hidden, collapsed = emptySet(), hasFixedRows = listOf(false, true))
        assertEquals(listOf(DrawerSectionId.SYSTEM), rows.map { it.section.id })
        assertTrue(rows.single().rows.isEmpty())
        assertTrue(rows.single().hasFixedRows)
    }

    @Test
    fun fixedRowsGatedOffLeaveAnEmptySectionOut() {
        val rows = drawerSectionRows(listOf(system), hidden = setOf(NavBarItem.WEB_BOOKMARKS), collapsed = emptySet(), hasFixedRows = listOf(false))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun aFoldedSectionKeepsItsRowsButIsNotExpanded() {
        val rows = drawerSectionRows(listOf(you, system), hidden = emptySet(), collapsed = setOf(DrawerSectionId.YOU), hasFixedRows = listOf(false, true))
        assertFalse(rows[0].expanded)
        assertEquals(listOf(NavBarItem.PROFILE, NavBarItem.BOOKMARKS), rows[0].rows)
        assertTrue(rows[1].expanded)
    }
}
