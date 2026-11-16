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
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Query state for metadata subscriptions.
 * Groups pubkeys with their preferred index relays.
 */
data class MetadataQueryState(
    val pubkeys: Set<HexKey>,
    val indexRelays: Set<NormalizedRelayUrl>,
    /** Which account is looking. Null when the caller has no account context (kept out of attribution). */
    val accountPubKey: HexKey? = null,
)

/**
 * Subscribes to Kind 0 (user metadata) for a set of pubkeys.
 * Used to load display names, avatars, and other profile information.
 *
 * This assembler:
 * - Batches multiple pubkey requests into single subscription
 * - Sends requests to index relays for efficient discovery
 * - Caches EOSE to avoid re-fetching known metadata
 */
class MetadataFilterAssembler(
    client: INostrClient,
    allKeys: () -> Set<MetadataQueryState>,
) : SingleSubEoseManager<MetadataQueryState>(client, allKeys, invalidateAfterEose = true) {
    override fun distinct(key: MetadataQueryState): Any = key.pubkeys

    override fun updateFilter(
        keys: List<MetadataQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        // Collect all pubkeys and relays from all query states
        val allPubkeys = mutableSetOf<HexKey>()
        val allRelays = mutableSetOf<NormalizedRelayUrl>()

        keys.forEach { state ->
            allPubkeys.addAll(state.pubkeys)
            allRelays.addAll(state.indexRelays)
        }

        if (allPubkeys.isEmpty() || allRelays.isEmpty()) return null

        // Attributed only when one account is looking. These pubkeys are whoever is rendered rather
        // than anything an account owns, and several query states are batched into one filter, so with
        // more than one asker there is no single honest owner.
        val soleAccountPubKey = keys.mapNotNullTo(mutableSetOf()) { it.accountPubKey }.singleOrNull()

        val pubkeyList = allPubkeys.toList()

        // Create filter for metadata (Kind 0)
        val filter =
            ExplainedFilter(
                kinds = listOf(MetadataEvent.KIND),
                authors = pubkeyList,
                limit = pubkeyList.size,
                purpose = SubPurpose.PROFILE_METADATA,
                accountPubKeys = listOfNotNull(soleAccountPubKey),
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
