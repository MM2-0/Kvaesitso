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
package com.vitorpamplona.amethyst.commons.service

import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** This class is designed to have a waiting time between two calls of invalidate */
class BundledUpdate(
    val delay: Long,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    // Exists to avoid exceptions stopping the coroutine
    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e("BundledUpdate", "Caught exception: ${throwable.message}", throwable)
        }

    val scope = CoroutineScope(dispatcher + SupervisorJob() + exceptionHandler)

    val bundledUpdate = BasicBundledUpdate(delay, dispatcher, scope)

    fun invalidate(
        ignoreIfDoing: Boolean = false,
        onUpdate: suspend () -> Unit,
    ) = bundledUpdate.invalidate(ignoreIfDoing, onUpdate)

    fun cancel() {
        scope.cancel()
    }
}

/**
 * Runs [invalidate]'s update once, then once more per [delay] for as long as invalidations keep
 * arriving, however many there are.
 *
 * Feeds and subscription managers call [invalidate] for every event that touches them, so a burst
 * is thousands of calls on top of a run already in progress. Each call is one atomic read and at
 * most one compare-and-set; a coroutine is launched only to start a run from idle. Launching one
 * per call just to find a run in progress cost ~250 ns of CPU and ~7 us for an IO-dispatched caller
 * (JMH); this costs ~1.3 ns.
 */
@OptIn(ExperimentalAtomicApi::class)
class BasicBundledUpdate(
    val delay: Long,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    val scope: CoroutineScope,
) {
    private val state = AtomicInt(IDLE)

    fun invalidate(
        ignoreIfDoing: Boolean = false,
        onUpdate: suspend () -> Unit,
    ) {
        while (true) {
            when (state.load()) {
                IDLE -> {
                    if (state.compareAndSet(IDLE, RUNNING)) {
                        scope.launch(dispatcher) { run(onUpdate) }
                        return
                    }
                }
                RUNNING -> {
                    if (ignoreIfDoing || state.compareAndSet(RUNNING, DIRTY)) return
                }
                // DIRTY: a repeat run is already owed.
                else -> return
            }
        }
    }

    private suspend fun run(onUpdate: suspend () -> Unit) {
        var released = false
        try {
            onUpdate()
            while (true) {
                delay(delay)
                // RUNNING -> IDLE ends the run. If that fails, an invalidation made it DIRTY in the
                // meantime (nothing else moves it while a run holds it): clear that and go again.
                if (state.compareAndSet(RUNNING, IDLE)) {
                    released = true
                    return
                }
                state.store(RUNNING)
                onUpdate()
            }
        } finally {
            // Cancelled or failed: hand the slot back so the next invalidation starts a new run.
            if (!released) state.store(IDLE)
        }
    }

    private companion object {
        const val IDLE = 0
        const val RUNNING = 1
        const val DIRTY = 2
    }
}

/** This class is designed to have a waiting time between two calls of invalidate */
class BundledInsert<T>(
    val delay: Long,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    // Exists to avoid exceptions stopping the coroutine
    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e("BundledInsert", "Caught exception: ${throwable.message}", throwable)
        }

    val scope = CoroutineScope(dispatcher + SupervisorJob() + exceptionHandler)

    val bundledInsert = BasicBundledInsert<T>(delay, dispatcher, scope)

    fun invalidateList(
        newObject: T,
        onUpdate: suspend (Set<T>) -> Unit,
    ) = bundledInsert.invalidateList(newObject, onUpdate)

    fun cancel() {
        scope.cancel()
    }
}

/** This class is designed to have a waiting time between two calls of invalidate */
class BasicBundledInsert<T>(
    val delay: Long,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var isProcessing = false
    private val queue = mutableListOf<T>()

    fun invalidateList(
        newObject: T,
        onUpdate: suspend (Set<T>) -> Unit,
    ) {
        scope.launch(dispatcher) {
            mutex.withLock {
                queue.add(newObject)

                if (isProcessing) {
                    return@launch
                }
                isProcessing = true
            }

            try {
                processLoop@ while (true) {
                    val batch =
                        mutex.withLock {
                            if (queue.isEmpty()) {
                                isProcessing = false
                                null
                            } else {
                                val items = queue.toSet()
                                queue.clear()
                                items
                            }
                        }

                    if (batch == null) break@processLoop

                    if (batch.isNotEmpty()) {
                        onUpdate(batch)
                    }

                    delay(delay)
                }
            } finally {
                // Mirrors BasicBundledUpdate: a throw out of onUpdate (or a
                // cancellation) must not leave isProcessing stuck at true, or
                // every later invalidateList() would enqueue and return forever.
                withContext(NonCancellable) {
                    mutex.withLock { isProcessing = false }
                }
            }
        }
    }
}
