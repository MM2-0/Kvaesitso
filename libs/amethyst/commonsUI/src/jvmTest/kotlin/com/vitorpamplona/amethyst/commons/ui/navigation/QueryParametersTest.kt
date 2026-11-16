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
package com.vitorpamplona.amethyst.commons.ui.navigation

import com.vitorpamplona.amethyst.commons.model.navigation.uri.findQueryParameterValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueryParametersTest {
    @Test
    fun readsOpaqueAndHierarchicalQueries() {
        assertEquals("npub1abc", "marmot:0011?account=npub1abc".findQueryParameterValue("account"))
        assertEquals("30078:ab:x", "connectedapp?coordinate=30078:ab:x&y=1".findQueryParameterValue("coordinate"))
        assertEquals("1", "https://h/p?a=0&y=1".findQueryParameterValue("y"))
    }

    @Test
    fun dropsTheFragment() {
        assertEquals("abc", "connectedapp?coordinate=abc#x".findQueryParameterValue("coordinate"))
        assertNull("connectedapp#x?coordinate=abc".findQueryParameterValue("coordinate"))
    }

    @Test
    fun keepsEqualsInsideValues() {
        assertEquals("https://h/p?q=1", "url?id=https://h/p?q=1".findQueryParameterValue("id"))
        assertEquals("a=b", "x?k=a=b".findQueryParameterValue("k"))
    }

    @Test
    fun missingOrEmptyIsNull() {
        assertNull("nevent1xyz".findQueryParameterValue("id"))
        assertNull("x?id=".findQueryParameterValue("id"))
        assertNull("x?other=1".findQueryParameterValue("id"))
    }
}
