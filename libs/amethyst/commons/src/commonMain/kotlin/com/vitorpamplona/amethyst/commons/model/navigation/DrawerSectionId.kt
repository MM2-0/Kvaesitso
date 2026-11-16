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

enum class DrawerSectionId {
    YOU,
    NAVIGATE,
    FEEDS,

    /** Composer entry points. Carries no catalog destinations, so nothing in it is configurable. */
    CREATE,

    /** Also renders the relay-status row, which isn't a catalog destination (it shows a live counter). */
    SYSTEM,
}

private val DrawerSectionIdsByName = DrawerSectionId.entries.associateBy { it.name }

/**
 * Parses the persisted names of the headings the user has collapsed, silently dropping any this
 * build doesn't know. Mirrors [com.vitorpamplona.amethyst.commons.model.navigation.navBarItemsFromNames]:
 * names rather than ordinals, so reordering this enum renames nothing by accident, and a value left
 * by a build with one more section costs that heading rather than the whole read.
 *
 * The stored set holds the **collapsed** headings rather than the expanded ones, for the same reason
 * [DrawerItemVisibility] stores the hidden rows: a heading nobody has ever collapsed simply isn't in
 * the set, so a section added in a later release opens expanded for everyone with no migration.
 */
fun drawerSectionIdsFromNames(names: Collection<String>): Set<DrawerSectionId> = names.mapNotNullTo(mutableSetOf()) { DrawerSectionIdsByName[it] }

/**
 * The inverse of [drawerSectionIdsFromNames]. Unlike the NavBarItem codec this returns an unsorted
 * Set rather than a sorted List: the destination is a DataStore string set, whose equality is
 * already order-independent, so there is no serialized form to keep deterministic.
 */
fun Set<DrawerSectionId>.toNames(): Set<String> = mapTo(mutableSetOf()) { it.name }
