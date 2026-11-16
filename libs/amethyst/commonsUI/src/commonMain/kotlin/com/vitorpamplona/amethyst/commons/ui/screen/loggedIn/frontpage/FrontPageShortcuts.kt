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

import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.favoriteCoordinateOf
import com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.BottomBarEditing
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * One tap to put the paper in the bottom bar from where the reader is: the front page itself
 * as a built-in tab, or the reader's public Observer site as a pinned favorite app, both through
 * the bottom-bar editor's own list ([BottomBarEditing]) and the favorites registry.
 */
object FrontPageShortcuts {
    val frontPageTab: BottomBarEntry = BottomBarEntry.BuiltIn(NavBarItem.FRONT_PAGE)

    /** "Add to bottom bar" is offered only while the front page isn't in the bar. */
    fun offersBottomBar(items: List<BottomBarEntry>): Boolean = !BottomBarEditing.isPinned(items, frontPageTab)

    /** The bar with the front page added at the end, as the editor's pin adds it. */
    fun withFrontPage(items: List<BottomBarEntry>): List<BottomBarEntry> = BottomBarEditing.pin(items, frontPageTab)

    /** The reader's Observer site (kind 35128, `d=observer`) as a favorite app, as an nSite card pins it. */
    fun observerFavorite(
        site: Event,
        label: String,
        addedAt: Long,
    ): FavoriteApp.NostrApp = FavoriteApp.NostrApp(favoriteCoordinateOf(site), label, addedAt)

    /** The favorite's tab in the bar. */
    fun favoriteTab(favoriteId: String): BottomBarEntry = BottomBarEntry.Favorite(favoriteId)
}
