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
package com.vitorpamplona.amethyst.commons.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WholeInputCodeTest {
    private companion object {
        const val NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
        const val NOTE = "note1stqea6wmwezg9x6yyr6qkukw95ewtdukyaztycws65l8wppjmtpscawevv"
    }

    @Test
    fun aPastedCodeIsTheWholeInput() {
        assertEquals(NPUB, wholeInputNip19(NPUB))
        assertEquals(NPUB, wholeInputNip19("  $NPUB  "))
        assertEquals(NPUB, wholeInputNip19("nostr:$NPUB"))
        assertEquals(NOTE, wholeInputNip19(NOTE))
    }

    @Test
    fun anAuthorFilterIsNotAPastedCode() {
        // The regression this exists for: `Nip19Parser` finds the npub inside this string, so the
        // search box treated typing a filter as pasting a profile and navigated away mid-query.
        assertNull(wholeInputNip19("from:$NPUB"))
        assertNull(wholeInputNip19("to:$NPUB"))
        assertNull(wholeInputNip19("to:$NOTE"))
    }

    @Test
    fun aCodeWithOtherWordsAroundItIsNotAPaste() {
        assertNull(wholeInputNip19("$NPUB bitcoin"))
        assertNull(wholeInputNip19("look at $NPUB"))
    }

    @Test
    fun anythingThatIsNotACodeIsNull() {
        assertNull(wholeInputNip19(null))
        assertNull(wholeInputNip19(""))
        assertNull(wholeInputNip19("   "))
        assertNull(wholeInputNip19("bitcoin"))
        assertNull(wholeInputNip19("#bitcoin"))
        // Hex is deliberately out of scope: it is ambiguous between a key and an event id.
        assertNull(wholeInputNip19("a".repeat(64)))
    }
}
