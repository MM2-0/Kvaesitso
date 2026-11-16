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

import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.RelaySession
import com.vitorpamplona.quartz.nip01Core.relay.server.SessionSink
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.FrameTooBigException
import io.ktor.websocket.ProtocolViolationException
import io.ktor.websocket.readText
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Per-WebSocket pump that owns the bounded outbound queue and the
 * writer coroutine. Pulled out of `KtorRelay` so that file
 * stays focused on Ktor wiring; the slow-client / backpressure
 * policy now lives next to the data structures it manages.
 *
 * Lifecycle:
 *   1. `connect(server, registerSession)` opens a [RelaySession],
 *      registers it with the supplied callback, and starts the
 *      writer coroutine that drains [outQueue] into [outgoing].
 *   2. `pump()` reads inbound frames until the socket closes.
 *   3. `finally`-style teardown closes the queue, cancels the
 *      writer, unregisters the session, and closes it.
 *
 * Slow-client policy: once the outbound backlog reaches
 * [MAX_OUTGOING_BUFFER] frames, the connection is dropped rather
 * than silently losing EVENT/EOSE — silent drop would corrupt
 * NIP-01.
 *
 * Memory model: the outbound queue is `Channel.UNLIMITED`, which in
 * kotlinx.coroutines allocates segments lazily — an idle connection
 * pays only a small head-segment cost. The cap is enforced via
 * [outstanding] rather than the channel's own capacity so we don't
 * reserve a fixed-size buffer up-front for every connection. At
 * 5 000+ idle connections this matters: an 8 192-slot fixed buffer
 * per connection would otherwise dominate JVM heap usage even
 * though the vast majority of connections never fan out.
 */
internal class WebSocketSessionPump(
    private val ws: DefaultWebSocketServerSession,
) {
    /**
     * Unbounded channel — bounded by [outstanding] above, not by the
     * channel's own capacity. See class kdoc for memory rationale.
     */
    private val outQueue = Channel<String>(capacity = Channel.UNLIMITED)

    /**
     * Number of frames queued but not yet written to the socket.
     * Producer increments before [Channel.trySend]; writer decrements
     * after the frame is handed to Ktor. When this would cross
     * [MAX_OUTGOING_BUFFER] we treat the client as slow and close
     * the queue.
     */
    private val outstanding = AtomicInteger(0)

    @Volatile private var droppedForBackpressure = false

    suspend fun pump(
        server: NostrServer,
        registerSession: (RelaySession) -> Unit,
        unregisterSession: (RelaySession) -> Unit,
        /** Who this socket counts against for per-client limits; see [ClientAddresses]. */
        clientAddress: String? = null,
    ) {
        val writerJob =
            ws.launch {
                try {
                    for (json in outQueue) {
                        ws.outgoing.send(Frame.Text(json))
                        outstanding.decrementAndGet()
                    }
                } catch (_: ClosedSendChannelException) {
                    // socket closed — outer handler runs normal teardown.
                }
            }
        val session =
            server.connect(
                SessionSink.of { json ->
                    // Backpressure, not instant drop. A full backlog usually
                    // means a REQ replay is producing faster than the socket
                    // writer ships — normal for bulk downloads to a HEALTHY
                    // client — so pace the producer (this blocks the replay
                    // coroutine's thread; the live-fanout path already
                    // documents that a slow sub blocks the ingest writer).
                    // Only a client that stays behind for the whole deadline
                    // is genuinely slow; then close FOR REAL. The previous
                    // code dropped at the cap immediately — killing healthy
                    // bulk clients the moment the O(n²)-replay throttle was
                    // fixed — and only closed outQueue, leaving the socket
                    // half-dead (no EOSE, no close frame; the client hung).
                    var waitedMs = 0L
                    while (outstanding.get() >= MAX_OUTGOING_BUFFER && !droppedForBackpressure) {
                        if (waitedMs >= SLOW_CLIENT_DEADLINE_MS) {
                            droppedForBackpressure = true
                            outQueue.close()
                            // Actually terminate the connection so the client
                            // sees the failure instead of waiting forever.
                            ws.cancel()
                            return@of
                        }
                        Thread.sleep(BACKPRESSURE_POLL_MS)
                        waitedMs += BACKPRESSURE_POLL_MS
                    }
                    if (droppedForBackpressure) return@of

                    outstanding.incrementAndGet()
                    val res = outQueue.trySend(json)
                    if (!res.isSuccess) {
                        // Channel was closed concurrently (e.g. teardown).
                        // Roll back the counter; nothing more to do.
                        outstanding.decrementAndGet()
                    }
                },
                clientAddress,
            )
        registerSession(session)
        try {
            ws.incoming.consumeEach { frame ->
                if (droppedForBackpressure) return@consumeEach
                if (frame is Frame.Text) {
                    session.receive(frame.readText())
                }
            }
        } catch (_: FrameTooBigException) {
            // Ktor has queued the 1009 close already. Rethrowing fails the call, which
            // drops the socket before that frame is written; returning lets Ktor close
            // the session and wait for it.
        } catch (_: ProtocolViolationException) {
            // Same, with the 1002 close.
        } finally {
            outQueue.close()
            writerJob.cancel()
            unregisterSession(session)
            session.close()
        }
    }

    companion object {
        /**
         * Per-session outbound backlog cap. When a slow client falls
         * this many frames behind, we close their connection rather
         * than silently dropping further frames (which would corrupt
         * NIP-01 by missing EVENT/EOSE messages).
         *
         * Sized to hold fan-out for a connection holding several
         * thousand subscriptions when one event matches all of them
         * — the realistic upper bound for a relay client. At ~250 B
         * per frame this caps per-session worst-case memory at
         * ~2 MiB before we drop the connection. Idle connections
         * pay only the small head-segment cost of an unlimited
         * channel (≈ a few hundred bytes), not the full cap.
         */
        const val MAX_OUTGOING_BUFFER: Int = 8192

        /**
         * How long a producer will wait for the writer to drain a full
         * backlog before the client is declared slow and disconnected.
         * A healthy client on any sane link drains 8192 frames orders of
         * magnitude faster than this.
         */
        const val SLOW_CLIENT_DEADLINE_MS: Long = 30_000

        /** Poll interval while pacing a producer against a full backlog. */
        const val BACKPRESSURE_POLL_MS: Long = 5
    }
}
