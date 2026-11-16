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
package com.vitorpamplona.amethyst.commons.relayClient.nests

import com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds.SingleTopNavFeedSubAssembler
import com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds.TopNavFeedFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds.TopNavFeedQueryState
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient

/**
 * [makeNestsFilter] reuses the live-activities wire filter (kinds 30311/30312/30313/1311); the feed
 * filter narrows to 30312/30313 client-side. Sharing the wire filter avoids duplicate REQs when
 * both the Live Streams and Audio Rooms screens are open for the same user.
 */
class NestsFilterAssembler(
    client: INostrClient,
) : TopNavFeedFilterAssembler<TopNavFeedQueryState>({ keys -> listOf(SingleTopNavFeedSubAssembler(client, keys, ::makeNestsFilter)) })
