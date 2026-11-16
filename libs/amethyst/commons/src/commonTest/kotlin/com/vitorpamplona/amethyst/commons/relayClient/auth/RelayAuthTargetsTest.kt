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
package com.vitorpamplona.amethyst.commons.relayClient.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayAuthTargetsTest {
    @Test
    fun aPlainRelayShowsAsItsHost() {
        val target = relayAuthTarget("wss://relay.damus.io")
        assertEquals("relay.damus.io", target.display)
        assertNotNull(target.url)
        assertFalse(target.unusual)
    }

    @Test
    fun trailingSlashAndHostCaseAreNotUnusual() {
        assertFalse(relayAuthTarget("wss://relay.damus.io/").unusual)
        assertFalse(relayAuthTarget("wss://Relay.Damus.io").unusual)
    }

    @Test
    fun anInsecureSchemeStaysVisible() {
        val target = relayAuthTarget("ws://relay.example.com")
        assertEquals("ws://relay.example.com", target.display)
        assertFalse(target.unusual)
    }

    @Test
    fun aValueTheNormalizerWouldRepairIsShownVerbatim() {
        // The normalizer adds a scheme / trims %20; the user must see what is actually signed.
        val noScheme = relayAuthTarget("relay.damus.io")
        assertEquals("relay.damus.io", noScheme.display)
        assertTrue(noScheme.unusual)

        val padded = relayAuthTarget("wss://relay.damus.io%20")
        assertEquals("wss://relay.damus.io%20", padded.display)
        assertTrue(padded.unusual)
    }

    @Test
    fun hiddenDirectionCharactersAreEscapedNotRendered() {
        // RLO would make "wss://\u202Eoi.live.xyz" read as a different host.
        val target = relayAuthTarget("wss://\u202Eoi.live.xyz")
        assertEquals("wss://\\u202Eoi.live.xyz", target.display)
        assertTrue(target.unusual)
    }

    @Test
    fun zeroWidthCharactersAreEscaped() {
        val target = relayAuthTarget("wss://relay.da\u200Bmus.io")
        assertEquals("wss://relay.da\\u200Bmus.io", target.display)
        assertTrue(target.unusual)
    }

    @Test
    fun garbageIsStillShownAndFlagged() {
        val target = relayAuthTarget("not a relay")
        assertEquals("not a relay", target.display)
        assertNull(target.url)
        assertTrue(target.unusual)
    }

    @Test
    fun readsEveryRelayTagInOrderAndSkipsTheRest() {
        val tags =
            arrayOf(
                arrayOf("relay", "wss://a.example.com"),
                arrayOf("challenge", "abc"),
                arrayOf("relay", ""),
                arrayOf("relay"),
                arrayOf("relay", "wss://b.example.com"),
            )
        assertEquals(listOf("a.example.com", "b.example.com"), tags.relayAuthTargets().map { it.display })
    }
}
