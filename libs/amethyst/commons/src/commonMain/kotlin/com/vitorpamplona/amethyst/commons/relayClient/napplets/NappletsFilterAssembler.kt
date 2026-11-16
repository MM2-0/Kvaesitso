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
package com.vitorpamplona.amethyst.commons.relayClient.napplets

import com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds.SingleTopNavFeedSubAssembler
import com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds.TopNavFeedFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds.TopNavFeedQueryState
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient

/**
 * Subscribes to NIP-5D napplet manifests (kinds 15129/35129) while a napplet screen is open,
 * honoring the top-nav follow-list selection at the relay level (authors resolved to their outbox
 * relays) and dumping the results into the cache for the screen to observe. The key carries no
 * feed: the screen reads the manifests straight out of the cache. The only spinner options are
 * author-based, so [makeNappletsFilter] needs no tag-based branches.
 */
class NappletsFilterAssembler(
    client: INostrClient,
) : TopNavFeedFilterAssembler<TopNavFeedQueryState>({ keys -> listOf(SingleTopNavFeedSubAssembler(client, keys, ::makeNappletsFilter)) })
