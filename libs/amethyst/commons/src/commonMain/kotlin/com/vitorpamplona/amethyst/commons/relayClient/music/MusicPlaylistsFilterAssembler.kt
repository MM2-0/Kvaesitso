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
package com.vitorpamplona.amethyst.commons.relayClient.music

import com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds.SingleTopNavFeedSubAssembler
import com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds.TopNavFeedFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds.TopNavFeedQueryState
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient

/**
 * Keyed on the user's *playlists* follow list, not the tracks one: the playlists screen has its own
 * top-bar spinner, and reusing the tracks subscription left the playlists feed empty whenever the
 * two selections differed. Asks for kind 34139 only; tracks referenced by a playlist are loaded on
 * demand by each playlist row's own per-track observer.
 */
class MusicPlaylistsFilterAssembler(
    client: INostrClient,
) : TopNavFeedFilterAssembler<TopNavFeedQueryState>({ keys -> listOf(SingleTopNavFeedSubAssembler(client, keys, ::makeMusicPlaylistsFilter)) })
