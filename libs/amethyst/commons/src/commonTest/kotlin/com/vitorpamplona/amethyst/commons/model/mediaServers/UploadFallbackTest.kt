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
package com.vitorpamplona.amethyst.commons.model.mediaServers

import kotlin.test.Test
import kotlin.test.assertEquals

class UploadFallbackTest {
    private val band = ServerName("Nostr.Build", "https://blossom.band/")
    private val primal = ServerName("Primal", "https://blossom.primal.net/")
    private val yaki = ServerName("YakiHonne", "https://blossom.yakihonne.com/")

    @Test
    fun `the picked server goes first and the rest follow in list order`() {
        assertEquals(listOf(primal, band, yaki), blossomUploadOrder(primal, listOf(band, primal, yaki)))
    }

    @Test
    fun `a server is never tried twice even under another spelling`() {
        val primalAgain = ServerName("primal", "https://BLOSSOM.PRIMAL.NET")
        assertEquals(listOf(primal, band), blossomUploadOrder(primal, listOf(primalAgain, band, band)))
    }

    @Test
    fun `a server picked from outside the list is still first`() {
        val custom = ServerName("mine", "https://blobs.example.com")
        assertEquals(listOf(custom, band), blossomUploadOrder(custom, listOf(band)))
    }

    @Test
    fun `non-Blossom uploads do not fall back`() {
        val nip96 = ServerName("nostr.build", "https://nostr.build", ServerType.NIP96)
        assertEquals(listOf(nip96), blossomUploadOrder(nip96, listOf(band, nip96)))
        // Nor does a Blossom upload fall back onto a NIP-96 server.
        assertEquals(listOf(band), blossomUploadOrder(band, listOf(nip96)))
    }
}
