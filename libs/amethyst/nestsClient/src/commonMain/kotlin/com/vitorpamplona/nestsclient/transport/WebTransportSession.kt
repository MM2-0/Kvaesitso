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

import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic WebTransport session, as produced by a successful Extended
 * CONNECT (RFC 9220) handshake.
 *
 * The MoQ layer talks to this interface; the production implementation is
 * [com.vitorpamplona.nestsclient.transport.QuicWebTransportFactory] which
 * sits on top of the pure-Kotlin `:quic` stack. Keeping this abstract lets
 * us:
 *   - unit-test the MoQ framing layer with [FakeWebTransport],
 *   - swap transport implementations (a different QUIC backend, or a
 *     browser-backed bridge as a contingency) without touching audio/UI
 *     code.
 *
 * Lifecycle: the session is opened via [WebTransportFactory.connect] and must
 * be closed with [close] to release the underlying QUIC connection.
 */
interface WebTransportSession {
    /** True once the Extended CONNECT exchange has returned 2xx and before [close] is called. */
    val isOpen: Boolean

    /**
     * The sub-protocol the server selected via the
     * `wt-protocol` response header (RFC 8941 SF-string;
     * draft-ietf-webtrans-http3 §3.3). `null` if the server didn't
     * echo a sub-protocol — typically because the client offered
     * none, or because the server is older than draft-13 and
     * supports only a single protocol implicitly.
     *
     * Used by moq-lite negotiation: a client that advertised both
     * `moq-lite-04` and `moq-lite-03` reads this to select the
     * matching [com.vitorpamplona.nestsclient.moq.lite.MoqLiteVersion]
     * for its [com.vitorpamplona.nestsclient.moq.lite.MoqLiteSession].
     */
    val negotiatedSubProtocol: String?

    /**
     * Open a new bidirectional WebTransport stream. The returned [WebTransportBidiStream]
     * is writable + readable and closes when either peer half-closes.
     */
    suspend fun openBidiStream(): WebTransportBidiStream

    /**
     * Open a new client-initiated unidirectional WebTransport stream.
     *
     * Required by the moq-lite (Lite-03) publisher path: each group of
     * audio frames is pushed on a fresh uni stream that the publisher
     * opens — see `rs/moq-lite/src/lite/publisher.rs:338`
     * (`session.open_uni()`).
     *
     * If [bestEffort] is true, the underlying QUIC stream drops lost
     * STREAM bytes instead of retransmitting them — for real-time
     * audio (Opus group streams) this avoids pushing 200-ms-stale
     * frames after a loss. Default false (RFC 9000 §3.5 reliable byte
     * sequence).
     */
    suspend fun openUniStream(bestEffort: Boolean = false): WebTransportWriteStream

    /**
     * Flow of inbound unidirectional streams initiated by the peer.
     *
     * The flow completes when [close] is called or the peer tears down the session.
     */
    fun incomingUniStreams(): Flow<WebTransportReadStream>

    /**
     * Flow of inbound bidirectional streams initiated by the peer.
     *
     * Required by the moq-lite (Lite-03) publisher path: kixelated/moq-rs
     * relays open Announce and Subscribe bidi streams *to* the publisher
     * — see `rs/moq-lite/src/lite/publisher.rs:40` (`Stream::accept(session)`).
     * The flow completes when [close] is called or the peer tears down
     * the session.
     */
    fun incomingBidiStreams(): Flow<WebTransportBidiStream>

    /** Send a QUIC datagram; returns false if the datagram was dropped by congestion control. */
    suspend fun sendDatagram(payload: ByteArray): Boolean

    /** Flow of inbound datagrams. */
    fun incomingDatagrams(): Flow<ByteArray>

    /** Gracefully close the session with an optional application-level code + reason. */
    suspend fun close(
        code: Int = 0,
        reason: String = "",
    )
}

/** Writable + readable half of a bidirectional WebTransport stream. */
interface WebTransportBidiStream :
    WebTransportReadStream,
    WebTransportWriteStream

/** Read-only WebTransport stream (either a received uni stream or the read half of a bidi). */
interface WebTransportReadStream {
    /** Flow of chunks as they arrive. Completes when the peer closes its write side. */
    fun incoming(): Flow<ByteArray>

