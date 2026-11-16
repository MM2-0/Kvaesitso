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
package com.vitorpamplona.amethyst.commons.relayClient.nip47WalletConnect

import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubNoEoseCacheEoseManager
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

class NWCPaymentWatcherSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<NWCPaymentQueryState>,
) : SingleSubNoEoseCacheEoseManager<NWCPaymentQueryState>(client, allKeys) {
    override fun updateFilter(keys: List<NWCPaymentQueryState>): List<RelayBasedFilter>? {
        if (keys.isEmpty()) return null

        return keys.groupBy { it.relay }.map { relayGroup ->
            val replyingToPayments = relayGroup.value.mapTo(mutableSetOf()) { it.replyingToHex }
            val aboutUsers = relayGroup.value.mapTo(mutableSetOf()) { it.toUserHex }

            if (replyingToPayments.isEmpty()) return null

            RelayBasedFilter(
                relay = relayGroup.key,
                filter = filterNWCPaymentsFromRequests(replyingToPayments, aboutUsers),
            )
        }
    }

    override fun distinct(key: NWCPaymentQueryState) = key.replyingToHex
}
