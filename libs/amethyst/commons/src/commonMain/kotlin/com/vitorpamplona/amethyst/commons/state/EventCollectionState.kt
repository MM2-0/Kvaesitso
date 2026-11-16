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
package com.vitorpamplona.amethyst.commons.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Generic event collection state with deduplication, batching, sorting, and size limits.
 *
 * Provides efficient management of event/item collections with:
 * - Automatic deduplication by ID
 * - Batched updates (250ms default) to reduce recomposition
 * - Optional sorting via comparator
 * - Automatic trimming to max size
 * - Thread-safe operations
 *
 * @param T The type of items to collect (must have a unique ID)
 * @param getId Function to extract unique ID from an item
 * @param sortComparator Optional comparator for sorting items (null = prepend newest first)
 * @param maxSize Maximum number of items to keep (older items trimmed)
 * @param batchDelayMs Delay in milliseconds before flushing batched updates (default 250ms)
 * @param scope CoroutineScope for batching jobs
 *
 * Usage example:
 * ```
 * val feedState = EventCollectionState<Event>(
 *     getId = { it.id },
 *     sortComparator = compareByDescending { it.createdAt },
 *     maxSize = 200,
 *     scope = viewModelScope
 * )
 *
 * // Add items (batched automatically)
 * feedState.addItem(event)
 * feedState.addItems(eventList)
 *
 * // Observe
 * val items by feedState.items.collectAsState()
 * ```
 */
class EventCollectionState<T : Any>(
    private val getId: (T) -> String,
    private val sortComparator: Comparator<T>? = null,
    private val maxSize: Int = 200,
    private val batchDelayMs: Long = 250,
    private val scope: CoroutineScope,
) {
    private val _items = MutableStateFlow<List<T>>(emptyList())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    private val seenIds = mutableSetOf<String>()
    private val pendingItems = mutableListOf<T>()
    private val mutex = Mutex()
    private var batchJob: Job? = null

    /**
     * Add a single item to the collection.
     * Updates are batched and applied after batchDelayMs.
     *
     * @param item The item to add
     */
    fun addItem(item: T) {
        scope.launch {
            mutex.withLock {
                val itemId = getId(item)
                if (itemId !in seenIds) {
                    seenIds.add(itemId)
                    pendingItems.add(item)
                    scheduleBatchUpdate()
                }
            }
        }
    }

    /**
     * Add multiple items to the collection.
     * Updates are batched and applied after batchDelayMs.
     *
     * @param items The items to add
     */
    fun addItems(items: List<T>) {
        scope.launch {
            mutex.withLock {
                val newItems = items.filter { getId(it) !in seenIds }
                if (newItems.isNotEmpty()) {
                    newItems.forEach { seenIds.add(getId(it)) }
                    pendingItems.addAll(newItems)
                    scheduleBatchUpdate()
                }
            }
        }
    }

    /**
     * Remove an item by ID.
     *
     * @param id The ID of the item to remove
     */
    fun removeItem(id: String) {
        scope.launch {
            mutex.withLock {
                seenIds.remove(id)
                _items.value = _items.value.filter { getId(it) != id }
            }
        }
    }

    /**
     * Remove multiple items by ID.
     *
     * @param ids The IDs of items to remove
     */
    fun removeItems(ids: Set<String>) {
        scope.launch {
            mutex.withLock {
                seenIds.removeAll(ids)
                _items.value = _items.value.filter { getId(it) !in ids }
            }
        }
    }

    /**
     * Clear all items from the collection.
     */
    fun clear() {
        scope.launch {
            mutex.withLock {
                seenIds.clear()
                pendingItems.clear()
                _items.value = emptyList()
                batchJob?.cancel()
                batchJob = null
            }
        }
    }

    /**
     * Get current item count.
     */
    val size: Int
        get() = _items.value.size

    /**
     * Check if collection is empty.
     */
    val isEmpty: Boolean
        get() = _items.value.isEmpty()

    /**
     * Schedules a batched update if not already scheduled (leading-edge
     * throttle). Cancelling and restarting the timer on every insert instead
     * would starve the flush on a busy feed: items arriving more often than
     * [batchDelayMs] would keep resetting the delay and the screen would never
     * update until the stream paused.
     */
    private fun scheduleBatchUpdate() {
        if (batchJob?.isActive == true) return
        batchJob =
            scope.launch {
                delay(batchDelayMs)
                applyBatchUpdate()
            }
    }

    /**
     * Applies pending items to the collection.
     * Merges with existing items, sorts if comparator provided, and trims to maxSize.
     */
    private suspend fun applyBatchUpdate() {
        mutex.withLock {
            if (pendingItems.isEmpty()) return

            // seenIds already updated in addItem/addItems

            // Merge with existing items
            val merged = _items.value + pendingItems

            // Sort if comparator provided, otherwise keep newest first (pending items already at end)
            val sorted =
                if (sortComparator != null) {
                    merged.sortedWith(sortComparator).distinctBy { getId(it) }
                } else {
                    // Reverse so newest (pending) items come first
                    (pendingItems.reversed() + _items.value).distinctBy { getId(it) }
                }

            // Trim to maxSize and update seenIds
            val trimmed =
                if (sorted.size > maxSize) {
                    val kept = sorted.take(maxSize)
                    val removed = sorted.drop(maxSize)
                    removed.forEach { seenIds.remove(getId(it)) }
                    kept
                } else {
                    sorted
                }

            _items.value = trimmed
            pendingItems.clear()
        }
    }

    /**
     * Force flush pending items immediately without waiting for batch delay.
     */
    suspend fun flush() {
        batchJob?.cancel()
        applyBatchUpdate()
    }
}
