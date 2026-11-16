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
package com.vitorpamplona.amethyst.commons.relayClient.search

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.search.QueryParser
import com.vitorpamplona.amethyst.commons.search.RenderableKinds
import com.vitorpamplona.amethyst.commons.search.SearchPipeline
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * The REQs a search sends, from the text the reader typed.
 *
 * The text is parsed first rather than handed to the relay whole: `from:`, `to:`, `since:`,
 * `#tag`, `label:`, `group:` and the NIP-73 scopes become NIP-01 filter fields, and only what is
 * left over travels as the NIP-50 `search` string. Before this the entire box — chips and all —
 * went into `search`, so a query like `from:npub1… bitcoin` asked relays for the literal text of
 * its own tokens and matched nothing.
 *
 * A `kind:` token names the window itself, and is asked as the one group it says. Only a query
 * that names no kind falls back to [RenderableKinds] — the kinds Amethyst can put on screen,
 * which is more than most relays accept in one filter, so it is asked in groups and the union
 * merged client-side. That list used to be spelled out here, and separately in desktop's filter
 * factory, and the two had already drifted apart.
 */
fun searchPostsByText(
    searchString: String,
    relay: NormalizedRelayUrl,
): List<RelayBasedFilter> {
    val query = QueryParser.parse(searchString)
    if (query.isEmpty) return emptyList()

    // One group when the query names its own window — SearchPipeline.filters lets a `kind:` win
    // over the caller's fallback, so passing the groups here would be asking for a narrowing the
    // pipeline has already decided against.
    val kindGroups = if (query.kinds.isNotEmpty()) listOf(null) else RenderableKinds.GROUPS

    return kindGroups.flatMap { kinds ->
        SearchPipeline.filters(query, kinds, limit = 100).map { filter ->
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.SEARCH,
                        kinds = filter.kinds,
                        authors = filter.authors,
                        tags = filter.tags,
                        since = filter.since,
                        until = filter.until,
                        limit = filter.limit,
                        search = filter.search,
                    ),
            )
        }
    }
}
