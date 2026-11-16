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
package com.vitorpamplona.amethyst.commons.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchEnginesTest {
    @Test
    fun unknownOrMissingIdFallsBackToDefault() {
        assertEquals(SearchEngines.DEFAULT, SearchEngines.byId(null))
        assertEquals(SearchEngines.DEFAULT, SearchEngines.byId("altavista"))
        assertEquals("duckduckgo", SearchEngines.DEFAULT.id)
    }

    @Test
    fun idsAreUniqueAndPrefixesAreHttps() {
        assertEquals(
            SearchEngines.list.size,
            SearchEngines.list
                .map { it.id }
                .toSet()
                .size,
        )
        SearchEngines.list.forEach { assertTrue(it.queryPrefix.startsWith("https://"), it.id) }
    }

    @Test
    fun searchUsesTheChosenEngine() {
        val brave = SearchEngines.byId("brave")
        assertEquals("https://search.brave.com/search?q=cats%20and%20dogs", OmniboxInput.resolve("cats and dogs", brave.queryPrefix)?.url)
        // Addresses are never sent to the engine.
        assertEquals("https://example.com", OmniboxInput.resolve("example.com", brave.queryPrefix)?.url)
    }

    @Test
    fun hostDropsWww() {
        assertEquals("google.com", SearchEngines.byId("google").host)
        assertEquals("duckduckgo.com", SearchEngines.DEFAULT.host)
    }
}
