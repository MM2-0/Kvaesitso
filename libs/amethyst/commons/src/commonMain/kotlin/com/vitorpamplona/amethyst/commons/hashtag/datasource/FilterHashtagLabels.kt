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
package com.vitorpamplona.amethyst.commons.hashtag.datasource

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.event.loaders.filterMissingEvents
import com.vitorpamplona.amethyst.commons.relayClient.event.loaders.potentialRelaysToFindEvent
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtagAlts
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.utils.mapOfSet
import kotlin.math.min

/**
 * Builds the relay filters that power the NIP-32 side of the hashtag feed:
 *
 * 1. A subscription for kind 1985 label events that tag posts with this hashtag (the `l`
 *    tag under the `#t` namespace), routed through the NIP-65 outbox model: each follow's
 *    labels are requested from THAT follow's outbox relays, with the relay's filter
 *    restricted to the follows that actually write there. This is what guarantees we pick
 *    up a follow's labels even if they never reach the hashtag's own relays.
 * 2. Requests for the underlying posts a *followed* user has labeled but that aren't in
 *    cache yet, so they can actually render in the feed. This rotates as new label
 *    events arrive (the sub-assembler re-runs after each EOSE).
 */
fun filterHashtagLabels(
    account: Account,
    hashtag: String,
    relays: Set<NormalizedRelayUrl>,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val labelValues = hashtagAlts(hashtag).sorted()

    // Outbox routing: relay -> the follows who publish to it.
    val followsPerRelay = account.followsPerRelay.value
    val labelFilters =
        followsPerRelay.mapNotNull { (relay, authors) ->
            if (authors.isEmpty()) return@mapNotNull null
            val authorList = authors.sorted()
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.TAG_FEED,
                        kinds = listOf(LabelEvent.KIND),
                        authors = authorList,
                        tags = mapOf("l" to labelValues),
                        limit = min(authorList.size * 5, 500),
                        since = since?.get(relay)?.time,
                    ),
            )
        }

    val follows = account.followingKeySet()

    // Collect the posts that a followed user labeled with this hashtag but that we don't
    // have the full event for yet, and ask their likely relays for them.
    val labelNotes =
        LocalCache.notes.filterIntoSet { _, note ->
            val noteEvent = note.event
            noteEvent is LabelEvent &&
                noteEvent.pubKey in follows &&
                hashtag in noteEvent.hashtagAssociations()
        }

    val missingTargets =
        mapOfSet {
            labelNotes.forEach { labelNote ->
                (labelNote.event as LabelEvent).labeledEvents().forEach { targetId ->
                    val target = LocalCache.getNoteIfExists(targetId)
                    if (target?.event == null) {
                        val targetNote = LocalCache.getOrCreateNote(targetId)
                        potentialRelaysToFindEvent(LocalCache, targetNote).ifEmpty { relays }.forEach { relayUrl ->
                            add(relayUrl, targetId)
                        }
                    }
                }
            }
        }

    return labelFilters + filterMissingEvents(missingTargets)
}
