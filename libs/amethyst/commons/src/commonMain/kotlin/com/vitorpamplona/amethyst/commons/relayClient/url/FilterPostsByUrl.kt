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
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip73ExternalIds.urls.UrlId
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent

/**
 * Everything written about one web page: NIP-22 comments scoped to it (`I`) and NIP-84
 * highlights taken from it (`r`). Highlights carry the page's address as their author wrote it,
 * and relays match tags exactly, so the `r` query also asks for the trailing-slash twin.
 */
fun filterPostsByUrl(
    url: String,
    relays: Set<NormalizedRelayUrl>,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val normalizedUrl = UrlId.toScopeOrNull(url) ?: return emptyList()
    val urlScopeMap = mapOf("I" to listOf(normalizedUrl))
    val highlightSourceMap = mapOf("r" to highlightReferenceVariants(normalizedUrl))

    return relays.flatMap { relay ->
        val since = since?.get(relay)?.time
        listOf(
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.OTHER,
                        tags = urlScopeMap,
                        kinds = CommentKinds,
                        limit = 100,
                        since = since,
                    ),
            ),
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.OTHER,
                        tags = highlightSourceMap,
                        kinds = HighlightKinds,
                        limit = 100,
                        since = since,
                    ),
            ),
        )
    }
}

val HighlightKinds = listOf(HighlightEvent.KIND)

/** [normalizedUrl] as a highlight's `r` tag may spell it: with and without a trailing slash. */
fun highlightReferenceVariants(normalizedUrl: String): List<String> {
    if (normalizedUrl.contains('?')) return listOf(normalizedUrl)
    val bare = normalizedUrl.trimEnd('/')
    return listOf(normalizedUrl, if (normalizedUrl.endsWith('/')) bare else "$bare/").distinct()
}
