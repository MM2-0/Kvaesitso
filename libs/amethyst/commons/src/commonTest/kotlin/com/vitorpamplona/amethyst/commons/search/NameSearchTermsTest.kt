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

class NameSearchTermsTest {
    private fun terms(input: String) = QueryParser.parse(input).nameSearchTerms()

    @Test
    fun plainWordsPassStraightThrough() {
        assertEquals("vitor", terms("vitor"))
        assertEquals("bitcoin lightning", terms("bitcoin lightning"))
    }

    @Test
    fun aQueryThatIsNothingButAHashtagStillNamesItsWord() {
        // The regression this exists for: the leftover text of `#bitcoin` is empty, so a name
        // search given only the leftovers found nobody — not even a channel called "Bitcoin".
        assertEquals("bitcoin", terms("#bitcoin"))
    }

    @Test
    fun anUnresolvedAuthorNameIsTheWordTheReaderTyped() {
        assertEquals("vitor", terms("from:vitor"))
    }

    @Test
    fun leftoverWordsWinOverATokensValue() {
        // They typed a word as well as a tag; the word is what they are naming.
        assertEquals("lightning", terms("#bitcoin lightning"))
    }

    @Test
    fun onlyOneTermIsEverReturned() {
        // These finders match a single name, so a join would match nothing at all.
        assertEquals("bitcoin", terms("#bitcoin #lightning"))
    }

    @Test
    fun aQueryNamingOnlyFiltersNamesNothing() {
        // A pure date or kind window says nothing about what somebody is called.
        assertEquals("", terms("since:2026-01-01"))
        assertEquals("", terms(""))
    }
}
