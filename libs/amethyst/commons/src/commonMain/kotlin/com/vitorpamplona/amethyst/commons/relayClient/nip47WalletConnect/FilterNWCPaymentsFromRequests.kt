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

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcResponseEvent

// The request event id (#e) is a unique 32-byte identifier — sufficient on
// its own to match a spec-compliant kind-23195 response. We deliberately keep
// `authors` out of the filter because some wallet services route through a
// signer pubkey that differs from the one advertised in the connection URI,
// which made the previous `authors`-strict filter time out.
//
// We DO keep `#p: [client pubkey]` because purpose-built NWC relays
// (e.g. relay.getalby.com/v1) use the `#p` tag as the routing key — without
// it the relay never delivers the response to our subscription, even though
// the event is well-formed. Spec-compliant responses always set `p` to the
// client pubkey, so including `#p` does not exclude any conforming wallet.
// The wallet's identity is still authenticated end-to-end by NIP-04
// decryption against the per-connection shared secret AND by a client-side
// `event.pubKey == request.p` check in NwcPaymentTracker.
fun filterNWCPaymentsFromRequests(
    paymentRequests: Set<HexKey>,
    fromUsers: Set<HexKey>,
): Filter =
    ExplainedFilter(
        purpose = SubPurpose.NWC,
        kinds = listOf(NwcResponseEvent.KIND),
        tags =
            mapOf(
                "e" to paymentRequests.sorted(),
                "p" to fromUsers.sorted(),
            ),
    )
