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
package com.vitorpamplona.geode.testing

import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import kotlinx.coroutines.CompletableDeferred

/**
 * Inserts events directly into the underlying store, bypassing the wire protocol.
 *
 * Use this for **pre-test setup** — events that exist before any client connects.
 * It does NOT broadcast to active subscriptions. For sending events that should
 * fan out to live subscribers (post-EOSE), use [publish] instead.
 */
suspend fun RelayEngine.preload(events: Iterable<Event>) {
    events.forEach { store.insert(it) }
}

/** @see preload */
suspend fun RelayEngine.preload(vararg events: Event) = preload(events.toList())

/**
 * Publishes an event through the relay's session machinery so it both lands
 * in the store and fans out to active subscriptions matching its filters
 * (mirrors what a real client would do via an `EVENT` command).
 *
 * Suspends until the relay's `OK` (or `NOTICE`) reply lands, i.e. until
 * the [com.vitorpamplona.quartz.nip01Core.relay.server.backend.IngestQueue] drain
 * loop has processed the event and fanned it out to active subscribers.
 * Tests that publish-then-subscribe rely on this ordering: otherwise the
 * fire-and-forget submit lets a subscription register *after* publish
 * returns but *before* fanout runs, and ephemeral kinds (not persisted)
 * still leak to the late subscriber.
 */
suspend fun RelayEngine.publish(event: Event) {
    val replied = CompletableDeferred<Unit>()
    val session = server.connect { replied.complete(Unit) }
    try {
        session.receive(OptimizedJsonMapper.toJson(EventCmd(event)))
        replied.await()
    } finally {
        session.close()
    }
}
