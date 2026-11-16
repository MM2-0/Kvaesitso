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
package com.vitorpamplona.geode.interop

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySession
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.test.fail

/**
 * Equivalent of strfry's `test/syncTest.pl` driver for our interop
 * tests. Drives one round of NIP-77 *negotiation* (NEG-OPEN /
 * NEG-MSG / NEG-CLOSE) against a real `ws://` endpoint — a Geode
 * `KtorRelay`, a strfry process, or any other NIP-77 relay.
 *
 * The driver opens a raw WebSocket — no `NostrClient` overhead —
 * to keep the wire format under direct control, the same way
 * `strfry sync` does. That way we exercise the server's NEG-OPEN /
 * NEG-MSG / NEG-CLOSE handling with no client-side framing or
 * filter-management indirection.
 *
 * Note: this driver only computes the symmetric difference. The
 * actual *sync* (REQ for `needIds`, EVENT for `haveIds`) is the
 * caller's job; that's a NIP-01 follow-up, not part of NIP-77.
 */
class InteropSyncDriver(
    private val httpClient: OkHttpClient = OkHttpClient.Builder().build(),
) {
    /**
     * Negotiates the symmetric difference between `localEvents` and
     * the relay at [wsUrl] under [filter]. Returns the id sets so
     * the caller can close the loop with REQ / EVENT.
     *
     * @param wsUrl the source relay's `ws://…` URL.
     * @param filter NEG-OPEN filter — usually the broadest filter the
     *   sync should cover (e.g. `Filter(kinds = listOf(1))`).
     * @param localEvents events the caller already has; the relay
     *   reconciles these against its own snapshot.
     * @param frameSizeLimit `0` lets the relay choose. We pass `0`
     *   here so the relay's configured cap (500_000 by default) is
     *   what governs framing — same shape as `strfry sync`.
     * @param timeoutMs hard timeout on a single NEG-MSG round trip.
     * @param maxRounds upper bound on round trips. Strfry typically
     *   converges in ≤5 rounds for 100 k corpora; 64 is a generous
     *   safety net that catches pathological splits without hanging
     *   tests forever.
     */
    suspend fun negotiate(
        wsUrl: String,
        filter: Filter,
        localEvents: List<Event>,
        subId: String = "interop-sync",
        frameSizeLimit: Long = 0,
        timeoutMs: Long = 30_000L,
        maxRounds: Int = 64,
    ): Result {
        val incoming = Channel<String>(UNLIMITED)
        val ws =
            httpClient.newWebSocket(
                Request.Builder().url(wsUrl.replace("ws://", "http://")).build(),
                object : WebSocketListener() {
                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        incoming.trySend(text)
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        incoming.close()
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        incoming.close(t)
                    }
                },
            )

        return try {
            val session = NegentropySession.fromEvents(subId, filter, localEvents, frameSizeLimit)

            // Step 1: NEG-OPEN.
            check(ws.send(OptimizedJsonMapper.toJson(session.open()))) { "send NEG-OPEN failed" }

            // Step 2: drive NEG-MSG round trips until the client-side
            // session reports completion.
            val haveIds = mutableSetOf<HexKey>()
            val needIds = mutableSetOf<HexKey>()
            var rounds = 0
            while (rounds < maxRounds) {
                val raw = withTimeout(timeoutMs) { incoming.receive() }
                val msg = OptimizedJsonMapper.fromJsonToMessage(raw)
                rounds++
                when (msg) {
                    is NegErrMessage -> {
                        return Result(
                            haveIds = haveIds,
                            needIds = needIds,
                            rounds = rounds,
                            error = "${msg.subId}: ${msg.reason}",
                        )
                    }

                    is NoticeMessage -> {
                        return Result(
                            haveIds = haveIds,
                            needIds = needIds,
                            rounds = rounds,
                            error = "NOTICE: ${msg.message}",
                        )
                    }

                    is NegMsgMessage -> {
                        val r = session.processMessage(msg.message)
                        haveIds += r.haveIds
                        needIds += r.needIds
                        if (r.isComplete()) {
                            return Result(haveIds, needIds, rounds, error = null)
                        }
                        check(ws.send(OptimizedJsonMapper.toJson(r.nextCmd!!))) {
                            "send NEG-MSG failed"
                        }
                    }

                    else -> {
                        fail("unexpected message during NEG sync: ${msg::class.simpleName}")
                    }
                }
            }
            Result(haveIds, needIds, rounds, error = "did not converge in $maxRounds rounds")
        } finally {
            ws.close(1000, "interop-test-done")
        }
    }

    /**
     * Result of a reconciliation. `error` is non-null on
     * NEG-ERR/NOTICE/timeout; otherwise the id-set fields are
     * authoritative.
     *
     * @param haveIds events the client (us) had that the relay did not.
     * @param needIds events the relay had that the client (us) lacked.
     * @param rounds NEG-MSG round trips, including the one carrying
     *   the terminator.
     */
    data class Result(
        val haveIds: Set<HexKey>,
        val needIds: Set<HexKey>,
        val rounds: Int,
        val error: String?,
    )
}
