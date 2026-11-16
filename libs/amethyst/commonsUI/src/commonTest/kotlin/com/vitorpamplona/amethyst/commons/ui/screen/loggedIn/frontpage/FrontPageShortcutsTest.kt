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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage

import com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.BottomBarEditing
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrontPageShortcutsTest {
    private val home = BottomBarEntry.BuiltIn(NavBarItem.HOME)
    private val messages = BottomBarEntry.BuiltIn(NavBarItem.MESSAGES)

    @Test
    fun offeredOnlyWhileTheFrontPageIsNotInTheBar() {
        assertTrue(FrontPageShortcuts.offersBottomBar(listOf(home, messages)))
        assertTrue(FrontPageShortcuts.offersBottomBar(emptyList()))
        assertFalse(FrontPageShortcuts.offersBottomBar(listOf(home, BottomBarEntry.BuiltIn(NavBarItem.FRONT_PAGE))))
    }

    @Test
    fun addingAppendsOnceAtTheEnd() {
        val added = FrontPageShortcuts.withFrontPage(listOf(home, messages))
        assertEquals(listOf(home, messages, BottomBarEntry.BuiltIn(NavBarItem.FRONT_PAGE)), added)
        assertEquals(added, FrontPageShortcuts.withFrontPage(added))
    }

    @Test
    fun theObserverSiteIsPinnedByItsAddress() {
        val pubkey = "b".repeat(64)
        val site = NamedSiteEvent("c".repeat(64), pubkey, 1_800_000_000, arrayOf(arrayOf("d", "observer")), "", "0".repeat(128))
        val favorite = FrontPageShortcuts.observerFavorite(site, "The Observer", 1L)
        assertEquals("35128:$pubkey:observer", favorite.coordinate)
        val tab = FrontPageShortcuts.favoriteTab(favorite.id)
        val bar = BottomBarEditing.pin(listOf(home), tab)
        assertEquals(listOf(home, tab), bar)
        assertEquals(listOf(home), BottomBarEditing.unpin(bar, BottomBarEntry.Favorite(favorite.id)))
    }
}
