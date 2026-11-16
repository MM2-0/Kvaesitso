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
package com.vitorpamplona.nestsclient.transport

import com.vitorpamplona.nestsclient.transport.FakeWebTransport.Companion.pair
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [WebTransportSession] used by tests for MoQ framing / session logic.
 *
 * A pair of fakes is connected via [pair] — anything written on one side is
 * delivered on the other. This deliberately simulates *success* semantics
 * only (no packet loss, no congestion); the real `:quic`-backed transport
 * exercises those codepaths via its own pipe + interop tests.
 *
 * [incomingDatagrams] and [FakeBidiStream.incoming] use [receiveAsFlow]
 * semantics: a `take(1)` / `first()` followed by a long-running `collect`
 * works on the same underlying channel, and cancelling a consumer does not
 * close the channel for future readers.
 */
class FakeWebTransport private constructor(
    private val outboundDatagrams: Channel<ByteArray>,
    private val inboundDatagrams: Channel<ByteArray>,
    private val outboundBidiStreams: Channel<FakeBidiStream>,
    private val inboundBidiStreams: Channel<FakeBidiStream>,
    private val inboundUniStreams: Channel<WebTransportReadStream>,
    private val outboundUniStreams: Channel<WebTransportReadStream>,
    /**
     * Test-controllable negotiated sub-protocol. Defaults to `null`
     * — production fakes that don't care about ALPN negotiation
     * keep the same behavior they had before this field existed.
     * Tests that exercise the version-aware [MoqLiteSession] can
     * pass a non-null value via [pair] to simulate a relay that
     * picked Lite-04 (or any other ALPN string).
     */
    override val negotiatedSubProtocol: String? = null,
) : WebTransportSession {
    private val stateLock = Mutex()
    private var open = true

    override val isOpen: Boolean get() = open

    override suspend fun openBidiStream(): WebTransportBidiStream {
        stateLock.withLock { check(open) { "session closed" } }
        val localToPeer = Channel<ByteArray>(Channel.BUFFERED)
        val peerToLocal = Channel<ByteArray>(Channel.BUFFERED)
        // Shared error-code cells: each side records its own reset()
        // and stopSending() codes here so the peer can introspect.
        // `localResetCell` records resets on `local.reset(code)` (=
        // peer's `lastPeerResetCode`), and vice-versa.
        val localResetCell =
            java.util.concurrent.atomic
                .AtomicLong(NO_CODE)
        val peerResetCell =
            java.util.concurrent.atomic
                .AtomicLong(NO_CODE)
        val localStopSendingCell =
            java.util.concurrent.atomic
                .AtomicLong(NO_CODE)
        val peerStopSendingCell =
            java.util.concurrent.atomic
                .AtomicLong(NO_CODE)
        val local =
            FakeBidiStream(
                write = localToPeer,
                read = peerToLocal,
                myResetCell = localResetCell,
                myStopSendingCell = localStopSendingCell,
                peerResetCell = peerResetCell,
                peerStopSendingCell = peerStopSendingCell,
            )
        val peer =
            FakeBidiStream(
                write = peerToLocal,
                read = localToPeer,
                myResetCell = peerResetCell,
                myStopSendingCell = peerStopSendingCell,
                peerResetCell = localResetCell,
                peerStopSendingCell = localStopSendingCell,
            )
        // Send *peer* half to the other side's inbound queue.
        outboundBidiStreams.send(peer)
        return local
    }

    override fun incomingUniStreams(): Flow<WebTransportReadStream> = inboundUniStreams.receiveAsFlow()

    override fun incomingBidiStreams(): Flow<WebTransportBidiStream> = inboundBidiStreams.receiveAsFlow()

    /**
     * Open a uni stream toward the paired peer — production flow used by
     * the moq-lite publisher path to push group data. The peer side
     * receives the new stream via [incomingUniStreams].
     */
    override suspend fun openUniStream(bestEffort: Boolean): WebTransportWriteStream {
        // The fake transport ignores [bestEffort] — there's no loss to
        // simulate in the in-memory channel.
        stateLock.withLock { check(open) { "session closed" } }
        val pipe = Channel<ByteArray>(Channel.BUFFERED)
        // Shared priority cell: the writer (us) stores its most recent
        // setPriority call here; the peer-side reader exposes it via
        // [FakeReadStream.lastSetPriority] so tests can verify the
        // priority value the moq-lite publisher computed without
        // peeking into private state. Defaults to 0 (the
        // [WebTransportWriteStream.setPriority] kdoc default).
        val priorityCell =
            java.util.concurrent.atomic
                .AtomicInteger(0)
        // Shared reset / stopSending cells. The writer (us) records its
        // `reset(code)` call here; the peer-side reader exposes it via
        // `FakeReadStream.lastResetCode`. Symmetric: the reader's
        // `stopSending(code)` call lands in `stopSendingCell` so the
        // writer (= moq-lite publisher tests) can assert the listener
        // canceled with the expected code.
        val resetCell =
            java.util.concurrent.atomic
                .AtomicLong(NO_CODE)
        val stopSendingCell =
            java.util.concurrent.atomic
                .AtomicLong(NO_CODE)
        outboundUniStreams.send(FakeReadStream(pipe, priorityCell, resetCell, stopSendingCell))
        return ChannelWriteStream(pipe, priorityCell, resetCell, stopSendingCell)
    }

    override suspend fun sendDatagram(payload: ByteArray): Boolean {
        if (!open) return false
        outboundDatagrams.send(payload)
        return true
    }

    override fun incomingDatagrams(): Flow<ByteArray> = inboundDatagrams.receiveAsFlow()

    override suspend fun close(
        code: Int,
        reason: String,
    ) {
        stateLock.withLock {
            if (!open) return
            open = false
        }
        outboundDatagrams.close()
        inboundDatagrams.close()
        outboundBidiStreams.close()
        inboundBidiStreams.close()
        inboundUniStreams.close()
        outboundUniStreams.close()
    }

    /**
     * Flow of peer-opened bidi streams (i.e. the local endpoint of a stream the
     * other side created via [openBidiStream]).
     */
    fun peerOpenedBidiStreams(): Flow<FakeBidiStream> = inboundBidiStreams.receiveAsFlow()

    companion object {
        /**
         * Create two linked fakes that act as "client" and "server" endpoints of
         * the same virtual WebTransport session. Datagrams and streams opened on
         * one side appear on the other.
         *
         * @param negotiatedSubProtocol surfaced by both sides via
         *   [WebTransportSession.negotiatedSubProtocol]. Defaults to
         *   `null`. Tests that drive a version-aware
         *   [com.vitorpamplona.nestsclient.moq.lite.MoqLiteSession]
         *   pass e.g. `"moq-lite-04"` here.
         */
        fun pair(negotiatedSubProtocol: String? = null): Pair<FakeWebTransport, FakeWebTransport> {
            val aToBDatagrams = Channel<ByteArray>(Channel.BUFFERED)
            val bToADatagrams = Channel<ByteArray>(Channel.BUFFERED)
            val aToBBidi = Channel<FakeBidiStream>(Channel.BUFFERED)
            val bToABidi = Channel<FakeBidiStream>(Channel.BUFFERED)
            val aToBUni = Channel<WebTransportReadStream>(Channel.BUFFERED)
            val bToAUni = Channel<WebTransportReadStream>(Channel.BUFFERED)

            val a =
                FakeWebTransport(
                    outboundDatagrams = aToBDatagrams,
                    inboundDatagrams = bToADatagrams,
                    outboundBidiStreams = aToBBidi,
                    inboundBidiStreams = bToABidi,
                    inboundUniStreams = bToAUni,
                    outboundUniStreams = aToBUni,
                    negotiatedSubProtocol = negotiatedSubProtocol,
                )
            val b =
                FakeWebTransport(
                    outboundDatagrams = bToADatagrams,
                    inboundDatagrams = aToBDatagrams,
                    outboundBidiStreams = bToABidi,
                    inboundBidiStreams = aToBBidi,
                    inboundUniStreams = aToBUni,
                    outboundUniStreams = bToAUni,
                    negotiatedSubProtocol = negotiatedSubProtocol,
                )
            return a to b
        }
    }
}

