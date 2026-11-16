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
package com.vitorpamplona.amethyst.commons.napplet

/**
 * The requests a page made to act for the user (sign, encrypt, decrypt, publish, pay…) while nobody was
 * looking at it, held until someone is (see [NappletActingRequests]).
 *
 * Bounded both ways, so an unattended page can neither grow the queue without end nor have the user come
 * back to a pile of stale prompts: past [cap] a new request is handed straight back to be failed, and one
 * held longer than [maxAgeMs] is failed instead of sent — by [expire], or when [drain] finds it on the user's
 * return. Either way the page's promise settles with an error rather than hanging.
 *
 * Single-threaded: call from the main thread.
 */
class NappletHeldRequests<T>(
    private val clock: () -> Long,
    private val cap: Int = MAX_HELD,
    private val maxAgeMs: Long = MAX_AGE_MS,
) {
    private class Held<T>(
        val item: T,
        val heldAt: Long,
    )

    private val items = ArrayDeque<Held<T>>()

    val size: Int get() = items.size

    /** Holds [item], or hands it back (for the caller to fail) when [cap] requests are already waiting. */
    fun hold(item: T): T? {
        if (items.size >= cap) return item
        items.addLast(Held(item, clock()))
        return null
    }

    /** Removes and returns the requests held longer than [maxAgeMs] (oldest first). */
    fun expire(): List<T> {
        val now = clock()
        val expired = mutableListOf<T>()
        while (items.isNotEmpty() && now - items.first().heldAt >= maxAgeMs) expired += items.removeFirst().item
        return expired
    }

    /** Removes everything held: the requests still fresh enough to send, and the ones to fail instead. */
    fun drain(): Drained<T> {
        val expired = expire()
        val fresh = items.map { it.item }
        items.clear()
        return Drained(fresh, expired)
    }

    /** Drops everything held (the page or surface is gone) and returns it. */
    fun clear(): List<T> {
        val all = items.map { it.item }
        items.clear()
        return all
    }

    data class Drained<T>(
        val send: List<T>,
        val fail: List<T>,
    )

    companion object {
        /** At most this many requests wait for the user at once. */
        const val MAX_HELD = 32

        /** A held request older than this is failed rather than sent: the moment it was made for has passed. */
        const val MAX_AGE_MS = 120_000L

        const val TOO_MANY = "Too many requests are waiting for the user."
        const val EXPIRED = "The request timed out while the user was away."
    }
}
