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
package com.vitorpamplona.amethyst.commons.relayClient.nip17Dm

import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

/** What the account's gift-wrap subscription asks its DM relays for, by which features are on. */
enum class GiftWrapInbox {
    /** Every wrap to us, kinds 1059 + 21059: NIP-17 DMs, NIP-AC calls, Marmot Welcomes, Concord invites. */
    EVERYTHING,

    /** Kind-1059 wraps to us, for Marmot Welcomes (NIP-17 DMs among them are unwrapped but kept out of rooms). */
    MARMOT_WELCOMES,

    /** Only the `k=3313` Concord Direct Invite wraps. */
    CONCORD_INVITES,

    /** Nothing on the DM relays. */
    NONE,
    ;

    companion object {
        /** NIP-17 needs everything; otherwise the narrowest filter that still serves what is on. */
        fun choose(
            nip17: Boolean,
            marmot: Boolean,
            concord: Boolean,
        ): GiftWrapInbox =
            when {
                nip17 -> EVERYTHING
                marmot -> MARMOT_WELCOMES
                concord -> CONCORD_INVITES
                else -> NONE
            }
    }
}

/**
 * Marmot Welcomes (MIP-02) for an account that has the NIP-17 inbox turned off: kind-1059 wraps to
 * [pubkey], without the kind-21059 ephemeral wraps (calls) the NIP-17 filter adds. A Welcome wrap
 * carries no tag that says what it holds, so this still downloads the NIP-17 DMs addressed to us and
 * unwraps them (two decryptions each) into the cache, like with NIP-17 on; only the rooms and
 * notifications skip them. That is the cost of reading Welcomes; it also lets turning NIP-17 back on
 * re-index them without a new download.
 */
fun filterMarmotWelcomesToPubkey(
    relay: NormalizedRelayUrl,
    pubkey: HexKey?,
    since: Long?,
): List<RelayBasedFilter> =
    filterGiftWrapsToPubkey(
        relay = relay,
        pubkey = pubkey,
        since = since,
        kinds = listOf(GiftWrapEvent.KIND),
        purpose = SubPurpose.ENCRYPTED_GROUPS,
        purposeDetail = "Marmot welcomes",
    )

/**
 * Concord Direct Invites (CORD-05 §6): kind-1059 wraps to [pubkey] tagged `k=3313`, from [since]
 * (the invite inbox's cursor, already rewound by the wrap backdate window; null = all of them).
 */
fun filterConcordDirectInvitesToPubkey(
    relay: NormalizedRelayUrl,
    pubkey: HexKey?,
    since: Long?,
): List<RelayBasedFilter> {
    if (pubkey.isNullOrEmpty()) return emptyList()
    val filter = ConcordActions.directInvitesFilter(pubkey, since)
    return listOf(RelayBasedFilter(relay, ExplainedFilter.of(filter, SubPurpose.COMMUNITY_CHATS, "Concord direct invites")))
}
