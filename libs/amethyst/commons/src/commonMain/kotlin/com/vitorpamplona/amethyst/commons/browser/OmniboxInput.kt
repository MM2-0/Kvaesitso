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

/**
 * Turns raw omnibox text into something the browser can load. The single source of truth shared by the
 * launcher's address bar (main process) and the in-page address bar in the `:napplet` browser host, so
 * both behave identically.
 *
 * The rules, in order:
 * - blank → null (nothing to open)
 * - already has a scheme (`foo://…`) and no spaces → used verbatim
 * - looks like a host/URL (no spaces, has a dot, or is `localhost`) → `https://` prepended
 * - anything else — including prose that merely contains a link → a search on [searchPrefix] (DuckDuckGo by default), URL-encoded
 *
 * [Resolved.forceTor] is set for `.onion` addresses, which only resolve over Tor; the caller ORs it with
 * the user's per-site choice. Pure and platform-agnostic (no `android.net.Uri`) so it lives in commons
 * and is unit-tested directly.
 */
object OmniboxInput {
    /** Default search engine. A prefix the query is URL-encoded onto; swappable per call so a future setting can override it. */
    const val DEFAULT_SEARCH_PREFIX = "https://duckduckgo.com/?q="

    data class Resolved(
        val url: String,
        val forceTor: Boolean,
        /** True when [url] is a search for the text rather than the address the user typed. */
        val isSearch: Boolean = false,
    )

    fun resolve(
        raw: String,
        searchPrefix: String = DEFAULT_SEARCH_PREFIX,
    ): Resolved? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (!isAddress(text)) return Resolved(searchPrefix + encodeQuery(text), forceTor = false, isSearch = true)
        val url = if (hasScheme(text)) text else "https://$text"
        return Resolved(url, isOnion(url))
    }

    /**
     * True when [raw] would be opened as an address rather than searched for — the one rule [resolve]
     * follows, exposed so the address bar's icon and hint can say what Go will do before it happens.
     */
    fun isAddress(raw: String): Boolean {
        val text = raw.trim()
        return hasScheme(text) || looksLikeHost(text)
    }

    private fun hasScheme(text: String): Boolean = text.contains("://") && text.none { it.isWhitespace() }

    /**
     * True when [text] (with no scheme) reads as a hostname/URL rather than a search query: no spaces, and
     * either `localhost` or a dotted host (so `example.com`, `1.2.3.4`, `localhost:8080` are hosts but
     * `how to tie a knot` and `cats` are searches).
     */
    fun looksLikeHost(text: String): Boolean {
        if (text.isEmpty() || text.any { it.isWhitespace() }) return false
        val host = hostPart(text)
        if (host.isEmpty()) return false
        if (host.equals("localhost", ignoreCase = true)) return true
        return host.contains('.') && !host.startsWith('.') && !host.endsWith('.')
    }

    /** The host of [url] (scheme/userinfo/port/path stripped), or null when it can't be read. */
    fun hostOf(url: String): String? {
        val authority =
            url
                .substringAfter("://", url)
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
        val afterUserInfo = authority.substringAfterLast('@', authority)
        // IPv6 literal: keep everything inside the brackets.
        if (afterUserInfo.startsWith('[')) return afterUserInfo.substringAfter('[').substringBefore(']').ifBlank { null }
        val host = afterUserInfo.substringBefore(':')
        return host.ifBlank { null }
    }

    private fun hostPart(text: String): String =
        text
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')

    /** Whether [url]'s host is a Tor onion service, which only resolves over Tor. */
    fun isOnion(url: String): Boolean = hostOf(url)?.endsWith(".onion", ignoreCase = true) == true

    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
    private val HEX = "0123456789ABCDEF".toCharArray()

    /** Percent-encodes [s] as a URL query component (UTF-8), so a multi-word search survives as a single param. */
    fun encodeQuery(s: String): String {
        val sb = StringBuilder(s.length)
        for (byte in s.encodeToByteArray()) {
            val c = byte.toInt() and 0xFF
            if (c < 128 && c.toChar() in UNRESERVED) {
                sb.append(c.toChar())
            } else {
                sb.append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
            }
        }
        return sb.toString()
    }
}
