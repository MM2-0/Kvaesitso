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

import java.net.URI

actual fun absoluteUrlHost(url: String): String? = runCatching { URI(url).toURL().host }.getOrNull()

actual fun resolveHttpUrl(
    base: String?,
    reference: String,
): String? =
    runCatching {
        val baseUri = base?.let { runCatching { URI(it).toURL().toURI().withRootPath() }.getOrNull() }
        val resolved = if (baseUri != null) baseUri.resolve(reference) else URI(reference)
        if (!resolved.scheme.equals("http", ignoreCase = true) &&
            !resolved.scheme.equals("https", ignoreCase = true)
        ) {
            null
        } else {
            resolved.toURL().toString()
        }
    }.getOrNull()

/**
 * `https://host` as `https://host/`. RFC 3986 resolves a relative reference against an empty base path
 * as if the path were `/`, and the JDK does, but Android's `java.net.URI` does not: it glued
 * `y18.svg` onto `https://news.ycombinator.com` as `https://news.ycombinator.comy18.svg`, so the
 * page's icon (or a relative og:image) pointed at a host that does not exist.
 */
private fun URI.withRootPath(): URI =
    if (isOpaque || !rawPath.isNullOrEmpty() || rawAuthority == null) {
        this
    } else {
        // From the raw (still-encoded) parts: the multi-argument constructor would encode them again.
        URI(
            buildString {
                append(scheme).append("://").append(rawAuthority).append('/')
                rawQuery?.let { append('?').append(it) }
                rawFragment?.let { append('#').append(it) }
            },
        )
    }
