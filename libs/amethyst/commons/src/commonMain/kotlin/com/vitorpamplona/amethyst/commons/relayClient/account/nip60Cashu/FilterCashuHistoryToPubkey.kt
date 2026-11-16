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
package com.vitorpamplona.amethyst.commons.relayClient.account.nip60Cashu

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent

/**
 * One backward-paging page of the account's NIP-60 spending history (kind:7376) on one of its outbox
 * relays: my own history rows, strictly older than [until], newest-first, capped at [limit].
 *
 * Deliberately kind:7376 only. The proofs (kind:7375) are not paged on demand — a balance computed from
 * a partial proof set is simply wrong, so those are walked to exhaustion in one shot by
 * `CashuWalletState.resyncProofsFromRelays`. History is the opposite: it is display-only, unbounded in
 * length, and the user reads it newest-first, so it is exactly the shape `until`+`limit` paging is for.
 *
 * `until`+`limit` rather than a `since`/`until` window for the reason in `RelayLoadingCursors`: an empty
 * time slice cannot distinguish "nothing older here" from "a quiet month", whereas an empty
 * `until`+`limit` page is gap-proof proof of the bottom.
 */
fun filterCashuHistoryToPubkey(
    relay: NormalizedRelayUrl,
    pubkey: HexKey?,
    until: Long,
    limit: Int,
): List<RelayBasedFilter> {
    if (pubkey.isNullOrEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.WALLET,
                    accountPubKeys = listOfNotNull(pubkey),
                    kinds = listOf(CashuSpendingHistoryEvent.KIND),
                    authors = listOf(pubkey),
                    limit = limit,
                    until = until,
                ),
        ),
    )
}
