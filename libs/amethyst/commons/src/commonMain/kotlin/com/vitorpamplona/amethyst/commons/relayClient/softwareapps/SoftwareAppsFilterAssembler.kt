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
package com.vitorpamplona.amethyst.commons.relayClient.softwareapps

import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds.TopNavFeedFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds.TopNavFeedQueryState
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Key for the Apps feed. Besides the usual top-nav inputs it carries the user's blocked-relay set:
 * the Global selection queries zapstore's relay directly, and must stop when the user blocks it.
 */
class SoftwareAppsQueryState(
    account: IAccount,
    listName: StateFlow<TopFilter>,
    followsPerRelay: StateFlow<IFeedTopNavPerRelayFilterSet>,
    scope: CoroutineScope,
    feed: FeedContentState,
    val blockedRelays: StateFlow<Set<NormalizedRelayUrl>>,
) : TopNavFeedQueryState(account, listName, followsPerRelay, scope, listOf(feed))

class SoftwareAppsFilterAssembler(
    client: INostrClient,
) : TopNavFeedFilterAssembler<SoftwareAppsQueryState>({ keys -> listOf(SoftwareAppsSubAssembler(client, keys)) })
