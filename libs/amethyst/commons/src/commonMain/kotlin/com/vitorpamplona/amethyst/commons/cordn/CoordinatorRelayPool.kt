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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.contextvm.transport.CvmRelayPool
import com.vitorpamplona.quartz.contextvm.transport.cvmRelayPool
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Quartz's ContextVM relay pool over the account's relay client, for a cordn coordinator.
 *
 * [relays] is the coordinator's own relay list, not the account's: a coordinator has no address
 * beyond its pubkey (`spec/00.md` §8.5). What this adds to quartz's pool is only Amethyst's: the
 * [SubPurpose.COORDINATOR_GROUPS] label the relay-activity screens group by.
 */
fun coordinatorRelayPool(
    client: INostrClient,
    relays: Set<NormalizedRelayUrl>,
): CvmRelayPool =
    client.cvmRelayPool(relays) { pubKey, kinds ->
        ExplainedFilter(kinds = kinds.toList(), tags = mapOf("p" to listOf(pubKey)), purpose = SubPurpose.COORDINATOR_GROUPS)
    }
