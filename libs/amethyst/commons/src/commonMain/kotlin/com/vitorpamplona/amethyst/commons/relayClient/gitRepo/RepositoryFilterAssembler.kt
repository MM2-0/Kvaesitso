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
package com.vitorpamplona.amethyst.commons.relayClient.gitRepo

import com.vitorpamplona.amethyst.commons.gitRepo.dal.isSeriesRoot
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.MutableComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.MutableQueryState
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * A repository screen's subscription. [itemIds] tracks the repository's items in the cache, so the
 * subscription also asks for each one's statuses, labels and cover notes by id as the items arrive.
 */
class RepositoryQueryState(
    var repository: AddressableNote,
    itemIds: Flow<Set<HexKey>>,
) : MutableQueryState {
    /** The item ids [itemIds] last emitted, read when the filters are built. */
    val currentItemIds = MutableStateFlow<Set<HexKey>>(emptySet())

    private val trackedItemIds = itemIds.distinctUntilChanged().onEach { currentItemIds.value = it }

    override fun flow(): Flow<*> = trackedItemIds

    companion object {
        /**
         * The ids of [event]'s issues, PRs and series-root patches in [LocalCache]: the items a
         * repository screen lists, and so the ones whose state it has to know.
         */
        fun itemIdsOf(event: GitRepositoryEvent): Flow<Set<HexKey>> =
            LocalCache
                .observeEvents<Event>(Filter(kinds = RepositoryItemKinds, tags = mapOf("a" to repositoryCoordinates(event))))
                .map { items -> items.filter { it !is GitPatchEvent || it.isSeriesRoot() }.mapTo(HashSet()) { it.id } }
    }
}

class RepositoryFilterAssembler(
    cache: ICacheProvider,
    client: INostrClient,
    scope: CoroutineScope,
) : MutableComposeSubscriptionManager<RepositoryQueryState>(scope) {
    val group =
        listOf(
            RepositoryContentSubAssembler(cache, client, ::allKeys),
            RepositoryItemEssentialsSubAssembler(cache, client, ::allKeys),
        )

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}
