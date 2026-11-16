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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcResponseEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The wallet's pubkey and the request id are both public on the relay, so a kind-23195 that names
 * them proves nothing until its signature checks out. A forged one must not resolve, and so use up,
 * the pending request the real wallet answer is still on its way to.
 */
class EventCacheNwcResponseTest {
    private val wallet = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))
    private val user = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000009".hexToByteArray()))

    @Test
    fun forgedSignatureResponseKeepsTheRequestPending() {
        val cache = EventCache()
        val requestId = "ab".repeat(32)
        cache.paymentTracker.registerRequest(requestId, wallet.pubKey, null) { }

        val forged =
            NwcResponseEvent(
                "cd".repeat(32),
                wallet.pubKey,
                1_000,
                arrayOf(arrayOf("e", requestId), arrayOf("p", user.pubKey)),
                "garbage",
                "00".repeat(64),
            )

        assertFalse(cache.justConsume(forged, null, false))
        assertTrue(cache.paymentTracker.hasPendingRequest(requestId))
    }
}
