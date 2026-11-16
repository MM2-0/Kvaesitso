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
package com.vitorpamplona.amethyst.commons.relayClient.event

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.event.loaders.AddressableAuthorRelayLoaderSubAssembler
import com.vitorpamplona.amethyst.commons.relayClient.event.loaders.NoteEventLoaderSubAssembler
import com.vitorpamplona.amethyst.commons.relayClient.event.watchers.EventWatcherSubAssembler
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderAccount
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderFilterAssembler
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient

// This allows multiple screen to be listening to tags, even the same tag
@Stable
class EventFinderQueryState(
    val note: Note,
    val account: UserFinderAccount,
)

@Stable
class EventFinderFilterAssembler(
    client: INostrClient,
    cache: ICacheProvider,
    userFinder: UserFinderFilterAssembler,
) : ComposeSubscriptionManager<EventFinderQueryState>() {
    val group =
        listOf(
            NoteEventLoaderSubAssembler(client, cache, ::allKeys),
            EventWatcherSubAssembler(client, ::allKeys),
            AddressableAuthorRelayLoaderSubAssembler(cache, ::allKeys, userFinder),
        )

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun invalidateKeys() = invalidateFilters()

    override fun destroy() = group.forEach { it.destroy() }
}
