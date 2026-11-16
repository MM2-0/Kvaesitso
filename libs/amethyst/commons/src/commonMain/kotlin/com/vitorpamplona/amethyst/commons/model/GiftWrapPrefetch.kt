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
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.concurrent.Volatile

/**
 * Opens gift wraps, the seals inside them, and the account's drafts, several at a time as soon
 * as they reach the cache, ahead of [EventProcessor], which indexes them one note at a time.
 *
 * Only for a remote signer (NIP-46, [enabled]): each decrypt there is a relay round trip of
 * ~0.7 s, and the serial pass used to wait for them one by one, so a cold start that opened ~110
 * messages spent over a minute with exactly one request in flight. A local key answers in
 * microseconds of CPU, where parallel decrypts only compete with ingest, and a NIP-55 app
 * without automatic approval would put up one approval screen per parallel request.
 *
 * The indexing stays serial: its handlers share chat rooms, Concord sessions and the MLS Welcome
 * flow. Only the signer round trips move ahead of it. Relays stream DMs a few per batch, so the
 * wraps are taken as each batch arrives ([offer]), not when the serial pass reaches it.
 *
 * The serial pass decrypts through [signer], which hands back a prefetched plaintext, once, for
 * the same sender key and ciphertext, and passes everything else to the account's signer.
 */
