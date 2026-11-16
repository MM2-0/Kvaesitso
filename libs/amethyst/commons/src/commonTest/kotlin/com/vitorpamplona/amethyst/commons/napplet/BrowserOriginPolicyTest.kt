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
package com.vitorpamplona.amethyst.commons.napplet

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserOriginPolicyTest {
    @Test
    fun httpsGetsNostr() = assertTrue(BrowserOriginPolicy.offersNostr("https://snort.social"))

    @Test
    fun plainHttpDoesNot() = assertFalse(BrowserOriginPolicy.offersNostr("http://snort.social"))

    @Test
    fun aHostThatOnlyLooksLocalDoesNot() = assertFalse(BrowserOriginPolicy.offersNostr("http://localhost.evil.com"))

    @Test
    fun onionOverHttpDoes() = assertTrue(BrowserOriginPolicy.offersNostr("http://abcdefghijklmnopqrstuvwxyz234567abcdefghijklmnopqrstuv.onion"))

    @Test
    fun theDeviceItselfDoes() {
        assertTrue(BrowserOriginPolicy.offersNostr("http://localhost:8080"))
        assertTrue(BrowserOriginPolicy.offersNostr("http://127.0.0.1:3000"))
        assertTrue(BrowserOriginPolicy.offersNostr("http://[::1]:3000"))
    }

    @Test
    fun otherSchemesDoNot() {
        assertFalse(BrowserOriginPolicy.offersNostr("file://"))
        assertFalse(BrowserOriginPolicy.offersNostr("data:text/html"))
    }
}
