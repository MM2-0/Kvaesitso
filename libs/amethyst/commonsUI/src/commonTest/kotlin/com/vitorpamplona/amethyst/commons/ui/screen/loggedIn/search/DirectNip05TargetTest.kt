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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.search

import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DirectNip05TargetTest {
    @Test
    fun bareBitDomainOpensItsWildcardAddress() {
        assertEquals(Nip05Id("_", "mstrofnone.bit"), directNip05Target("mstrofnone.bit"))
        assertEquals(Nip05Id("_", "mstrofnone.bit"), directNip05Target("MstrOfNone.bit"))
    }

    @Test
    fun fullAddressOpensAsTyped() {
        assertEquals(Nip05Id("m", "testls.bit"), directNip05Target("m@testls.bit"))
        assertEquals(Nip05Id("alice", "example.com"), directNip05Target("alice@example.com"))
    }

    @Test
    fun seededKindChipBeforeTheNameIsASearchNotALookup() {
        // The Git Repositories search icon seeds `kind:git `; the name typed after it
        // must not be glued to the chip and looked up as `_@kind:git mstrofnone.bit`.
        assertNull(directNip05Target("kind:git mstrofnone.bit"))
        assertNull(directNip05Target("#nostr mstrofnone.bit"))
        assertNull(directNip05Target("foo mstrofnone.bit"))
    }

    @Test
    fun seededKindChipBeforeAFullAddressIsASearchNotALookup() {
        assertNull(directNip05Target("kind:git m@testls.bit"))
    }

    @Test
    fun plainWordsAreNotAddresses() {
        assertNull(directNip05Target("rabbit"))
        assertNull(directNip05Target("example.com"))
        assertNull(directNip05Target(""))
    }
}