class FakeBidiStream internal constructor(
    private val write: Channel<ByteArray>,
    private val read: Channel<ByteArray>,
    /**
     * Cell that records this side's `reset(code)` calls. Read by
     * [lastResetCode] from the same side (rare — usually it's the
     * peer's [peerResetCell] you want).
     */
    private val myResetCell: java.util.concurrent.atomic.AtomicLong? = null,
    private val myStopSendingCell: java.util.concurrent.atomic.AtomicLong? = null,
    /**
     * Cell that records the *peer's* `reset(code)` calls (i.e.
     * mirror of the peer's [myResetCell]). [lastPeerResetCode] reads
     * this so tests can assert "the peer reset the bidi with code X."
     */
    private val peerResetCell: java.util.concurrent.atomic.AtomicLong? = null,
    private val peerStopSendingCell: java.util.concurrent.atomic.AtomicLong? = null,
) : WebTransportBidiStream {
    override fun incoming(): Flow<ByteArray> = read.receiveAsFlow()

    override suspend fun write(chunk: ByteArray) {
        write.send(chunk)
    }

    override suspend fun finish() {
        write.close()
    }

    override suspend fun reset(errorCode: Long) {
        // First call wins, mirroring `:quic`'s `QuicStream.resetStream`
        // contract. Subsequent reset/finish/write are silently
        // dropped to keep the wire frame stable in the eyes of any
        // test that reads `lastResetCode`.
        myResetCell?.compareAndSet(NO_CODE, errorCode)
        write.close()
    }

    override suspend fun stopSending(errorCode: Long) {
        myStopSendingCell?.compareAndSet(NO_CODE, errorCode)
        // The peer's send half is our receive half; close it so a
        // collector sees end-of-flow.
        read.close()
    }

    /**
     * Code our side passed to [reset], or `null` if reset wasn't
     * called. Mostly useful when a test holds the peer's reference;
     * [lastPeerResetCode] is the more common assertion.
     */
    val lastResetCode: Long?
        get() = myResetCell?.get()?.takeIf { it != NO_CODE }

    /** Code the peer side passed to [reset], or `null` if not called. */
    val lastPeerResetCode: Long?
        get() = peerResetCell?.get()?.takeIf { it != NO_CODE }

    val lastStopSendingCode: Long?
        get() = myStopSendingCell?.get()?.takeIf { it != NO_CODE }

    val lastPeerStopSendingCode: Long?
        get() = peerStopSendingCell?.get()?.takeIf { it != NO_CODE }

    override fun setPriority(priority: Int) = Unit
}

