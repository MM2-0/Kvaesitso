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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds.mine

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedFlowsType
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * Resolves the "Mine" top-nav selection to an author filter scoped to the logged-in user's own
 * pubkey, pinned to the user's own relays — their outbox, private-storage, local and proxy relays
 * (see [com.vitorpamplona.amethyst.commons.model.nip01UserMetadata.AccountMineRelayState]). Unlike the
 * follow filters, "Mine" doesn't need per-author outbox resolution from cache: the only author is
 * the user, and the user's relays are already known from their own account state — so we pin the
 * fixed set directly via [AuthorsByProxyTopNavFilter] (it associates each given relay with the
 * authors, which is exactly "query my relays for my events").
 *
 * This is the single source of truth for "Mine". Both the relay sub-assemblers (through each
 * screen's `liveXFollowListsPerRelay`) and the local DAL filters (through `liveXFollowLists`)
 * consume the produced [AuthorsByProxyTopNavFilter], so screens no longer need a dedicated
 * `TopFilter.Mine` branch: the generic author path already narrows to the user. It also makes
 * relay-set changes re-invalidate automatically — `liveXFollowListsPerRelay` re-emits whenever
 * [mineRelays] changes, through the sub-assemblers' existing `followsPerRelayFlow` collector.
 */
class MineFeedFlow(
    val myPubkey: HexKey,
    val mineRelays: StateFlow<Set<NormalizedRelayUrl>>,
) : IFeedFlowsType {
    fun convert(relays: Set<NormalizedRelayUrl>): IFeedTopNavFilter =
        AuthorsByProxyTopNavFilter(
            authors = setOf(myPubkey),
            proxyRelays = relays,
        )

    override fun flow(): Flow<IFeedTopNavFilter> = mineRelays.map(::convert)

    override fun startValue(): IFeedTopNavFilter = convert(mineRelays.value)

    override suspend fun startValue(collector: FlowCollector<IFeedTopNavFilter>) {
        collector.emit(startValue())
    }
}
