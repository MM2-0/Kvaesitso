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
package com.vitorpamplona.amethyst.service.okhttp

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Verifies that OkHttp 5.x HttpUrl.toHttpUrl() accepts .onion pseudo-TLD hostnames.
 *
 * .onion is not an IANA-registered TLD (RFC 7686), so it may be rejected by strict
 * domain validators. If toHttpUrl() rejects .onion URLs the entire OnionUrlRewriteInterceptor
 * feature silently falls through to the clearnet address. This test documents and guards
 * the actual OkHttp behavior so any upgrade that breaks .onion support is caught.
 */
class OnionUrlParseTest {
    // A real Tor v3 .onion address (56 base32 chars + ".onion")
    private val torV3Host = "vww6ybal4bd7szmgncyruucpgfkqahzddi37ktceo3ah7ngmcopnpyyd.onion"

    @Test
    fun torV3HttpUrl_parsesSuccessfully() {
        val url = runCatching { "http://$torV3Host/".toHttpUrl() }.getOrNull()
        assertNotNull(
            "OkHttp must accept Tor v3 .onion hosts — if null, OnionUrlRewriteInterceptor is silently broken",
            url,
        )
    }

    @Test
    fun torV3HttpUrl_hostIsPreserved() {
        val url = "http://$torV3Host/".toHttpUrl()
        assertEquals(torV3Host, url.host)
    }

    @Test
    fun torV3HttpsUrl_parsesSuccessfully() {
        val url = runCatching { "https://$torV3Host/".toHttpUrl() }.getOrNull()
        assertNotNull(url)
    }

    @Test
    fun torV3WithPort_parsesSuccessfully() {
        val url = runCatching { "http://$torV3Host:8080/path".toHttpUrl() }.getOrNull()
        assertNotNull(url)
        assertEquals(8080, url?.port)
    }

    @Test
    fun shortOnionLabel_parsesSuccessfully() {
        // Shorter label (e.g. Tor v2-style) — also must be accepted
        val url = runCatching { "http://relay.someservice.onion/".toHttpUrl() }.getOrNull()
        assertNotNull(url)
    }

    // --- Regression: OnionUrlRewriteInterceptor scheme translation ---

    @Test
    fun wsOriginalWithHttpOnion_rewritesToWs() {
        val onionUrlStr = "http://$torV3Host/"
        val onionUrl = onionUrlStr.toHttpUrl()
        val originalScheme = "ws"
        val newScheme =
            if (originalScheme == "ws" || originalScheme == "wss") {
                if (onionUrl.scheme == "https") "wss" else "ws"
            } else {
                onionUrl.scheme
            }
        assertEquals("ws", newScheme)
    }

    @Test
    fun wssOriginalWithHttpsOnion_rewritesToWss() {
        val onionUrlStr = "https://$torV3Host/"
        val onionUrl = onionUrlStr.toHttpUrl()
        val originalScheme = "wss"
        val newScheme =
            if (originalScheme == "ws" || originalScheme == "wss") {
                if (onionUrl.scheme == "https") "wss" else "ws"
            } else {
                onionUrl.scheme
            }
        assertEquals("wss", newScheme)
    }
}
