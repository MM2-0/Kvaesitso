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
package com.vitorpamplona.amethyst.commons.relayClient.assemblers

import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent

/**
 * Query state for reactions subscriptions.
 * Groups note IDs with their preferred relays.
 */
data class ReactionsQueryState(
    val noteIds: Set<HexKey>,
    val relays: Set<NormalizedRelayUrl>,
)

/**
 * Subscribes to Kind 7 (reactions) for a set of note IDs.
 * Used to load like counts, zap counts, and other reactions.
 *
 * This assembler:
 * - Batches multiple note ID requests into single subscription
 * - Uses e-tags to filter reactions for specific notes
 * - Caches EOSE to avoid re-fetching known reactions
 */
class ReactionsFilterAssembler(
    client: INostrClient,
    allKeys: () -> Set<ReactionsQueryState>,
) : SingleSubEoseManager<ReactionsQueryState>(client, allKeys, invalidateAfterEose = true) {
    override fun distinct(key: ReactionsQueryState): Any = key.noteIds

    override fun updateFilter(
        keys: List<ReactionsQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        // Collect all note IDs and relays from all query states
        val allNoteIds = mutableSetOf<HexKey>()
        val allRelays = mutableSetOf<NormalizedRelayUrl>()

        keys.forEach { state ->
            allNoteIds.addAll(state.noteIds)
            allRelays.addAll(state.relays)
        }

        if (allNoteIds.isEmpty() || allRelays.isEmpty()) return null

        val noteIdList = allNoteIds.toList()

        // Create filter for reactions (Kind 7) targeting these notes via e-tags
        val filter =
            ExplainedFilter(
                purpose = SubPurpose.ENGAGEMENT,
                purposeDetail = "reactions on notes currently on screen",
                kinds = listOf(ReactionEvent.KIND),
                tags = mapOf("e" to noteIdList),
            )

        // Apply since times per relay
        return allRelays.map { relay ->
            val sinceTime = since?.get(relay)?.time
            val filterWithSince =
                if (sinceTime != null) {
                    filter.copy(since = sinceTime)
                } else {
                    filter
                }
            RelayBasedFilter(relay, filterWithSince)
        }
    }
}
