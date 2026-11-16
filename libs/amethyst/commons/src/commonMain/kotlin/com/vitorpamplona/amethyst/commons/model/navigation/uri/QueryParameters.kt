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
package com.vitorpamplona.amethyst.commons.model.navigation.uri

/**
 * The value of [parameterName] in this URI's query string, without going through
 * `java.net.URI`.
 *
 * Needed because `java.net.URI` only exposes `rawQuery` for *hierarchical* URIs. A URI
 * with a scheme and no `//` is **opaque** — everything after the colon is one
 * scheme-specific part — so `URI("marmot:<hex>?account=npub1…").rawQuery` is null. That
 * is the shape `NotificationRoutes.marmotUri`
 * produces, so every Marmot group notification silently lost its `?account=` and opened
 * the group under whichever account happened to be current instead of switching first.
 *
 * Splitting on the first `?` gets the same answer for both shapes, and returns null for a
 * bare `nevent1…` with no query at all. A `#fragment` is dropped; a value may itself contain `=`.
 *
 * `UriParser` reads an opaque query correctly too, and is the
 * right tool when a URI is already known to be well-formed. It is not this one: it builds a
 * `java.net.URI`, which *throws* on anything that is not a legal URI. What arrives here comes
 * from an exported, browsable scheme, so it can be any string at all, and every caller on the
 * deep-link path treats an unreadable uri as "no route" rather than as a crash.
 */
fun String.findQueryParameterValue(parameterName: String): String? {
    // A raw `#` starts the fragment, which is not part of the query (as `java.net.URI` read it).
    val query = substringBefore('#').substringAfter('?', "")
    if (query.isEmpty()) return null

    return query
        .split('&')
        .firstOrNull { it.substringBefore('=') == parameterName }
        ?.substringAfter('=', "")
        ?.ifEmpty { null }
}
