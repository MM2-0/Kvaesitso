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
package com.vitorpamplona.amethyst.commons.service.lnurl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LightningAddressInputTest {
    @Test
    fun readsAPlainAddress() {
        assertEquals("alice@getalby.com", lightningAddressOrNull("alice@getalby.com"))
        assertEquals("a.b_c-d+tag@sub.example.co.uk", lightningAddressOrNull("a.b_c-d+tag@sub.example.co.uk"))
    }

    @Test
    fun dropsWhitespaceAndTheLightningScheme() {
        assertEquals("alice@getalby.com", lightningAddressOrNull("  alice@getalby.com\n"))
        assertEquals("alice@getalby.com", lightningAddressOrNull("lightning:alice@getalby.com"))
        assertEquals("alice@getalby.com", lightningAddressOrNull("LIGHTNING:alice@getalby.com"))
    }

    @Test
    fun rejectsInvoicesAndNonAddresses() {
        assertNull(lightningAddressOrNull("lnbc1m1pjt9u0qsp553q90pj5mafzv20w45eqavned9tgwhl4q99n9s5ppcw24nzw3zeqpp5"))
        assertNull(lightningAddressOrNull("lnurl1dp68gurn8ghj7um9wfmxjcm99e3k7mf0v9cxj0m385ekvcenxc6r2c35xvukxefcv5mkvv34x5ekzd3ev56nyd3hxqurzepexejxxepnxscrvwfnv9nxzcn9xq6xyefhvgcxxcmyxymnserxfq5fns"))
        assertNull(lightningAddressOrNull("alice@localhost"))
        assertNull(lightningAddressOrNull("alice@"))
        assertNull(lightningAddressOrNull("@getalby.com"))
        assertNull(lightningAddressOrNull("alice smith@getalby.com"))
        assertNull(lightningAddressOrNull(""))
    }
}
