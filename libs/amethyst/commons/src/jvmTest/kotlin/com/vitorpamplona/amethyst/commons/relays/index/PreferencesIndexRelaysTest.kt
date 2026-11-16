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
package com.vitorpamplona.amethyst.commons.relays.index

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.prefs.Preferences

class PreferencesIndexRelaysTest {
    private val testNode = "com/vitorpamplona/amethyst/test/relays/index_${System.currentTimeMillis()}"

    private fun prefs(): Preferences = Preferences.userRoot().node(testNode)

    @Before
    fun setup() {
        prefs().clear()
    }

    @After
    fun teardown() {
        prefs().removeNode()
    }

    @Test
    fun defaultsWhenPreferencesUnset() {
        val store = PreferencesIndexRelays(prefs())
        assertTrue(store.relays.value.isEmpty())
        assertEquals(PreferencesIndexRelays.DEFAULT_INDEX_RELAYS, store.effective())
    }

    @Test
    fun setRelaysPersistsAcrossInstances() {
        val store = PreferencesIndexRelays(prefs())
        val urls =
            listOf("wss://relay.example", "wss://index.example")
                .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                .toSet()
        store.setRelays(urls)
        assertEquals(urls, store.relays.value)

        val reloaded = PreferencesIndexRelays(prefs())
        assertEquals(urls, reloaded.relays.value)
        assertEquals(urls, reloaded.effective())
    }

    @Test
    fun effectiveFallsBackWhenOverrideCleared() {
        val store = PreferencesIndexRelays(prefs())
        val urls =
            listOf("wss://relay.example")
                .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                .toSet()
        store.setRelays(urls)
        store.setRelays(emptySet())
        assertEquals(PreferencesIndexRelays.DEFAULT_INDEX_RELAYS, store.effective())
    }

    @Test
    fun emptyEntriesInCsvAreSkipped() {
        // Plant a URL list with empty tokens (extra commas). The
        // parser should skip blanks silently.
        prefs().put(PreferencesIndexRelays.KEY_URLS, "wss://good.example,,wss://also-good.example,")
        val store = PreferencesIndexRelays(prefs())
        // Both good URLs should be present; no blank / empty entry.
        assertEquals(2, store.relays.value.size)
        assertTrue(store.relays.value.none { it.url.isBlank() })
    }

    @Test
    fun defaultSetIsNotEmpty() {
        // Guardrail against a future refactor accidentally clearing the constant.
        assertTrue(PreferencesIndexRelays.DEFAULT_INDEX_RELAYS.isNotEmpty())
    }
}