class FakeReadStream internal constructor(
    private val read: Channel<ByteArray>,
    /**
     * Optional shared cell with the writer side. When present, exposes
     * the most recent [WebTransportWriteStream.setPriority] value the
     * writer applied. `null` for read streams not paired with a fake
     * uni-stream writer (e.g. tests that hand-roll a [Channel]).
     */
    private val priorityCell: java.util.concurrent.atomic.AtomicInteger? = null,
    /**
     * Optional shared cell that records the writer's `reset(code)`
     * call. Exposed via [lastResetCode] so tests can assert the
     * publisher reset its uni stream with the expected typed error.
     */
    private val resetCell: java.util.concurrent.atomic.AtomicLong? = null,
    /**
     * Optional shared cell that records this side's own
     * `stopSending(code)` call. Exposed via [lastStopSendingCode]
     * so the test can assert the listener actually fired the cancel.
     */
    private val stopSendingCell: java.util.concurrent.atomic.AtomicLong? = null,
) : WebTransportReadStream {
    override fun incoming(): Flow<ByteArray> = read.receiveAsFlow()

    override suspend fun stopSending(errorCode: Long) {
        // First-call-wins like the production path. Closing `read`
        // here would race the production path's "publisher closes
        // its write end" semantic — moq-lite calls `stopSending`
        // when it's done caring about the bytes, but the publisher
        // is still expected to finish what it was sending. We
        // record the code without closing so a test that asserts
        // `incoming().toList()` post-stopSending still sees any
        // bytes the publisher had already enqueued.
        stopSendingCell?.compareAndSet(NO_CODE, errorCode)
    }

    /**
     * Last priority value the paired write side applied via
     * [WebTransportWriteStream.setPriority], or `0` if no priority was
     * ever set. Returns `null` when this read stream isn't paired with
     * a fake uni-stream writer.
     */
    val lastSetPriority: Int?
        get() = priorityCell?.get()

    /**
     * Code the peer-side writer passed to [WebTransportWriteStream.reset],
     * or `null` if reset wasn't called.
     */
    val lastResetCode: Long?
        get() = resetCell?.get()?.takeIf { it != NO_CODE }

    /** Code we passed to [stopSending], or `null` if not called. */
    val lastStopSendingCode: Long?
        get() = stopSendingCell?.get()?.takeIf { it != NO_CODE }
}

