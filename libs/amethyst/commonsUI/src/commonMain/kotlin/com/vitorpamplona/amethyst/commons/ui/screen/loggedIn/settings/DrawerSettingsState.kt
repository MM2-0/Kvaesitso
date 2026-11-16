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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.model.navigation.DrawerItemVisibility
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.DrawerSection
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.DrawerSectionVisibility

/**
 * State holder for the Side Menu settings screen: owns the set of switched-off drawer rows and the
 * show / hide / restore-default operations, so the composable only renders and forwards events.
 *
 * The rules themselves live in [DrawerItemVisibility] (pure, unit-tested); this adds only the Compose
 * state and the write-through to the account's synced settings. Unlike the bottom bar there is no
 * transient/commit split — a toggle is a single discrete edit, not a drag, so every change persists
 * immediately.
 *
 * No sanitizing here: every value in is either already sanitized by the persistence layer or produced
 * by a [DrawerItemVisibility] operation that can't introduce a mandatory row, and the write side
 * sanitizes again anyway. One authority, not three.
 */
@Stable
class DrawerSettingsState(
    initial: Set<NavBarItem>,
    private val persist: (Set<NavBarItem>) -> Unit,
) {
    var hidden by mutableStateOf(initial)
        private set

    fun isVisible(item: NavBarItem): Boolean = DrawerItemVisibility.isVisible(hidden, item)

    fun toggle(item: NavBarItem) = update(DrawerItemVisibility.toggle(hidden, item))

    fun hiddenCount(section: DrawerSection): Int = DrawerSectionVisibility.hiddenCount(section, hidden)

    fun totalHidden(): Int = DrawerSectionVisibility.totalHidden(hidden)

    fun showAll(section: DrawerSection) = update(DrawerSectionVisibility.showAll(hidden, section))

    fun hideAll(section: DrawerSection) = update(DrawerSectionVisibility.hideAll(hidden, section))

    /** Back to the stock drawer: nothing hidden. */
    fun restoreDefault() = update(emptySet())

    /**
     * Re-seed from an external change (the saved settings flow emitted) without re-persisting. A no-op
     * when equal, so the echo of our own [persist] doesn't fight an in-progress edit.
     */
    fun syncFrom(items: Set<NavBarItem>) {
        if (items != hidden) hidden = items
    }

    private fun update(newHidden: Set<NavBarItem>) {
        if (newHidden == hidden) return
        hidden = newHidden
        persist(newHidden)
    }
}