internal class GiftWrapPrefetch(
    private val scope: CoroutineScope,
    private val accountSigner: () -> NostrSigner,
    /** Whether prefetching pays off: the account signs through a remote signer. */
    private val enabled: () -> Boolean = { true },
    /** A draft the handler would skip (deleted): not worth a round trip. */
    private val skipDraft: (DraftWrapEvent) -> Boolean = { false },
    /** Fills the account's draft cache for one of its drafts (NIP-37), the same serial decrypt. */
    private val warmDraft: suspend (DraftWrapEvent) -> Unit = {},
) {
    private val gate = Semaphore(MAX_IN_FLIGHT)

    private class Answer {
        val plaintext = CompletableDeferred<String>()

        // When it was answered, in seconds; 0 while the signer has not answered.
        @Volatile var answeredAt = 0L

        fun complete(value: String) {
            answeredAt = TimeUtils.now()
            plaintext.complete(value)
        }

        fun fail(e: Throwable) {
            answeredAt = TimeUtils.now()
            plaintext.completeExceptionally(e)
        }
    }

    // "<sender key>:<ciphertext>" -> its answer, until the serial pass claims it. Keyed by the
    // sender too: anyone can copy a wrap's content under another key, and that copy must get the
    // answer the real signer would give it (a failure), never the original's plaintext.
    private val pending = LargeCache<String, Answer>()

    // Wraps being opened, by id, in seconds, until the indexer has opened them ([opened]): a batch
    // the indexer reached first is offered again by the early collector while its wrap is still
    // opening, and must not be opened twice. Not longer: a wrap pruned from the cache and sent
    // again by a relay is a new note to open, and should be fetched ahead like the first time.
    private val started = LargeCache<HexKey, Long>()

    // Draft id -> its warm-up, while it runs: the handler waits for it rather than asking again.
    private val drafts = LargeCache<HexKey, Job>()

    @Volatile private var lastSweep = 0L

    /** What the serial pass decrypts gift wraps and seals with. */
    val signer: NostrSigner = PrefetchingSigner()

    /** Starts opening the wraps and drafts in a new batch of events that belong to this account. */
    fun offer(events: Collection<Event>) {
        if (!enabled()) return
        sweep()
        val me = accountSigner().pubKey
        events.forEach { event ->
            if (event is GiftWrapEvent) {
                if (event.innerEventId == null &&
                    event.content.isNotEmpty() &&
                    event.recipientPubKey() == me &&
                    started.createIfAbsent(event.id) { TimeUtils.now() }
                ) {
                    prefetch(event)
                }
            } else if (event is DraftWrapEvent) {
                if (event.pubKey == me && !event.isDeleted() && !skipDraft(event)) warm(event)
            }
        }
    }

    /** The indexer opened [wrap] (or gave up on it): a later copy of it is a new note to open. */
    fun opened(wrap: GiftWrapEvent) {
        started.remove(wrap.id)
    }

    /** Waits for [draft]'s warm-up if one is running, so the handler finds it in the draft cache. */
    suspend fun awaitDraft(draft: DraftWrapEvent) {
        drafts.get(draft.id)?.join()
    }

    private fun warm(draft: DraftWrapEvent) {
        val job = scope.launch(start = CoroutineStart.LAZY) { gate.withPermit { warmDraft(draft) } }
        if (drafts.createIfAbsent(draft.id) { job }) {
            job.invokeOnCompletion { if (drafts.get(draft.id) === job) drafts.remove(draft.id) }
            job.start()
        } else {
            job.cancel()
        }
    }

    private fun prefetch(wrap: GiftWrapEvent) {
        val wrapAnswer = register(wrap.pubKey, wrap.content) ?: return
        scope.launch {
            try {
                // One permit for the wrap and its seal: a seal queued behind every other wrap would
                // hold the serial pass, which needs both, until the whole backlog was open.
                gate.withPermit {
                    val plaintext = accountSigner().nip44Decrypt(wrap.content, wrap.pubKey)
                    // The seal's slot first, so the serial pass, woken by the wrap's answer, never
                    // reaches the seal before it is registered and asks a second time.
                    val seal = sealIn(plaintext)
                    val sealAnswer = seal?.let { register(it.pubKey, it.content) }
                    wrapAnswer.complete(plaintext)
                    if (seal != null && sealAnswer != null) {
                        try {
                            sealAnswer.complete(accountSigner().nip44Decrypt(seal.content, seal.pubKey))
                        } catch (e: Exception) {
                            sealAnswer.fail(e)
                            if (e is CancellationException) throw e
                        }
                    }
                }
            } catch (e: Exception) {
                // The serial pass gets the same failure it would have met itself.
                wrapAnswer.fail(e)
                if (e is CancellationException) throw e
            }
        }
    }

    /** A new slot for this sender and ciphertext, or null when one is already there. */
    private fun register(
        from: HexKey,
        ciphertext: String,
    ): Answer? {
        val answer = Answer()
        return if (pending.createIfAbsent(key(from, ciphertext)) { answer }) answer else null
    }

    /**
     * Drops answers nobody claimed (a seal the cache already had, a batch the indexer never got)
     * a while after the signer gave them, and old records of opened wraps. Never one in flight.
     */
    private fun sweep() {
        val now = TimeUtils.now()
        if (now - lastSweep < SWEEP_EVERY_SECONDS) return
        lastSweep = now
        pending.keys().forEach { k ->
            val answeredAt = pending.get(k)?.answeredAt ?: return@forEach
            if (answeredAt > 0 && now - answeredAt > UNCLAIMED_SECONDS) pending.remove(k)
        }
        started.keys().forEach { id ->
            val at = started.get(id) ?: return@forEach
            if (now - at > STARTED_SECONDS) started.remove(id)
        }
    }

    private fun sealIn(plaintext: String): SealEvent? =
        try {
            (Event.fromJson(plaintext) as? SealEvent)?.takeIf { it.innerEventId == null && it.content.isNotEmpty() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    private inner class PrefetchingSigner : NostrSigner(accountSigner().pubKey) {
        private val inner: NostrSigner get() = accountSigner()

        override val pubKey: HexKey get() = inner.pubKey

        override fun isWriteable() = inner.isWriteable()

        // Claimed once and dropped: the plaintext is not kept past the note it opens.
        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ): String = pending.remove(key(fromPublicKey, ciphertext))?.plaintext?.await() ?: inner.nip44Decrypt(ciphertext, fromPublicKey)

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

        override fun hasForegroundSupport() = inner.hasForegroundSupport()
    }

    companion object {
        /**
         * Requests in flight at once. A NIP-46 bunker answered 23–33 parallel requests in 8–10 s
         * each at a cold start, against ~0.7 s for one at a time: past a handful, more only queue
         * at the signer and its relays.
         */
        const val MAX_IN_FLIGHT = 8

        const val UNCLAIMED_SECONDS = 60L
        const val STARTED_SECONDS = 600L
        const val SWEEP_EVERY_SECONDS = 10L

        private fun key(
            from: HexKey,
            ciphertext: String,
        ) = "$from:$ciphertext"
    }
}