/**
 * Write-only adapter over a [Channel]. Backs both
 * [FakeWebTransport.openUniStream] (production: locally-opened uni
 * stream that the paired peer reads via incomingUniStreams) and any
 * test that wants to drive uni-stream bytes through a known channel.
 *
 * Public so tests that hold the writer reference (e.g. via
 * `serverSide.openUniStream()`) can introspect the peer-side
 * `stopSending` code via [peerStopSendingCode] without racing the
 * listener's uni-stream pump on the read side.
 */
class ChannelWriteStream internal constructor(
    private val channel: Channel<ByteArray>,
    /**
     * Optional shared cell with the peer-side reader. When present,
     * stores each [setPriority] call so the peer can introspect.
     */
    private val priorityCell: java.util.concurrent.atomic.AtomicInteger? = null,
    private val resetCell: java.util.concurrent.atomic.AtomicLong? = null,
    /**
     * Cell that records the *peer's* `stopSending(code)` call, since
     * the writer is on the other side of the stream from the
     * `stopSending` caller. Same cell that
     * [FakeReadStream.lastStopSendingCode] reads.
     */
    private val stopSendingCell: java.util.concurrent.atomic.AtomicLong? = null,
) : WebTransportWriteStream {
    override suspend fun write(chunk: ByteArray) {
        channel.send(chunk)
    }

    override suspend fun finish() {
        channel.close()
    }

    override suspend fun reset(errorCode: Long) {
        // First-call-wins. We close the channel so the peer's
        // receive flow ends — same shape as `finish` but with a
        // typed error code recorded for the peer to introspect.
        resetCell?.compareAndSet(NO_CODE, errorCode)
        channel.close()
    }

    override fun setPriority(priority: Int) {
        priorityCell?.set(priority)
    }

    /**
     * Code the peer-side reader passed to
     * [WebTransportReadStream.stopSending], or `null` if not called.
     * Lets a test that holds the writer side inspect the listener's
     * group-cancel without racing for the peer-side
     * [WebTransportReadStream] reference.
     */
    val peerStopSendingCode: Long?
        get() = stopSendingCell?.get()?.takeIf { it != NO_CODE }
}

/**
 * Sentinel for "no reset / stopSending code recorded yet" in the
 * [java.util.concurrent.atomic.AtomicLong] cells used by [FakeBidiStream]
 * and [FakeReadStream] / [ChannelWriteStream]. Real moq-lite codes are
 * non-negative varints (RFC 9000 application error codes are u32
 * unsigned-fitting-in-Long); `Long.MIN_VALUE` is safely outside that
 * range.
 */
internal const val NO_CODE: Long = Long.MIN_VALUE
