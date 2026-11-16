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

/**
 * Which drawer rows the user cannot hide.
 *
 * Settings is the only one, and it is mandatory for a specific reason: it is the route back to the
 * screen that hides rows in the first place. Hiding it would let a user lock themselves out of their
 * own configuration. Everything else the drawer always shows — the profile header, the relay-status
 * row, the account switcher and the version/QR footer — is fixed chrome rather than a catalog row,
 * so it is present by construction and never appears in the hidden set.
 */
val MandatoryDrawerItems: Set<NavBarItem> = setOf(NavBarItem.SETTINGS)

/**
 * Pure show/hide rules for the drawer's catalog rows, kept free of Compose and Android so they are
 * exercised directly by unit tests (DrawerItemVisibilityTest) rather than only through the UI.
 *
 * The per-account preference stores the **hidden** items rather than the visible ones. That choice is
 * what makes a newly added destination appear for everyone automatically: a row nobody has ever
 * hidden simply isn't in the set, so it renders. Storing the visible list instead would freeze each
 * account's drawer at the moment they first touched the setting, and every later release would have
 * to migrate saved lists to introduce a screen.
 */
object DrawerItemVisibility {
    fun isVisible(
        hidden: Set<NavBarItem>,
        item: NavBarItem,
    ): Boolean = item in MandatoryDrawerItems || item !in hidden

    /** Hides [item] if shown, shows it if hidden. Mandatory items never change (see [MandatoryDrawerItems]). */
    fun toggle(
        hidden: Set<NavBarItem>,
        item: NavBarItem,
    ): Set<NavBarItem> =
        when {
            item in MandatoryDrawerItems -> hidden
            item in hidden -> hidden - item
            else -> hidden + item
        }

    /**
     * Drops mandatory rows from the set. The persistence layer is the single place this is enforced —
     * it runs on decode, on an external sync, and on every write — so a value synced from another
     * client (or from a build where the row wasn't mandatory yet) can't strand Settings as hidden.
     *
     * Ids that no section renders are deliberately *kept*: on a device where a row is gated off (see
     * DrawerFeedsItems' API-30 gate on Favorite Apps) it matches nothing and costs nothing, and
     * preserving it means editing the drawer on that device doesn't silently clear the choice the
     * user made on another one.
     */
    fun sanitize(hidden: Set<NavBarItem>): Set<NavBarItem> = hidden - MandatoryDrawerItems
}
