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

actual class ConcurrentSet<E : Any> {
    // No lock-free set in the K/N standard library — guard a plain set with the
    // same reentrant lock KmpLock uses. Same behaviour as the previous
    // KmpLock-wrapped sets; the lock-striped win only exists on JVM/Android.
    private val lock = KmpLock()
    private val set = HashSet<E>()

    actual fun add(element: E): Boolean = lock.withLock { set.add(element) }

    actual operator fun contains(element: E): Boolean = lock.withLock { set.contains(element) }

    actual fun remove(element: E): Boolean = lock.withLock { set.remove(element) }

    actual fun clear() = lock.withLock { set.clear() }

    actual val size: Int get() = lock.withLock { set.size }

    actual fun isEmpty(): Boolean = lock.withLock { set.isEmpty() }

    actual fun snapshot(): Set<E> = lock.withLock { HashSet(set) }
}
