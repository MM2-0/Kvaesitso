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
package com.vitorpamplona.amethyst.commons.relayClient.url

import com.vitorpamplona.amethyst.commons.relayClient.nip22Comments.CommentKinds
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilterPostsByUrlTest {
    @Test
    fun normalizesUrlAndQueriesNip22UrlScope() {
        val relay = NormalizedRelayUrl("wss://relay.example/")
        val filters = filterPostsByUrl("HTTPS://Example.com/path?b=2&a=1#section", setOf(relay), null)

        assertEquals(2, filters.size)
        assertEquals(relay, filters[0].relay)
        assertEquals(CommentKinds, filters[0].filter.kinds)
        assertEquals(mapOf("I" to listOf("https://example.com/path?b=2&a=1")), filters[0].filter.tags)
        assertEquals(100, filters[0].filter.limit)
    }

    @Test
    fun alsoQueriesHighlightsTakenFromThePage() {
        val relay = NormalizedRelayUrl("wss://relay.example/")
        val filters = filterPostsByUrl("https://medium.com/feenk/rewilding-software-engineering-083b86c81c43", setOf(relay), null)

        val highlights = filters.single { it.filter.kinds == HighlightKinds }
        assertEquals(relay, highlights.relay)
        assertEquals(
            mapOf(
                "r" to
                    listOf(
                        "https://medium.com/feenk/rewilding-software-engineering-083b86c81c43",
                        "https://medium.com/feenk/rewilding-software-engineering-083b86c81c43/",
                    ),
            ),
            highlights.filter.tags,
        )
    }

    @Test
    fun returnsNoFiltersForMalformedUrls() {
        val relay = NormalizedRelayUrl("wss://relay.example/")

        val filters = filterPostsByUrl("https://", setOf(relay), null)

        assertTrue(filters.isEmpty())
    }
}
