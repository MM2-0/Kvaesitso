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
package com.vitorpamplona.amethyst.commons.model.nip46Signer

import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.Nip11CachedRetriever
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import kotlinx.coroutines.withTimeoutOrNull

/** How long a `switch_relays` answer waits on a relay's NIP-11 document before trusting it. */
private const val NIP11_TIMEOUT_MS = 5_000L

/**
 * Whether [relay] will carry a NIP-46 session, by its NIP-11 document: kind 24133 goes between two
 * transport keys that are nobody's account, so a relay that requires login, payment or restricted
 * writes would refuse it. A relay that publishes no limits, or doesn't answer in time, is trusted.
 */
suspend fun Nip11CachedRetriever.acceptsSignerTraffic(relay: NormalizedRelayUrl): Boolean {
    var info: Nip11RelayInformation? = null
    withTimeoutOrNull(NIP11_TIMEOUT_MS) {
        loadRelayInfo(relay, onInfo = { info = it }, onError = { _, _, _ -> })
    }
    val limits = info?.limitation ?: return true
    return limits.auth_required != true && limits.payment_required != true && limits.restricted_writes != true
}