    /**
     * RFC 9000 §3.5: ask the peer to stop sending on this stream.
     * Causes a `STOP_SENDING(applicationErrorCode)` frame to land at
     * the peer, which typically responds with `RESET_STREAM` carrying
     * the same code. Call this when the application no longer needs
     * the stream's bytes — it lets the peer abandon any pending
     * retransmits instead of wasting bandwidth on data we'd discard.
     *
     * First call wins per `:quic`'s `QuicStream.stopSending`
     * lock-free first-call-wins gate; subsequent calls (including
     * with a different code) are silently ignored to keep the wire
     * frame stable.
     *
     * Used by moq-lite Lite-03's group-cancel path: a listener that
     * decides a specific group is too stale can `stopSending` its
     * uni stream so the publisher abandons any in-flight retransmits
     * instead of wasting bandwidth on bytes the listener will discard.
     *
     * Implementations that don't model receive-side cancellation
     * (e.g. the in-memory fake when no test asserts on it) MAY
     * treat this as a no-op.
     */
    suspend fun stopSending(errorCode: Long)
}

/** Write-only WebTransport stream. */
interface WebTransportWriteStream {
    suspend fun write(chunk: ByteArray)

    /** Half-close the write side (FIN). No further writes after this call. */
    suspend fun finish()

    /**
     * RFC 9000 §3.5: send `RESET_STREAM(applicationErrorCode)` on
     * this stream's send half, abandoning any pending bytes. Distinct
     * from [finish], which is a graceful FIN — `reset` carries a
     * typed error code the peer can act on.
     *
     * First call wins per `:quic`'s `QuicStream.resetStream`
     * lock-free first-call-wins gate; subsequent calls (and any
     * subsequent [write] / [finish]) are silently ignored to keep
     * the wire frame stable.
     *
     * Used by moq-lite Lite-03's typed cancel paths: a publisher
     * that rejects a Subscribe (e.g. broadcast / track does not
     * exist) follows the SubscribeDrop body with a
     * `RESET_STREAM(errorCode)` so the subscriber sees a typed
     * application reason rather than the ambiguous "publisher FINed
     * the bidi" signal that overlaps with a graceful publisher
     * shutdown.
     *
     * Implementations that don't model sender-driven reset (e.g.
     * the in-memory fake when no test asserts on it) MAY treat this
     * as a [finish].
     */
    suspend fun reset(errorCode: Long)

    /**
     * Hint to the transport about this stream's drain priority relative
     * to other streams on the same session. Higher value drains first
     * under congestion; same-priority streams keep round-robin order.
     * Default 0 = unchanged round-robin behaviour.
     *
     * moq-lite uses this to bias the writer toward newer group streams
     * (sequence-numbered, fresher audio) so a backlog of retransmits on
     * an older group doesn't starve the listener of fresh frames. See
     * `Publisher::serve_group` in `rs/moq-lite/src/lite/publisher.rs`.
     *
     * Implementations that don't model priority (e.g. the in-memory
     * fake) MAY treat this as a no-op.
     */
    fun setPriority(priority: Int)
}

/**
 * Factory that opens a [WebTransportSession] against a nests MoQ endpoint.
 *
 * [authority] is the `host:port` of the WT server, [path] is the URL path
 * (nests defaults to `/moq`), and [bearerToken] is typically the token
 * returned by the `/api/v1/nests/<roomId>` HTTP call (see [NestsClient]).
 */
interface WebTransportFactory {
    suspend fun connect(
        authority: String,
        path: String,
        bearerToken: String? = null,
    ): WebTransportSession
}

/**
 * Single transport-layer exception type, so UI code only has to differentiate
 * on [kind] rather than catching library-specific classes.
 */
class WebTransportException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    enum class Kind {
        /** DNS / TCP / TLS / QUIC handshake failed. */
        HandshakeFailed,

        /** WebTransport Extended CONNECT returned a non-2xx response. */
        ConnectRejected,

        /** Peer closed the session or a stream unexpectedly. */
        PeerClosed,

        /** The implementation does not yet support the requested operation. */
        NotImplemented,
    }
}
