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

/**
 * Which pages of the in-app browser get `window.nostr` (NIP-07).
 *
 * A grant is kept per origin. On plain `http://` anyone on the path (the café's Wi-Fi, an ISP, a Tor
 * exit) can put a script into that origin's pages, and it would sign and decrypt with whatever the user
 * once allowed the site. So only pages whose transport can't be tampered with may ask: https, an
 * `.onion` (Tor authenticates the service), and the device itself.
 */
object BrowserOriginPolicy {
    fun offersNostr(origin: String): Boolean {
        val scheme = origin.substringBefore("://", "").lowercase()
        if (scheme == "https") return true
        if (scheme != "http") return false
        val authority = origin.substringAfter("://").substringBefore('/')
        val host =
            if (authority.startsWith('[')) {
                authority.substringBefore(']') + "]"
            } else {
                authority.substringBefore(':')
            }.lowercase()
        return host.endsWith(".onion") || host == "localhost" || host == "127.0.0.1" || host == "[::1]"
    }
}
