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
package com.vitorpamplona.amethyst.commons.defaults

import kotlin.test.Test
import kotlin.test.assertEquals

class RelayListDefaultsTest {
    private val defaults = setOf("wss://default.one", "wss://default.two")

    /** No event: we genuinely do not know what the user wants, so the app's defaults stand in. */
    @Test
    fun `absent event yields the defaults`() {
        assertEquals(defaults, relayListOrDefaultsWhenUnknown<String, String>(null, defaults) { setOf("wss://ignored") })
    }

    /**
     * The case every open-coded copy of this rule got wrong: a published-but-empty list is the user
     * saying "nothing", and must not acquire the defaults.
     */
    @Test
    fun `present event with an empty list stays empty`() {
        assertEquals(emptySet(), relayListOrDefaultsWhenUnknown("event", defaults) { emptySet<String>() })
    }

    /**
     * Same, via null: several event accessors end in `.ifEmpty { null }`, so null from a *present*
     * event means the user listed nothing — not that the event is missing.
     */
    @Test
    fun `present event whose reader returns null stays empty`() {
        assertEquals(emptySet(), relayListOrDefaultsWhenUnknown<String, String>("event", defaults) { null })
    }

    @Test
    fun `present event with relays yields those relays`() {
        val mine = setOf("wss://mine.example")
        assertEquals(mine, relayListOrDefaultsWhenUnknown("event", defaults) { mine })
    }

    /** The reader must not even be consulted when there is no event to read. */
    @Test
    fun `reader is not invoked for an absent event`() {
        var invoked = false
        relayListOrDefaultsWhenUnknown<String, String>(null, defaults) {
            invoked = true
            emptySet()
        }
        assertEquals(false, invoked)
    }
}
