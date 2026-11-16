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
package com.vitorpamplona.amethyst.commons.util

/**
 * KMP-friendly thread-safe set of non-null elements.
 *
 * On JVM/Android this is backed by `ConcurrentHashMap.newKeySet()`, so
 * concurrent [add] / [contains] / [remove] from many threads do NOT all
 * serialize on one monitor — writes are lock-striped and reads are lock-free.
 * That is the difference from wrapping a plain set in a single [KmpLock], where
 * every operation from every thread contends the same lock.
 *
 * iOS has no lock-free set in the standard library, so its actual guards a plain
 * set with a reentrant lock — same behaviour as before, no regression. The
 * win is on the JVM/Android app, which is where the high-throughput event
 * paths run.
 *
 * Use this only when the access pattern is genuinely multi-threaded and hot
 * (e.g. per-event dedup fed by multiple relay callback threads). A set touched
 * only from one thread does not need it.
 */
expect class ConcurrentSet<E : Any>() {
    /** Adds [element]; returns true if it was not already present. */
    fun add(element: E): Boolean

    operator fun contains(element: E): Boolean

    /** Removes [element]; returns true if it was present. */
    fun remove(element: E): Boolean

    fun clear()

    val size: Int

    fun isEmpty(): Boolean

    /** A point-in-time copy — safe to iterate without a lock. */
    fun snapshot(): Set<E>
}
