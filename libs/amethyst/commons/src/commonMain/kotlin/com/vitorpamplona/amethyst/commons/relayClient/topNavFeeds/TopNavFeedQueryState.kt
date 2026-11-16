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
package com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds

import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Key for a feed subscription driven by the top-nav selector: the list the user picked
 * ([listName]), that list resolved through the outbox model into per-relay author/tag sets
 * ([followsPerRelay]), and the feed(s) whose oldest loaded note bounds the REQ's `since`
 * ([feeds] — empty for screens that read the cache directly, such as nSites and nApplets).
 *
 * One key per open screen: several screens may show the same feed at once, each with its own key.
 * Watchers launched for the key run on [scope], the screen's scope.
 */
open class TopNavFeedQueryState(
    override val account: IAccount,
    val listName: StateFlow<TopFilter>,
    val followsPerRelay: StateFlow<IFeedTopNavPerRelayFilterSet>,
    val scope: CoroutineScope,
    val feeds: List<FeedContentState> = emptyList(),
) : AccountScopedQuery
