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
package com.vitorpamplona.amethyst.commons.relayClient.preload

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Global rate limiter for metadata requests to prevent thundering herd on fast scroll.
 * Batches pubkeys and processes at a controlled rate.
 *
 * @param maxRequestsPerSecond Maximum requests to process per second (default 20)
 * @param scope CoroutineScope for processing
 */
class MetadataRateLimiter(
    private val maxRequestsPerSecond: Int = 20,
    private val scope: CoroutineScope,
    /** How long a partial batch may wait for more pubkeys before it is flushed. */
    private val flushDelayMs: Long = 250,
) {
    private val queue = Channel<String>(Channel.BUFFERED)

    // Written from enqueue() (feed/scroll path, any thread) and from the
    // collector coroutine — a plain HashSet corrupts under that.
    private val processedLock = KmpLock()
    private val processed = mutableSetOf<String>()

    /** Returns true when [pubkey] was not processed before (and marks it). */
    private fun markProcessed(pubkey: String) = processedLock.withLock { processed.add(pubkey) }

    /**
     * Enqueue a pubkey for metadata fetching.
     * Duplicates within the same batch are automatically filtered.
     */
    fun enqueue(pubkey: String) {
        if (!isProcessed(pubkey)) {
            queue.trySend(pubkey)
        }
    }

    /**
     * Enqueue multiple pubkeys for metadata fetching.
     */
    fun enqueueAll(pubkeys: Collection<String>) {
        pubkeys.forEach { enqueue(it) }
    }

    /**
     * Start processing the queue with rate limiting.
     * @param onRequest Callback invoked for each pubkey to process
     */
    fun start(onRequest: suspend (String) -> Unit) {
        scope.launch {
            val batch = mutableListOf<String>()
            while (true) {
                // A partial batch must not wait for the channel to close (it
                // never does): flush it once the queue has been quiet for
                // flushDelayMs, otherwise fewer than maxRequestsPerSecond
                // pubkeys would never be requested at all.
                val pubkey =
                    if (batch.isEmpty()) {
                        queue.receive()
                    } else {
                        withTimeoutOrNull(flushDelayMs) { queue.receive() }
                    }

                if (pubkey != null && markProcessed(pubkey)) {
                    batch.add(pubkey)
                }

                if (batch.size >= maxRequestsPerSecond || (pubkey == null && batch.isNotEmpty())) {
                    processBatch(batch, onRequest)
                    batch.clear()
                    delay(1000) // Wait 1 second before next batch
                }
            }
        }
    }

    private suspend fun processBatch(
        batch: List<String>,
        onRequest: suspend (String) -> Unit,
    ) {
        batch.forEach { pubkey ->
            onRequest(pubkey)
        }
    }

    /**
     * Clear the processed set to allow re-fetching.
     * Call this when switching accounts or clearing cache.
     */
    fun reset() {
        processedLock.withLock { processed.clear() }
    }

    /**
     * Check if a pubkey has already been processed.
     */
    fun isProcessed(pubkey: String): Boolean = processedLock.withLock { pubkey in processed }
}
