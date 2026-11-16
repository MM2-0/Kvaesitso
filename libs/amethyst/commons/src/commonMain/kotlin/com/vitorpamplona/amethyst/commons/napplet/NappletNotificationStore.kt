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

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Per-coordinate registry of the notifications a napplet created via the NAP `notify` domain. Lives
 * in the main process (the broker's process) and survives broker rebuilds on account switch, so a
 * napplet's `notify.list`/`notify.dismiss` see the same set it created.
 *
 * Namespaced by applet coordinate, like [DataStoreNappletStorage]: a napplet can only ever see and
 * dismiss its **own** notifications. In-memory only — notifications are ephemeral UI, not durable state.
 */
@OptIn(ExperimentalAtomicApi::class)
object NappletNotificationStore {
    // coordinate -> insertion-ordered bucket so list() is stable. The bucket lock
    // guards the LinkedHashMap's ordered mutation; per-bucket so coordinates never
    // contend with each other.
    private class Bucket {
        val lock = KmpLock()
        val map = LinkedHashMap<String, NappletNotification>()
    }

    private const val MAX_PER_NAPPLET = 50
    private const val MAX_TITLE_CHARS = 256
    private const val MAX_BODY_CHARS = 4096

    private val byCoordinate = ConcurrentMap<String, Bucket>()
    private val seq = AtomicLong(0)

    fun create(
        coordinate: String,
        title: String,
        body: String,
    ): String {
        val id = "n${TimeUtils.nowMillis()}-${seq.addAndFetch(1L)}"
        val bucket = byCoordinate.getOrPut(coordinate) { Bucket() }
        bucket.lock.withLock {
            // Untrusted text: capped like the count, or 50 notifications could still hold anything.
            bucket.map[id] = NappletNotification(id, title.take(MAX_TITLE_CHARS), body.take(MAX_BODY_CHARS))
            // A napplet is untrusted and only removes what it dismisses; a create loop must not
            // grow this process-wide store forever. Drops the oldest beyond the cap.
            val iterator = bucket.map.keys.iterator()
            while (bucket.map.size > MAX_PER_NAPPLET && iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        return id
    }

    fun list(coordinate: String): List<NappletNotification> {
        val bucket = byCoordinate[coordinate] ?: return emptyList()
        return bucket.lock.withLock { bucket.map.values.toList() }
    }

    fun dismiss(
        coordinate: String,
        id: String,
    ) {
        val bucket = byCoordinate[coordinate] ?: return
        bucket.lock.withLock { bucket.map.remove(id) }
    }
}
