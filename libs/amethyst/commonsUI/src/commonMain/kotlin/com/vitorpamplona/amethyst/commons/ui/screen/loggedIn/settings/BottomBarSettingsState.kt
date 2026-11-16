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
import com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry
import com.vitorpamplona.amethyst.commons.model.navigation.DefaultBottomBarEntries
import com.vitorpamplona.amethyst.commons.model.navigation.stableKey

/**
 * Pure list transforms for the bottom-bar pinned list. Kept free of Compose/Android so the pin,
 * unpin and reorder rules are exercised directly by unit tests (BottomBarSettingsStateTest) rather
 * than only through the drag UI. [BottomBarSettingsState] is the thin stateful wrapper the screen uses.
 */
object BottomBarEditing {
    fun isPinned(
        items: List<BottomBarEntry>,
        entry: BottomBarEntry,
    ): Boolean = items.any { it.stableKey == entry.stableKey }

    /** Adds [entry] to the end if absent, or removes it (by stable identity) if present. */
    fun togglePin(
        items: List<BottomBarEntry>,
        entry: BottomBarEntry,
    ): List<BottomBarEntry> =
        if (isPinned(items, entry)) {
            items.filter { it.stableKey != entry.stableKey }
        } else {
            items + entry
        }

    /** Adds [entry] to the end unless it is already there: a one-tap "add to bottom bar" from elsewhere. */
    fun pin(
        items: List<BottomBarEntry>,
        entry: BottomBarEntry,
    ): List<BottomBarEntry> = if (isPinned(items, entry)) items else items + entry

    /** [items] without [entry] (by stable identity). */
    fun unpin(
        items: List<BottomBarEntry>,
        entry: BottomBarEntry,
    ): List<BottomBarEntry> = items.filter { it.stableKey != entry.stableKey }

    /** Moves the item at [from] to index [to]; a no-op if either index is out of bounds. */
    fun move(
        items: List<BottomBarEntry>,
        from: Int,
        to: Int,
    ): List<BottomBarEntry> {
        if (from == to || from !in items.indices || to !in items.indices) return items
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }
}

/**
 * State holder for the Bottom Bar settings screen: owns the ordered pinned list and the pin / unpin /
 * reorder / restore-default operations, so the composable only renders and forwards events. During a
 * drag, [moveTransient] reorders without persisting; [commit] writes the final order once the drag ends.
 */
@Stable
class BottomBarSettingsState(
    initial: List<BottomBarEntry>,
    private val persist: (List<BottomBarEntry>) -> Unit,
) {
    var pinned by mutableStateOf(initial)
        private set

    fun isPinned(entry: BottomBarEntry): Boolean = BottomBarEditing.isPinned(pinned, entry)

    fun pinnedKeys(): Set<String> = pinned.mapTo(HashSet(pinned.size)) { it.stableKey }

    fun togglePin(entry: BottomBarEntry) = update(BottomBarEditing.togglePin(pinned, entry))

    fun restoreDefault() = update(DefaultBottomBarEntries)

    /** Reorder mid-drag WITHOUT persisting. Pair with [commit] when the gesture ends. */
    fun moveTransient(
        from: Int,
        to: Int,
    ) {
        pinned = BottomBarEditing.move(pinned, from, to)
    }

    /** Persist the current (post-drag) order. */
    fun commit() = persist(pinned)

    /**
     * Re-seed from an external change (the saved settings flow emitted) without re-persisting. A no-op
     * when equal, so the echo of our own [persist] doesn't clobber an in-progress edit.
     */
    fun syncFrom(items: List<BottomBarEntry>) {
        if (items != pinned) pinned = items
    }

    private fun update(newItems: List<BottomBarEntry>) {
        pinned = newItems
        persist(newItems)
    }
}
