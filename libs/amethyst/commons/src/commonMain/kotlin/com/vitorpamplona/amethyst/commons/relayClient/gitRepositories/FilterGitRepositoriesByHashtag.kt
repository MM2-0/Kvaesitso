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
package com.vitorpamplona.amethyst.commons.relayClient.gitRepositories

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

fun filterGitRepositoriesByHashtag(
    relay: NormalizedRelayUrl,
    hashtags: Set<String>,
    since: Long? = null,
): List<RelayBasedFilter> =
    listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.TAG_FEED,
                    kinds = listOf(GitRepositoryEvent.KIND),
                    tags = mapOf("t" to hashtags.toList()),
                    limit = 200,
                    since = since,
                ),
        ),
    )

fun filterGitRepositoriesByHashtag(
    hashtagSet: HashtagTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (hashtagSet.set.isEmpty()) return emptyList()

    return hashtagSet.set
        .mapNotNull { relayHashSet ->
            if (relayHashSet.value.hashtags.isEmpty()) {
                null
            } else {
                filterGitRepositoriesByHashtag(
                    relay = relayHashSet.key,
                    hashtags = relayHashSet.value.hashtags,
                    since = since?.get(relayHashSet.key)?.time ?: defaultSince,
                )
            }
        }.flatten()
}
