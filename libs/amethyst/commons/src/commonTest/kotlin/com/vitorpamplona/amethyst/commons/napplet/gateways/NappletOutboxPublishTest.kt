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
package com.vitorpamplona.amethyst.commons.napplet.gateways

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NappletOutboxPublishTest {
    private val fast = RelayUrlNormalizer.normalize("wss://fast.example.com")
    private val slow = RelayUrlNormalizer.normalize("wss://slow.example.com")
    private val silent = RelayUrlNormalizer.normalize("wss://silent.example.com")

    /** A relay that answers [accepted] after [afterMs], or never when [afterMs] is null. */
    private class Relay(
        val afterMs: Long?,
        val accepted: Boolean = true,
    )

    private fun relays(vararg pairs: Pair<NormalizedRelayUrl, Relay>): suspend (NormalizedRelayUrl, (Boolean) -> Unit) -> Unit {
        val byUrl = pairs.toMap()
        return { url, answered ->
            val relay = byUrl.getValue(url)
            if (relay.afterMs == null) awaitCancellation()
            delay(relay.afterMs)
            answered(relay.accepted)
        }
    }

    @Test
    fun aSilentRelayDoesNotHoldTheAnswer() =
        runTest {
            // The case seen live: five relays took a game's save within 200 ms, one never answered,
            // and the 15 s wait outlasted the game's own 10 s.
            val answers =
                NappletOutboxPublish().publish(setOf(fast, slow, silent), relays(fast to Relay(100), slow to Relay(300), silent to Relay(null)))

            assertEquals(mapOf(fast to true, slow to true, silent to false), answers)
            assertEquals(100 + NappletOutboxPublish.GRACE_MS, currentTime, "answered a grace period after the first relay took it")
        }

    @Test
    fun everyRelayAnsweringEndsTheWaitAtOnce() =
        runTest {
            val answers = NappletOutboxPublish().publish(setOf(fast, slow), relays(fast to Relay(100), slow to Relay(400, accepted = false)))

            assertEquals(mapOf(fast to true, slow to false), answers)
            assertEquals(400, currentTime)
        }

    @Test
    fun refusalsAloneWaitForEveryRelayUpToTheCap() =
        runTest {
            val refused = NappletOutboxPublish().publish(setOf(fast, slow), relays(fast to Relay(100, false), slow to Relay(2_500, false)))
            assertEquals(mapOf(fast to false, slow to false), refused)
            assertEquals(2_500, currentTime, "no relay took it, so every refusal counts")

            val start = currentTime
            val unanswered = NappletOutboxPublish().publish(setOf(fast, silent), relays(fast to Relay(100, false), silent to Relay(null)))
            assertEquals(mapOf(fast to false, silent to false), unanswered)
            assertEquals(NappletOutboxPublish.CAP_MS, currentTime - start)
        }

    @Test
    fun noRelaysAnswersAtOnce() =
        runTest {
            assertTrue(NappletOutboxPublish().publish(emptySet()) { _, _ -> awaitCancellation() }.isEmpty())
            assertEquals(0, currentTime)
        }
}
