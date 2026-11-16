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
package com.vitorpamplona.amethyst.commons.util

/**
 * True iff [url] is a syntactically valid absolute URL with a recognized
 * scheme. JVM/Android delegate to `java.net.URI(url).toURL()`; the iOS actual
 * will use `NSURL(string:)` when that target is added.
 *
 * Called per-URL during feed render — actuals should keep this fast and
 * allocation-light.
 */
expect fun isValidUrl(url: String?): Boolean

/**
 * True when [url] is an `http(s)://` link with something after the scheme: the only links a card
 * may preview or open. Event authors write whatever they like into a tag, and a `javascript:`,
 * `file:` or `intent:` value handed to a link opener is not a web page.
 */
fun isWebLink(url: String): Boolean {
    val trimmed = url.trim()
    val rest =
        when {
            trimmed.startsWith("https://", ignoreCase = true) -> 8
            trimmed.startsWith("http://", ignoreCase = true) -> 7
            else -> return false
        }
    return trimmed.length > rest
}
