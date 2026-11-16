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
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent

/**
 * Query state for the NIP-60 / NIP-61 wallet subscription.
 *
 * `pubkey` is the wallet owner — used both as `authors=` for their own
 * NIP-60 events and as the `#p` tag value for inbound nutzaps.
 *
 * The two filters read from different relay sets, following the NIP-65
 * outbox model:
 *  - [ownEventRelays] — the user's own write/outbox relays, where they
 *    published their NIP-60 wallet/token/history events. Restoring those
 *    means reading from where they were written.
 *  - [inboxRelays] — where *other* people deliver kind:9321 nutzaps to this
 *    user. Per NIP-61 the source of truth is the `relay` tags in the user's
 *    own kind:10019; in practice we listen on the union of those plus the
 *    user's NIP-65 inbox + DM relays so a nutzap can't slip past us.
 */
@Immutable
data class CashuWalletQueryState(
    val pubkey: HexKey,
    val ownEventRelays: Set<NormalizedRelayUrl>,
    val inboxRelays: Set<NormalizedRelayUrl>,
)

/**
 * Every NIP-60 / NIP-61 filter for one account's Cashu wallet:
 *
 *  - kind 17375 — the wallet event (replaceable)
 *  - kind 7375  — unspent proofs (token events)
 *  - kind 7376  — spending history
 *  - kind 7374  — quote state events
 *  - kind 10019 — nutzap info (replaceable, for incoming nutzaps)
 *  - kind 9321  — inbound nutzaps tagged with the user's pubkey
 *
 * Authored events are queried by `authors=[pubkey]`; nutzaps by `#p=[pubkey]`, since we receive
 * those rather than send them.
 *
 * A plain function rather than a subscription manager: the wallet's lifetime belongs to the account,
 * so it is mounted by the account-level assembler alongside notifications, DMs and NWC, and this only
 * has to describe the query.
 */
fun cashuWalletFilters(
    key: CashuWalletQueryState,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val pubkey = key.pubkey
    val ownEventRelays = key.ownEventRelays
    val inboxRelays = key.inboxRelays
    if (ownEventRelays.isEmpty() && inboxRelays.isEmpty()) return emptyList()

    val ownedFilter =
        ExplainedFilter(
            purpose = SubPurpose.WALLET,
            kinds =
                listOf(
                    CashuWalletEvent.KIND,
                    CashuTokenEvent.KIND,
                    CashuSpendingHistoryEvent.KIND,
                    CashuMintQuoteEvent.KIND,
                    NutzapInfoEvent.KIND,
                    // NIP-87 mint recommendations the user has published.
                    // Pulled here (instead of relying on the general
                    // account filter) so the Cashu Settings screen can
                    // list and retract them without any extra subscription.
                    MintRecommendationEvent.KIND,
                ),
            authors = listOf(pubkey),
            accountPubKeys = listOfNotNull(pubkey),
        )

    val inboundNutzapsFilter =
        ExplainedFilter(
            purpose = SubPurpose.NUTZAP_INBOX,
            kinds = listOf(NutzapEvent.KIND),
            tags = mapOf("p" to listOf(pubkey)),
            accountPubKeys = listOfNotNull(pubkey),
        )

    // Own NIP-60 events are read from the user's outbox; inbound nutzaps
    // from the user's inbox set. A relay that appears in both gets both
    // filters.
    val ownedSubs =
        ownEventRelays.map { relay ->
            val sinceTime = since?.get(relay)?.time
            RelayBasedFilter(
                relay,
                if (sinceTime != null) ownedFilter.copy(since = sinceTime) else ownedFilter,
            )
        }
    val inboundSubs =
        inboxRelays.map { relay ->
            val sinceTime = since?.get(relay)?.time
            RelayBasedFilter(
                relay,
                if (sinceTime != null) inboundNutzapsFilter.copy(since = sinceTime) else inboundNutzapsFilter,
            )
        }

    return ownedSubs + inboundSubs
}

/**
 * The filter used to **page** the account's whole proof set back from a relay,
 * as opposed to [cashuWalletFilters], which opens a live subscription.
 *
 * The live subscription sends one REQ with no `limit` and takes whatever the
 * relay decides to give back. Relays cap an unbounded REQ (NIP-11
 * `limitation.max_limit`, or a hard-coded default) and serve the **newest**
 * events within that cap, so a wallet whose kind:7375 events are outnumbered
 * by its kind:7376 history — which is every wallet after a few hundred
 * transactions — silently receives only a suffix of its proofs. Everything
 * downstream (balance, per-mint balances, coin selection) is a pure function
 * of that suffix, which is why two devices on the same account can show two
 * different balances and neither is right.
 *
 * There is no way to detect the truncation from the REQ itself: a capped
 * response and a complete one both just EOSE. The only fix is to not rely on
 * one REQ — hand this to `fetchAllPages` / `fetchAllPagesFromPool`, which
 * walks `until` cursors until a page comes back empty and thereby reaches
 * events of any age regardless of the cap.
 *
 * Scoped to kind:7375 alone. Those are the events that carry money; history,
 * quotes and recommendations are display-only, and paging them too would
 * multiply the download for a wallet with a long history without changing a
 * single balance. A caller that wants the rest — a headless client with no
 * scrolling list to page for it — asks for [cashuOwnEventBackfillFilters].
 */
fun cashuProofBackfillFilters(pubkey: HexKey): List<Filter> = cashuOwnEventBackfillFilters(pubkey, listOf(CashuTokenEvent.KIND))

/**
 * Every NIP-60/87 kind this account authors, for a paged walk over the relays it
 * publishes to. The read-side twin of the `authors=` half of [cashuWalletFilters],
 * minus the live subscription's cap exposure.
 */
val OWN_CASHU_KINDS =
    listOf(
        CashuWalletEvent.KIND,
        CashuTokenEvent.KIND,
        CashuSpendingHistoryEvent.KIND,
        CashuMintQuoteEvent.KIND,
        NutzapInfoEvent.KIND,
        MintRecommendationEvent.KIND,
    )

/**
 * A paged backfill of the account's **own** NIP-60/87 events, over the relays it
 * publishes to. Defaults to every kind it authors; pass a narrower [kinds] to
 * page only part of it (see [cashuProofBackfillFilters]).
 *
 * Hand this to `fetchAllPages` / `fetchAllPagesFromPool`, never to a plain REQ:
 * the whole point is walking `until` cursors past the relay's cap, which is what
 * silently truncates the single uncapped REQ [cashuWalletFilters] opens.
 */
fun cashuOwnEventBackfillFilters(
    pubkey: HexKey,
    kinds: List<Int> = OWN_CASHU_KINDS,
): List<Filter> =
    listOf(
        ExplainedFilter(
            kinds = kinds,
            authors = listOf(pubkey),
            purpose = SubPurpose.WALLET,
        ),
    )

/**
 * A paged backfill of inbound NIP-61 nutzaps (kind:9321) addressed to this
 * account, matched by the recipient `#p` tag because someone else authored them.
 * Read from the account's inbox set, mirroring the split in [cashuWalletFilters].
 */
fun cashuInboundNutzapBackfillFilters(pubkey: HexKey): List<Filter> =
    listOf(
        ExplainedFilter(
            kinds = listOf(NutzapEvent.KIND),
            tags = mapOf("p" to listOf(pubkey)),
            purpose = SubPurpose.NUTZAP_INBOX,
        ),
    )
