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
package com.vitorpamplona.amethyst.commons.relayClient.event.loaders

import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubNoEoseCacheEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderQueryState
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient

class NoteEventLoaderSubAssembler(
    client: INostrClient,
    val cache: ICacheProvider,
    allKeys: () -> Set<EventFinderQueryState>,
) : SingleSubNoEoseCacheEoseManager<EventFinderQueryState>(client, allKeys, invalidateAfterEose = true) {
    override fun updateFilter(keys: List<EventFinderQueryState>) =
        listOfNotNull(
            filterMissingEvents(cache, keys),
            filterMissingAddressables(cache, keys),
        ).flatten()

    override fun distinct(key: EventFinderQueryState) = key.note

    // Attribute to the account that owns this subscription, when a single account is watching.
    // Deduped by pubkey hex, not by account identity, so two objects for the same logged-in user
    // don't look like two accounts and suppress attribution.
    override fun accountPubKeyOf(key: Any?): String? = (key as? EventFinderQueryState)?.account?.userFinderPubkeyHex
}
