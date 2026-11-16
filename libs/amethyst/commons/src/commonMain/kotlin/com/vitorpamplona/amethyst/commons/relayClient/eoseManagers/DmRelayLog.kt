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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log

/**
 * Diagnostic for "which account is bringing which relays into the DM filters". For each DM
 * subscription it prints the account whose relays are used and breaks the relay set down by the
 * source list each relay comes from (NIP-65 inbox/outbox, the DM-relay-list, the private-storage
 * outbox, local relays). Use it to trace an unexpected relay — e.g. a write-only NIP-65 relay that
 * only the NIP-04 (home+dm) path queries — back to the list it leaks in from.
 */
object DmRelayLog {
    private const val TAG = "DMPagination"

    fun log(
        label: String,
        account: Account,
    ) = Log.d(TAG) {
        val pk = account.userProfile().pubkeyHex.take(8)
        val inbox = account.nip65RelayList.inboxFlow.value
        val outbox = account.nip65RelayList.outboxFlow.value
        val dmList = account.dmRelayList.flow.value
        val priv = account.privateStorageRelayList.flow.value
        val local = account.localRelayList.flow.value
        buildString {
            append("[$label] account=$pk relays by source:")
            appendSource("nip65In", inbox)
            appendSource("nip65Out", outbox)
            appendSource("dmList", dmList)
            appendSource("private", priv)
            appendSource("local", local)
        }
    }

    private fun StringBuilder.appendSource(
        name: String,
        relays: Collection<NormalizedRelayUrl>,
    ) {
        if (relays.isNotEmpty()) append(" $name=${relays.map { it.url }}")
    }
}
