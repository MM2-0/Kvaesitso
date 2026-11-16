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
package com.vitorpamplona.amethyst.commons.relayClient.preload

import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.metadata.pickAccountDataRelays
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Dials the relays a freshly loaded [Account] is about to ask, before anything asks them.
 *
 * An account's first REQs (its own lists, then the home feed) come from subscriptions the screens
 * register once they compose, and a relay only dials once a REQ wants it. On a cold start the
 * first composition of the logged-in shell takes longer than a TLS handshake, so dialing at login
 * overlaps the two: by the time the REQs arrive, the sockets are open.
 *
 * Holds the connections with [INostrClient.keepConnected] for [holdMs], long enough for the
 * subscriptions to take over, then lets go: a relay nothing asks for by then closes as usual.
 * Dials the relays [pickAccountDataRelays] reads the account's own events from, and the outbox
 * relays of the follows as known now (on a cold cache, the hint and fallback relays the home feed
 * starts on). Blocked relays are left out, and since the blocked list often loads after the
 * login, a relay it adds while held is let go at once.
 */
class AccountRelayPreconnect(
    private val client: INostrClient,
    private val scope: CoroutineScope,
    private val holdMs: Long = DEFAULT_HOLD_MS,
) {
    fun relaysFor(account: Account): Set<NormalizedRelayUrl> {
        val ownData =
            pickAccountDataRelays(
                home = account.homeRelays.flow.value,
                inbox = account.nip65RelayList.inboxFlow.value,
                defaults = Constants.eventFinderRelays,
            )
        val follows = account.followsPerRelay.value.keys
        return (ownData + follows) - account.blockedRelayList.flow.value
    }

    fun preconnect(account: Account) = hold(relaysFor(account), account.blockedRelayList.flow)

    /**
     * Holds [relays] for [holdMs], letting go of each one as soon as [blocked] lists it: keepConnected
     * bypasses the client's blocked-relay filter, which only stops REQs.
     */
    fun hold(
        relays: Set<NormalizedRelayUrl>,
        blocked: Flow<Collection<NormalizedRelayUrl>>,
    ): Job? {
        if (relays.isEmpty()) return null
        relays.forEach { client.keepConnected(it) }
        return scope.launch {
            val held = relays.toMutableSet()
            try {
                withTimeoutOrNull(holdMs) {
                    blocked.collect { nowBlocked ->
                        val release = held.filter { it in nowBlocked }
                        held.removeAll(release.toSet())
                        release.forEach { client.releaseConnection(it) }
                    }
                }
            } finally {
                withContext(NonCancellable) { held.forEach { client.releaseConnection(it) } }
            }
        }
    }

    companion object {
        const val DEFAULT_HOLD_MS = 30_000L
    }
}
