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

import com.vitorpamplona.amethyst.commons.model.navigation.DrawerItemVisibility
import com.vitorpamplona.amethyst.commons.model.navigation.MandatoryDrawerItems
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem

/**
 * The section-aware half of [DrawerItemVisibility]. It stays in the app because it reads
 * [DrawerSection], whose catalog is Android-gated (see DrawerFeedsItems' API-30 gate on
 * Favorite Apps); the set algebra it builds on is shared, so account settings can sanitize a
 * synced value without knowing which rows this build renders.
 */
object DrawerSectionVisibility {
    /** The rows of [section] to render, in the section's fixed order. */
    fun visibleItems(
        section: DrawerSection,
        hidden: Set<NavBarItem>,
    ): List<NavBarItem> = section.items.filter { DrawerItemVisibility.isVisible(hidden, it) }

    /** How many of [section]'s rows are currently hidden — shown on the collapsed section header. */
    fun hiddenCount(
        section: DrawerSection,
        hidden: Set<NavBarItem>,
    ): Int = section.items.count { !DrawerItemVisibility.isVisible(hidden, it) }

    /** Whether [section] has any row the user is allowed to switch off — gates its bulk actions. */
    fun hasHideableRows(section: DrawerSection): Boolean = section.items.any { it !in MandatoryDrawerItems }

    /** Hides every row of [section] that can be hidden, leaving the mandatory ones. */
    fun hideAll(
        hidden: Set<NavBarItem>,
        section: DrawerSection,
    ): Set<NavBarItem> = hidden + section.items.filter { it !in MandatoryDrawerItems }

    /** Shows every row of [section] again. */
    fun showAll(
        hidden: Set<NavBarItem>,
        section: DrawerSection,
    ): Set<NavBarItem> = hidden - section.items.toSet()

    /** Total hidden rows across every section — the count the settings screen shows at the top. */
    fun totalHidden(hidden: Set<NavBarItem>): Int = DrawerSections.sumOf { hiddenCount(it, hidden) }
}
