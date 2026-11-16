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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip87Ecash.cashu.CashuMintEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent

/**
 * Query state for the NIP-87 cashu mint directory subscription.
 *
 * The picker UI feeds [relays] from the user's NIP-65 outbox + indexer
 * relays — those are where mint announcements (kind 38172) and
 * recommendations (kind 38000) tend to be published. Switching `relays`
 * mid-session is supported: the assembler tears down the old subscription
 * and stands up a new one keyed by the relay set.
 */
@Immutable
data class CashuMintDirectoryQueryState(
    val relays: Set<NormalizedRelayUrl>,
)

/**
 * Subscribes to NIP-87 cashu mint announcements (kind 38172) and the
 * cashu-scoped recommendations (kind 38000 with `#k=["38172"]`).
 *
 * Fedimint announcements (kind 38173) are intentionally excluded — this
 * assembler only feeds the Cashu mint picker.
 */
@Stable
class CashuMintDirectoryFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<CashuMintDirectoryQueryState>() {
    private val sub = CashuMintDirectorySubAssembler(client, ::allKeys)

    override fun invalidateFilters() = sub.invalidateFilters()

    override fun invalidateKeys() = invalidateFilters()

    override fun destroy() = sub.destroy()
}

private class CashuMintDirectorySubAssembler(
    client: INostrClient,
    allKeys: () -> Set<CashuMintDirectoryQueryState>,
) : SingleSubEoseManager<CashuMintDirectoryQueryState>(client, allKeys, invalidateAfterEose = true) {
    override fun distinct(key: CashuMintDirectoryQueryState): Any = key.relays.hashCode()

    override fun updateFilter(
        keys: List<CashuMintDirectoryQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val relays =
            keys
                .flatMap { it.relays }
                .toSet()
                .ifEmpty { return null }

        val mintAnnouncements =
            ExplainedFilter(
                purpose = SubPurpose.MINT_DIRECTORY,
                purposeDetail = "mint directory",
                kinds = listOf(CashuMintEvent.KIND),
            )

        // NIP-87 recommendations are kind 38000 with a `k` tag holding the
        // recommended event's kind as a string. We only want cashu mint
        // recommendations here.
        val cashuRecommendations =
            ExplainedFilter(
                purpose = SubPurpose.MINT_DIRECTORY,
                purposeDetail = "mint directory",
                kinds = listOf(MintRecommendationEvent.KIND),
                tags = mapOf("k" to listOf(CashuMintEvent.KIND.toString())),
            )

        return relays.flatMap { relay ->
            val sinceTime = since?.get(relay)?.time
            listOf(
                RelayBasedFilter(
                    relay,
                    if (sinceTime != null) mintAnnouncements.copy(since = sinceTime) else mintAnnouncements,
                ),
                RelayBasedFilter(
                    relay,
                    if (sinceTime != null) cashuRecommendations.copy(since = sinceTime) else cashuRecommendations,
                ),
            )
        }
    }
}
