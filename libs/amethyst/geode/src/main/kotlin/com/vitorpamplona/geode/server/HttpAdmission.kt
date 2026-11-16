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
package com.vitorpamplona.geode.server

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * How many NIP-FE requests run at once, per client and in all. Each HTTP request is its own
 * connection, so the per-connection limits the websocket leans on (open subscriptions, one search at
 * a time) bound nothing here; this does. A request over its client's share is that client's to
 * slow down (429); one over the relay's is everyone's (503).
 */
internal class HttpAdmission(
    /** Requests running at once across all clients; 0 is no limit. */
    private val maxConcurrent: Int,
    /** Requests one client may run at once; 0 is no limit. */
    private val maxPerClient: Int,
) {
    enum class Verdict { ADMITTED, CLIENT_BUSY, RELAY_BUSY }

    private val running = AtomicInteger()
    private val perClient = ConcurrentHashMap<String, Int>()

    /** Number of requests running now. */
    val inFlight: Int get() = running.get()

    /** Runs [block] if [client] and the relay have room; otherwise says which of them had none. */
    suspend fun admit(
        client: String,
        block: suspend () -> Unit,
    ): Verdict {
        val verdict = enter(client)
        if (verdict != Verdict.ADMITTED) return verdict
        try {
            block()
        } finally {
            leave(client)
        }
        return verdict
    }

    private fun enter(client: String): Verdict {
        var clientFull = false
        perClient.compute(client) { _, n ->
            val now = n ?: 0
            if (maxPerClient in 1..now) {
                clientFull = true
                n
            } else {
                now + 1
            }
        }
        if (clientFull) return Verdict.CLIENT_BUSY
        if (running.incrementAndGet().let { maxConcurrent in 1 until it }) {
            running.decrementAndGet()
            release(client)
            return Verdict.RELAY_BUSY
        }
        return Verdict.ADMITTED
    }

    private fun leave(client: String) {
        running.decrementAndGet()
        release(client)
    }

    private fun release(client: String) {
        perClient.computeIfPresent(client) { _, n -> if (n <= 1) null else n - 1 }
    }
}
