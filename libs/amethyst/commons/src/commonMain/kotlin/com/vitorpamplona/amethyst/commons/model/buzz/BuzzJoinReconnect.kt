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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageKeys
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient

/**
 * Re-dials the whole relay pool after a Buzz workspace join.
 *
 * NIP-42 sends its AUTH challenge once, on connect. If the socket was already open
 * before the join (the common case — the relay is in the user's lists and connected
 * at startup), that challenge was spent while the relay was still NOT first-party,
 * so the connection is unauthenticated and every `#p=me`-gated read on it is
 * refused. Joining makes the relay first-party (see `AuthCoordinator.isFirstParty`);
 * this forces the relay to re-challenge so the connection authenticates.
 *
 * Shared by the three join sites that need the re-challenge, both to keep the
 * reconnect flags identical and to give the churn ledger one place to attribute from:
 * without the counter, `Σ(relay.trigger.*)` would only account for the
 * connectivity-driven teardowns `RelayProxyClientConnector` reports, and a full pool
 * teardown is the most expensive thing either can do.
 *
 * `BuzzInviteScreen` is a fourth join+pre-approve site that deliberately does NOT
 * reconnect — it hands off to the in-app browser rather than reading a `#p=me`-gated
 * subscription — so `relay.trigger.buzz` undercounts joins, not re-challenges.
 */
fun reconnectPoolAfterJoin(client: INostrClient) {
    LocalCache.appHost.usageCounter.add(UsageKeys.relayTrigger(UsageKeys.TRIGGER_BUZZ), 1)
    client.reconnect(onlyIfChanged = false, ignoreRetryDelays = true)
}
