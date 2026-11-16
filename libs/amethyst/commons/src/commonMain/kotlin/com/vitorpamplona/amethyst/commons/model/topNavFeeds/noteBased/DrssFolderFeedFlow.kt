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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.drss.DrssTree
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedFlowsType
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.quartz.drss.feedList.DrssFeedListEvent
import com.vitorpamplona.quartz.drss.listFolder.DrssListFolderEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * A drss.io river: everyone followed by the follow sets, starter packs and feed lists under a list
 * folder ([root], kind 30101), at any depth. The tree is read again whenever a list of those kinds
 * arrives, so an edit anywhere in it, or a child that loads late, changes the feed.
 */
class DrssFolderFeedFlow(
    val root: Address,
    val outboxRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val blockedRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val proxyRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val cache: EventCache = LocalCache,
) : IFeedFlowsType {
    private fun authors(): Set<HexKey> = DrssTree.authorsUnder(root, cache)

    private val authorsFlow: Flow<Set<HexKey>> =
        cache.live.newEventBundles
            .filter { notes -> notes.any { it is AddressableNote && it.address.kind in TREE_KINDS } }
            .map { authors() }
            .onStart { emit(authors()) }
            .distinctUntilChanged()

    private fun filterFor(
        authors: Set<HexKey>,
        proxyRelays: Set<NormalizedRelayUrl>,
    ): IFeedTopNavFilter =
        if (proxyRelays.isEmpty()) {
            AuthorsByOutboxTopNavFilter(authors, blockedRelays)
        } else {
            AuthorsByProxyTopNavFilter(authors, proxyRelays)
        }

    override fun flow(): Flow<IFeedTopNavFilter> = combine(authorsFlow, outboxRelays, proxyRelays) { authors, _, proxy -> filterFor(authors, proxy) }

    override fun startValue(): IFeedTopNavFilter = filterFor(authors(), proxyRelays.value)

    override suspend fun startValue(collector: FlowCollector<IFeedTopNavFilter>) = collector.emit(startValue())

    companion object {
        private val TREE_KINDS = setOf(DrssListFolderEvent.KIND, DrssFeedListEvent.KIND, FollowSetEvent.KIND, StarterPackEvent.KIND)
    }
}
