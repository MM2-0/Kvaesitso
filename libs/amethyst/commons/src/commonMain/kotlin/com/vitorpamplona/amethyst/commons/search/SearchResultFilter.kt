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
package com.vitorpamplona.amethyst.commons.search

import com.vitorpamplona.quartz.nip01Core.core.Event

object SearchResultFilter {
    /**
     * Does this event survive the parts of [query] no relay can be asked about?
     *
     * `-term` and the `kind:reply`/`kind:media` pseudo-kinds are the query's post-filters: NIP-50
     * has no negation operator, and "is a reply" is a shape of the event's tags rather than
     * anything a relay indexes. So they are applied here, over whatever came back — which means
     * every front end has to remember to call this. One did not, and both features silently did
     * nothing there; hence this predicate, so a caller with its own list type can apply the same
     * rules without going through [filter].
     */
    fun matches(
        event: Event,
        query: SearchQuery,
    ): Boolean {
        if (query.excludeTerms.any { event.content.contains(it, ignoreCase = true) }) return false
        if ("reply" in query.pseudoKinds && !isReply(event)) return false
        if ("media" in query.pseudoKinds && !isMedia(event)) return false
        return true
    }

    fun filter(
        events: List<Event>,
        query: SearchQuery,
    ): List<Event> =
        events
            .distinctBy { it.id }
            .filter { matches(it, query) }
            .sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id })

    fun isReply(event: Event): Boolean = event.kind == 1 && event.tags.any { it.size >= 2 && it[0] == "e" }

    fun isMedia(event: Event): Boolean {
        if (event.kind != 1) return false
        // Check for imeta tag
        if (event.tags.any { it.size >= 2 && it[0] == "imeta" }) return true
        // Check for image/video URLs in content
        return IMAGE_URL_PATTERN.containsMatchIn(event.content)
    }

    private val IMAGE_URL_PATTERN =
        Regex(
            """https?://\S+\.(jpg|jpeg|png|gif|webp|svg|mp4|webm|mov)""",
            RegexOption.IGNORE_CASE,
        )
}
