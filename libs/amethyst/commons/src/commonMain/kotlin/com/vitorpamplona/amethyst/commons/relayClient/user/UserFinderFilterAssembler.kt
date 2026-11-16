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
package com.vitorpamplona.amethyst.commons.relayClient.user

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.user.loaders.UserOutboxFinderSubAssembler
import com.vitorpamplona.amethyst.commons.relayClient.user.watchers.UserCardsSubAssembler
import com.vitorpamplona.amethyst.commons.relayClient.user.watchers.UserReportsSubAssembler
import com.vitorpamplona.amethyst.commons.relayClient.user.watchers.UserWatcherSubAssembler
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker

// This allows multiple screen to be listening to tags, even the same tag
@Stable
class UserFinderQueryState(
    val user: User,
    val account: UserFinderAccount,
    /**
     * True when [user] is watched because the selected feed reads their notes (a member of the list
     * picked in the top bar), not because they are drawn on screen. Such a user gets the relay list
     * and the live metadata the rest of the app reads, but not the reports and contact cards a
     * profile or note card shows: a list can hold thousands of members, few of them ever drawn.
     */
    val isFeedAuthor: Boolean = false,
)

@Stable
class UserFinderFilterAssembler(
    client: INostrClient,
    cache: ICacheProvider,
    failureTracker: RelayOfflineTracker,
) : ComposeSubscriptionManager<UserFinderQueryState>() {
    val group =
        listOf(
            UserOutboxFinderSubAssembler(client, cache, failureTracker, ::allKeys),
            UserWatcherSubAssembler(client, cache, failureTracker, ::allKeys),
            UserReportsSubAssembler(client, cache, ::onScreenKeys),
            UserCardsSubAssembler(client, cache, ::onScreenKeys),
        )

    /** The users drawn on screen, without the authors a feed only reads: see [UserFinderQueryState.isFeedAuthor]. */
    private fun onScreenKeys() = allKeys().filterNotTo(mutableSetOf()) { it.isFeedAuthor }

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun invalidateKeys() = invalidateFilters()

    override fun destroy() = group.forEach { it.destroy() }
}
