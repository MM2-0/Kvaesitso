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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GiftWrapPrefetchTest {
    private val sender = NostrSignerInternal(KeyPair())
    private val recipient = NostrSignerInternal(KeyPair())

    private suspend fun wrappedDms(count: Int): List<GiftWrapEvent> =
        (1..count).map { i ->
            val dm = sender.sign<Event>(TimeUtils.now(), 14, arrayOf(arrayOf("p", recipient.pubKey)), "message $i")
            GiftWrapEvent.create(SealEvent.create(dm, recipient.pubKey, sender), recipient.pubKey)
        }

    @Test
    fun wrapsOpenInParallelOnceEachAndTheSerialPassGetsTheAnswers() =
        runTest {
            val wraps = wrappedDms(20)
            val signer = SlowSigner(recipient, delayMs = 100)
            val prefetch = GiftWrapPrefetch(backgroundScope, { signer })

            val start = currentTime
            // Relays stream DMs a few per batch: offered one wrap at a time, as they arrive.
            wraps.forEach { prefetch.offer(listOf(it)) }
            // The indexing pass, one note at a time as EventProcessor runs it.
            val messages =
                wraps.map { wrap ->
                    val seal = wrap.unwrapThrowing(prefetch.signer) as SealEvent
                    seal.unsealRumorThrowing(prefetch.signer).content
                }
            val elapsed = currentTime - start

            assertEquals((1..20).map { "message $it" }, messages)
            // A wrap and a seal for each message, none asked twice.
            assertEquals(40, signer.calls)
            assertTrue(signer.maxInFlight in 2..GiftWrapPrefetch.MAX_IN_FLIGHT, "in flight: ${signer.maxInFlight}")
            // One at a time would take 40 x 100 ms.
            assertTrue(elapsed < 1_500, "took $elapsed ms")
        }

    @Test
    fun offeringTheSameBatchTwiceAsksOnce() =
        runTest {
            val wraps = wrappedDms(3)
            val signer = SlowSigner(recipient, delayMs = 10)
            val prefetch = GiftWrapPrefetch(backgroundScope, { signer })

            prefetch.offer(wraps)
            prefetch.offer(wraps)
            wraps.forEach { (it.unwrapThrowing(prefetch.signer) as SealEvent).unsealRumorThrowing(prefetch.signer) }

            assertEquals(6, signer.calls)
        }

    @Test
    fun aLateOfferOfAWrapTheSerialPassAlreadyClaimedAsksNothingAgain() =
        runTest {
            val wraps = wrappedDms(3)
            val signer = SlowSigner(recipient, delayMs = 100)
            val prefetch = GiftWrapPrefetch(backgroundScope, { signer })

            // The indexer got the batch first: it offers it and starts opening the first wrap.
            prefetch.offer(wraps)
            val opening = async { (wraps[0].unwrapThrowing(prefetch.signer) as SealEvent).unsealRumorThrowing(prefetch.signer) }
            runCurrent()
            // The early collector sees the same batch only now, while that wrap is still open.
            prefetch.offer(wraps)
            opening.await()
            wraps.drop(1).forEach { (it.unwrapThrowing(prefetch.signer) as SealEvent).unsealRumorThrowing(prefetch.signer) }
            delay(1_000)

            assertEquals(6, signer.calls)
        }

    @Test
    fun aWrapSentAgainAfterItWasOpenedIsFetchedAheadAgain() =
        runTest {
            val wrap = wrappedDms(1).single()
            val signer = SlowSigner(recipient, delayMs = 10)
            val prefetch = GiftWrapPrefetch(backgroundScope, { signer })

            prefetch.offer(listOf(wrap))
            (wrap.unwrapThrowing(prefetch.signer) as SealEvent).unsealRumorThrowing(prefetch.signer)
            prefetch.opened(wrap)

            // Pruned from the cache and delivered again: a fresh, unopened copy of the same wrap.
            val again = Event.fromJson(wrap.toJson()) as GiftWrapEvent
            prefetch.offer(listOf(again))
            // The prefetch runs in the background scope, which advanceUntilIdle does not wait for.
            delay(1_000)

            // Its wrap and seal fetched ahead the second time too.
            assertEquals(4, signer.calls)
        }

    @Test
    fun wrapsForSomeoneElseAreLeftAlone() =
        runTest {
            val signer = SlowSigner(sender, delayMs = 10)
            val prefetch = GiftWrapPrefetch(backgroundScope, { signer })

            // Addressed to the recipient, while this account is the sender.
            prefetch.offer(wrappedDms(3))
            delay(1_000)

            assertEquals(0, signer.calls)
        }

    @Test
    fun draftsWarmInParallelOnceEachAndTheHandlerWaitsForThem() =
        runTest {
            val drafts =
                (1..12).map { i ->
                    val note = recipient.sign<Event>(TimeUtils.now(), 1, emptyArray(), "draft $i")
                    DraftWrapEvent.create("d$i", note, recipient)
                }
            var warmed = 0
            var inFlight = 0
            var maxInFlight = 0
            val prefetch =
                GiftWrapPrefetch(backgroundScope, { recipient }) {
                    warmed++
                    inFlight++
                    maxInFlight = maxOf(maxInFlight, inFlight)
                    delay(100)
                    inFlight--
                }

            val start = currentTime
            prefetch.offer(drafts)
            prefetch.offer(drafts)
            // The draft handler, one at a time: it waits for the warm-up instead of decrypting again.
            drafts.forEach { prefetch.awaitDraft(it) }
            val elapsed = currentTime - start

            assertEquals(12, warmed)
            assertTrue(maxInFlight in 2..GiftWrapPrefetch.MAX_IN_FLIGHT, "in flight: $maxInFlight")
            assertTrue(elapsed < 600, "took $elapsed ms")
        }

    @Test
    fun aCopyOfAWrapUnderAnotherKeyNeverGetsTheOriginalsPlaintext() =
        runTest {
            val original = wrappedDms(1).single()
            // Anyone can republish the content under their own key, p-tagging the same recipient.
            val copy =
                NostrSignerInternal(KeyPair()).sign<GiftWrapEvent>(
                    TimeUtils.now(),
                    GiftWrapEvent.KIND,
                    arrayOf(arrayOf("p", recipient.pubKey)),
                    original.content,
                )
            val signer = SlowSigner(recipient, delayMs = 10)
            val prefetch = GiftWrapPrefetch(backgroundScope, { signer })

            // Both orders: the copy fails as the real signer fails it, the original still opens.
            prefetch.offer(listOf(original, copy))
            assertTrue(runCatching { copy.unwrapThrowing(prefetch.signer) }.isFailure)
            assertEquals("message 1", (original.unwrapThrowing(prefetch.signer) as SealEvent).unsealRumorThrowing(prefetch.signer).content)

            val original2 = wrappedDms(1).single()
            val copy2 =
                NostrSignerInternal(KeyPair()).sign<GiftWrapEvent>(TimeUtils.now(), GiftWrapEvent.KIND, arrayOf(arrayOf("p", recipient.pubKey)), original2.content)
            prefetch.offer(listOf(copy2, original2))
            assertEquals("message 1", (original2.unwrapThrowing(prefetch.signer) as SealEvent).unsealRumorThrowing(prefetch.signer).content)
            assertTrue(runCatching { copy2.unwrapThrowing(prefetch.signer) }.isFailure)
        }

    @Test
    fun theFirstMessageOpensWithoutWaitingForTheWholeBacklog() =
        runTest {
            val wraps = wrappedDms(40)
            val signer = SlowSigner(recipient, delayMs = 100)
            val prefetch = GiftWrapPrefetch(backgroundScope, { signer })

            val start = currentTime
            prefetch.offer(wraps)
            (wraps.first().unwrapThrowing(prefetch.signer) as SealEvent).unsealRumorThrowing(prefetch.signer)

            // Its wrap and its seal, one after the other; not behind the other 39 wraps.
            assertEquals(200, currentTime - start)
        }

    @Test
    fun offWhenTheSignerIsNotRemote() =
        runTest {
            val signer = SlowSigner(recipient, delayMs = 10)
            val prefetch = GiftWrapPrefetch(backgroundScope, { signer }, enabled = { false })

            prefetch.offer(wrappedDms(3))
            delay(1_000)

            assertEquals(0, signer.calls)
        }

    @Test
    fun deletedDraftsAreNotWarmed() =
        runTest {
            val draft = DraftWrapEvent.create("d", recipient.sign<Event>(TimeUtils.now(), 1, emptyArray(), "draft"), recipient)
            var warmed = 0
            val prefetch = GiftWrapPrefetch(backgroundScope, { recipient }, skipDraft = { true }) { warmed++ }

            prefetch.offer(listOf(draft))
            delay(1_000)

            assertEquals(0, warmed)
        }

    private class SlowSigner(
        val inner: NostrSigner,
        val delayMs: Long,
    ) : NostrSigner(inner.pubKey) {
        var calls = 0
        var inFlight = 0
        var maxInFlight = 0

        override fun isWriteable() = true

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ): String {
            calls++
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            try {
                delay(delayMs)
                return inner.nip44Decrypt(ciphertext, fromPublicKey)
            } finally {
                inFlight--
            }
        }

        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T = inner.sign(createdAt, kind, tags, content)

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = inner.nip04Encrypt(plaintext, toPublicKey)

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = inner.nip04Decrypt(ciphertext, fromPublicKey)

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = inner.nip44Encrypt(plaintext, toPublicKey)

        override suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent = inner.decryptZapEvent(event)

        override suspend fun deriveKey(nonce: HexKey) = inner.deriveKey(nonce)

        override suspend fun signPsbt(psbtHex: String) = inner.signPsbt(psbtHex)

        override fun hasForegroundSupport() = false
    }
}
