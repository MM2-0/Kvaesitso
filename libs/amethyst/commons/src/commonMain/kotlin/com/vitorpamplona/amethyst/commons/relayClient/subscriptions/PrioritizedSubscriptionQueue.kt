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
package com.vitorpamplona.amethyst.commons.relayClient.subscriptions

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

/**
 * A subscription request with its priority.
 */
data class PrioritizedFilter(
    val priority: SubscriptionPriority,
    val filter: Filter,
    val tag: String? = null,
)

/**
 * Queue that processes subscription requests in priority order.
 * Ensures metadata loads before reactions, reactions before replies, etc.
 *
 * Usage:
 * ```
 * val queue = PrioritizedSubscriptionQueue(scope)
 * queue.start { filter -> relayClient.subscribe(filter) }
 *
 * // Add subscriptions - they'll be processed in priority order
 * queue.enqueue(SubscriptionPriority.METADATA, metadataFilter)
 * queue.enqueue(SubscriptionPriority.REACTIONS, reactionsFilter)
 * ```
 */
class PrioritizedSubscriptionQueue(
    private val scope: CoroutineScope,
) {
    // Separate channels per priority for efficient processing
    private val queues =
        SubscriptionPriority.entries.associateWith {
            Channel<PrioritizedFilter>(Channel.BUFFERED)
        }

    private var isRunning = false

    /**
     * Enqueue a filter with the given priority.
     */
    fun enqueue(
        priority: SubscriptionPriority,
        filter: Filter,
        tag: String? = null,
    ) {
        queues[priority]?.trySend(PrioritizedFilter(priority, filter, tag))
    }

    /**
     * Enqueue multiple filters with the same priority.
     */
    fun enqueueAll(
        priority: SubscriptionPriority,
        filters: List<Filter>,
        tag: String? = null,
    ) {
        filters.forEach { enqueue(priority, it, tag) }
    }

    /**
     * Start processing the queue.
     * Processes higher priority items first within each batch.
     *
     * @param onSubscribe Callback to execute the subscription
     */
    fun start(onSubscribe: suspend (Filter) -> Unit) {
        if (isRunning) return
        isRunning = true

        // Process each priority queue in order
        SubscriptionPriority.sortedByPriority().forEach { priority ->
            scope.launch {
                queues[priority]?.consumeAsFlow()?.collect { item ->
                    onSubscribe(item.filter)
                }
            }
        }
    }

    /**
     * Process all pending items immediately in priority order.
     * Useful for batch operations.
     */
    suspend fun flush(onSubscribe: suspend (Filter) -> Unit) {
        SubscriptionPriority.sortedByPriority().forEach { priority ->
            val queue = queues[priority] ?: return@forEach
            while (true) {
                val item = queue.tryReceive().getOrNull() ?: break
                onSubscribe(item.filter)
            }
        }
    }

    /**
     * Clear all pending items.
     */
    fun clear() {
        queues.values.forEach { channel ->
            while (channel.tryReceive().isSuccess) {
                // Drain the channel
            }
        }
    }
}
