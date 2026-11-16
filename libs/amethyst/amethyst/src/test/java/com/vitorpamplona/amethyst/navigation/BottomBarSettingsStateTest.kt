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

import com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry
import com.vitorpamplona.amethyst.commons.model.navigation.DefaultBottomBarEntries
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.amethyst.commons.model.navigation.stableKey
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.BottomBarEditing
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.BottomBarSettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarSettingsStateTest {
    private val home = BottomBarEntry.BuiltIn(NavBarItem.HOME)
    private val messages = BottomBarEntry.BuiltIn(NavBarItem.MESSAGES)
    private val group = BottomBarEntry.RelayGroup("abcd", "wss://relay.example")
    private val chat = BottomBarEntry.PublicChat("f".repeat(64))

    // --- Pure edit operations ---

    @Test
    fun togglePinAddsWhenAbsentAndRemovesWhenPresent() {
        val once = BottomBarEditing.togglePin(listOf(home), messages)
        assertEquals(listOf(home, messages), once)

        val twice = BottomBarEditing.togglePin(once, messages)
        assertEquals(listOf(home), twice)
    }

    @Test
    fun togglePinIsIdempotentOverTwoCalls() {
        val start = listOf(home, group)
        val roundTrip = BottomBarEditing.togglePin(BottomBarEditing.togglePin(start, chat), chat)
        assertEquals(start, roundTrip)
    }

    @Test
    fun togglePinRemovesByStableIdentityNotReferenceEquality() {
        val start = listOf(BottomBarEntry.RelayGroup("abcd", "wss://relay.example"))
        // A distinct instance with the same (id, relay) resolves to the same stableKey.
        val result = BottomBarEditing.togglePin(start, BottomBarEntry.RelayGroup("abcd", "wss://relay.example"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun moveReordersAndClampsOutOfBounds() {
        val start = listOf(home, messages, group)
        assertEquals(listOf(messages, group, home), BottomBarEditing.move(start, 0, 2))
        assertEquals(start, BottomBarEditing.move(start, 1, 1)) // no-op: same index
        assertEquals(start, BottomBarEditing.move(start, -1, 2)) // no-op: out of bounds
        assertEquals(start, BottomBarEditing.move(start, 0, 9)) // no-op: out of bounds
    }

    @Test
    fun stableKeyIsUniquePerTypeEvenWhenUnderlyingStringMatches() {
        val a = BottomBarEntry.PublicChat("x").stableKey
        val b = BottomBarEntry.Concord("x").stableKey
        val c = BottomBarEntry.Favorite("x").stableKey
        assertEquals(3, setOf(a, b, c).size)
    }

    // --- Holder: persistence semantics ---

    @Test
    fun togglePinPersistsImmediately() {
        var saved: List<BottomBarEntry>? = null
        val state = BottomBarSettingsState(listOf(home)) { saved = it }

        state.togglePin(messages)

        assertEquals(listOf(home, messages), state.pinned)
        assertEquals(listOf(home, messages), saved)
    }

    @Test
    fun moveTransientDoesNotPersistUntilCommit() {
        var saveCount = 0
        val state = BottomBarSettingsState(listOf(home, messages)) { saveCount++ }

        state.moveTransient(0, 1)
        assertEquals(listOf(messages, home), state.pinned)
        assertEquals(0, saveCount) // dragging does not write

        state.commit()
        assertEquals(1, saveCount) // drag end writes once
    }

    @Test
    fun restoreDefaultPersistsTheDefaults() {
        var saved: List<BottomBarEntry>? = null
        val state = BottomBarSettingsState(listOf(home)) { saved = it }

        state.restoreDefault()

        assertEquals(DefaultBottomBarEntries, state.pinned)
        assertEquals(state.pinned, saved)
    }

    @Test
    fun syncFromOnlyUpdatesWhenDifferent() {
        var saveCount = 0
        val state = BottomBarSettingsState(listOf(home)) { saveCount++ }

        state.syncFrom(listOf(home)) // equal → no change, and never persists
        assertEquals(listOf(home), state.pinned)

        state.syncFrom(listOf(home, messages)) // external change adopted
        assertEquals(listOf(home, messages), state.pinned)
        assertEquals(0, saveCount) // syncFrom must never persist (it would echo-loop)
    }

    @Test
    fun isPinnedReflectsMembership() {
        val state = BottomBarSettingsState(listOf(home, group)) {}
        assertTrue(state.isPinned(group))
        assertFalse(state.isPinned(messages))
    }
}
