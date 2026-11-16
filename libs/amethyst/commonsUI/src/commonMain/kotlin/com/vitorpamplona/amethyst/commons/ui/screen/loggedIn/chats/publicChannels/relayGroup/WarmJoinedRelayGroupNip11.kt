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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.WarmNip11
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * Eagerly warms the NIP-11 documents of the host relays of every group the user has joined, in
 * parallel. The NIP-29 relay-signed check ([com.vitorpamplona.amethyst.model.nip11RelayInfo.isRelaySignedRelayGroup])
 * reads those docs from cache, so pre-warming them from a primary tab (Messages) means the moment
 * one of these groups also surfaces in discovery — or any relay-signed gate runs — the answer is a
 * cache hit rather than a cold fetch. Cheap: the cache dedups and holds each doc for an hour.
 */
@Composable
fun WarmJoinedRelayGroupNip11(accountViewModel: AccountViewModel) {
    val servers by accountViewModel.account.relayGroupList.liveRelayGroupServers
        .collectAsStateWithLifecycle()
    val relays = remember(servers) { servers.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) } }
    WarmNip11(relays)
}
