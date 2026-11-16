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
package com.vitorpamplona.amethyst.commons.search

import com.vitorpamplona.amethyst.commons.util.ConcurrentSet

/**
 * Tracks event ids already seen so duplicate deliveries can be dropped.
 *
 * Fed from relay subscription callbacks, which can arrive on multiple threads
 * concurrently, so this uses a [ConcurrentSet] (lock-striped on JVM/Android)
 * rather than a single lock that would serialize every delivery.
 */
class EventDeduplicator {
    private val seenIds = ConcurrentSet<String>()

    fun tryAdd(id: String): Boolean = seenIds.add(id)

    fun contains(id: String): Boolean = seenIds.contains(id)

    fun clear() = seenIds.clear()

    val size: Int get() = seenIds.size
}
