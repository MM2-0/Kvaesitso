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

import platform.Foundation.NSURL

// Schemes that JVM's URI.toURL() requires a host for (network schemes).
// "http:" without a host throws MalformedURLException on JVM; NSURL accepts
// it. Explicitly reject to keep behavior aligned.
private val HOST_REQUIRED_SCHEMES = setOf("http", "https", "ws", "wss", "ftp")

actual fun isValidUrl(url: String?): Boolean {
    if (url == null) return false
    // NSURL.URLWithString returns null for syntactically invalid URLs. It is
    // more permissive than JVM's URI(url).toURL() — it accepts scheme-less
    // relative URLs ("foo", "/bar") and scheme-only inputs ("http:"). Match
    // the JVM contract (absolute URL with a known scheme and, for network
    // schemes, a host).
    val nsUrl = NSURL.URLWithString(url) ?: return false
    val scheme = nsUrl.scheme?.lowercase() ?: return false
    if (scheme.isEmpty()) return false
    if (scheme in HOST_REQUIRED_SCHEMES && nsUrl.host.isNullOrEmpty()) return false
    return true
}
