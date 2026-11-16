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

import com.vitorpamplona.amethyst.commons.model.navigation.DrawerSectionId
import com.vitorpamplona.amethyst.commons.model.navigation.MandatoryDrawerItems
import com.vitorpamplona.amethyst.commons.model.navigation.drawerSectionIdsFromNames
import com.vitorpamplona.amethyst.commons.model.navigation.toNames
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.NavBarCatalog
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.DrawerSections
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.SdkGatedDrawerItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drawer and its settings screen both render [DrawerSections], so a destination missing from
 * every section is invisible in both places at once — no compiler error, no crash, just a screen
 * nobody can reach from the menu. These pin the invariants that keep that from happening quietly.
 */
class DrawerSectionsTest {
    @Test
    fun everyCatalogItemAppearsInADrawerSection() {
        val sectioned = DrawerSections.flatMap { it.items }.toSet()
        val missing = NavBarCatalog.keys - sectioned

        assertEquals(
            "a catalog destination is in no drawer section — add it to one in NavBarItem.kt, " +
                "or to SdkGatedDrawerItems with the reason it can't be there",
            emptySet<Any>(),
            missing - SdkGatedDrawerItems,
        )
    }

    @Test
    fun noItemIsListedInTwoSections() {
        val sectioned = DrawerSections.flatMap { it.items }

        assertEquals("an item is listed in more than one drawer section", sectioned.size, sectioned.toSet().size)
    }

    @Test
    fun everySectionedItemResolvesInTheCatalog() {
        // The drawer looks each id up in NavBarCatalog and skips misses, so an id with no catalog
        // entry would silently render nothing while still occupying a row in the settings screen.
        DrawerSections.forEach { section ->
            section.items.forEach { item ->
                assertTrue("$item has no NavBarCatalog entry", NavBarCatalog.containsKey(item))
            }
        }
    }

    @Test
    fun sectionsAreOrderedWithCreateBetweenFeedsAndSystem() {
        // The drawer renders DrawerSections in order, so this list *is* the menu's layout. Create sits
        // between the feeds and System, and is the one section with nothing configurable in it.
        assertEquals(
            listOf(
                DrawerSectionId.YOU,
                DrawerSectionId.NAVIGATE,
                DrawerSectionId.FEEDS,
                DrawerSectionId.CREATE,
                DrawerSectionId.SYSTEM,
            ),
            DrawerSections.map { it.id },
        )
        assertEquals(emptyList<Any>(), DrawerSections.first { it.id == DrawerSectionId.CREATE }.items)
    }

    @Test
    fun everySectionHasItsOwnId() {
        val ids = DrawerSections.map { it.id }

        assertEquals("two sections share a DrawerSectionId", ids.size, ids.toSet().size)
    }

    @Test
    fun aSectionWithNoCatalogItemsRendersFixedRowsOrNothingAtAll() {
        // hasFixedRows is declared on the section but consumed by drawerSectionItems' `when (section.id)`,
        // in another file — so the flag and the branch that honours it can drift apart with no compile
        // error. A section that carries neither is unreachable in both directions at once: the settings
        // screen skips it on items.isEmpty(), and drawerSectionRows drops it before a heading renders.
        val unreachable = DrawerSections.filter { it.items.isEmpty() && !it.hasFixedRows }

        assertEquals(
            "a drawer section has no catalog items and no fixed rows, so it renders nowhere — " +
                "give it items, set hasFixedRows and a branch in drawerSectionItems, or delete it",
            emptyList<Any>(),
            unreachable.map { it.id },
        )
    }

    @Test
    fun everySectionSurvivesTheCollapsedHeadingNameRoundTrip() {
        // The collapsed-heading preference stores DrawerSectionIds by name, and CollapsibleSection is
        // driven purely by that id — so a heading whose name didn't round trip would collapse on tap
        // and spring open again on the next launch, with nothing failing anywhere in between.
        val all = DrawerSections.map { it.id }.toSet()

        assertEquals(all, drawerSectionIdsFromNames(all.toNames()))
    }

    @Test
    fun aCollapsedHeadingNameFromAnotherBuildIsDroppedInsteadOfFailingTheWholeRead() {
        // Stored as names precisely for this: a section that exists in a newer build (or one deleted
        // since) must cost that one heading, not every other heading the user collapsed.
        assertEquals(
            setOf(DrawerSectionId.FEEDS),
            drawerSectionIdsFromNames(setOf("FEEDS", "SOME_SECTION_FROM_THE_FUTURE")),
        )
    }

    @Test
    fun mandatoryItemsAreActuallyRenderedByASection() {
        // A mandatory item that no section renders would be unhideable *and* invisible — the worst
        // of both. Settings is mandatory precisely because it is the way back to this configuration.
        val sectioned = DrawerSections.flatMap { it.items }.toSet()

        assertTrue("a mandatory drawer item is in no section", sectioned.containsAll(MandatoryDrawerItems))
    }
}
