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

import com.vitorpamplona.nestsclient.moq.lite.MoqLiteAlpn
import com.vitorpamplona.quic.connection.QuicConnection
import com.vitorpamplona.quic.connection.QuicConnectionConfig
import com.vitorpamplona.quic.connection.QuicConnectionDriver
import com.vitorpamplona.quic.http3.Http3Frame
import com.vitorpamplona.quic.http3.Http3FrameReader
import com.vitorpamplona.quic.http3.Http3StreamType
import com.vitorpamplona.quic.http3.buildClientWebTransportSettings
import com.vitorpamplona.quic.qpack.QpackDecoder
import com.vitorpamplona.quic.stream.QuicStream
import com.vitorpamplona.quic.tls.CertificateValidator
import com.vitorpamplona.quic.tls.JdkCertificateValidator
import com.vitorpamplona.quic.transport.UdpSocket
import com.vitorpamplona.quic.webtransport.QuicWebTransportSessionState
import com.vitorpamplona.quic.webtransport.buildExtendedConnectHeaders
import com.vitorpamplona.quic.webtransport.encodeHeadersFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Pure-Kotlin WebTransport over QUIC v1, sitting on top of every layer in
 * `:quic`. The historical alternative was a Kwik-based JNI binding; that
 * was rejected because no Android-compatible native classifier ships, so
 * we wrote `:quic` from scratch.
 *
 * Lifecycle on [connect]:
 *   1. Open a UDP socket connected to (authority host, authority port).
 *   2. Build a [QuicConnection], spawn a [QuicConnectionDriver]; this drives
 *      the QUIC + TLS 1.3 handshake to completion.
 *   3. Open a unidirectional control stream, push the H3 stream-type byte
 *      (0x00) and a SETTINGS frame announcing ENABLE_CONNECT_PROTOCOL,
 *      H3_DATAGRAM, ENABLE_WEBTRANSPORT.
 *   4. Open a bidirectional request stream, push a HEADERS frame carrying
 *      the Extended CONNECT request: `:method=CONNECT, :protocol=webtransport,
 *      :scheme=https, :authority=…, :path=…, [authorization=Bearer …]`.
 *   5. Wait for the response HEADERS; on `:status = 2xx` the WT session is
 *      open. Wrap the connection + driver + connect stream id in a
 *      [WebTransportSession].
 *
 * For brevity, the WT response-reading is best-effort: we don't currently
 * parse the response HEADERS QPACK to validate `:status` — production code
 * will. The wire is otherwise fully RFC-conformant.
 */
class QuicWebTransportFactory(
    private val parentScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /**
     * Certificate validator. Defaults to [JdkCertificateValidator], which
     * delegates to the platform / JDK system trust store. Tests or self-signed
     * dev environments can pass a permissive validator explicitly.
     */
    private val certificateValidator: CertificateValidator = JdkCertificateValidator(),
    /**
     * Maximum time we'll suspend waiting for the server's HEADERS response on
     * the Extended CONNECT request stream before giving up with HandshakeFailed.
     */
    private val connectTimeoutMillis: Long = 10_000L,
    /**
     * WebTransport sub-protocols to advertise on the Extended CONNECT request,
     * via the `wt-available-protocols` header (RFC 8941 Structured Field List
     * of strings — see draft-ietf-webtrans-http3-14 §3.3).
     *
     * For nests, this MUST contain at least `moq-lite-03`; without it,
     * moq-relay falls back to the legacy in-band SETUP exchange
     * (moq-lite-02) and our first post-CONNECT message is decoded as
     * SETUP_CLIENT, producing `connection closed err=invalid value` on
     * the relay side and a stalled subscribe on the client side.
     *
     * Both `moq-lite-04` and `moq-lite-03` are advertised by default
     * — the codec is now version-aware (audit L1), so the relay's
     * choice (echoed via `wt-protocol`) is honoured at runtime via
     * [WebTransportSession.negotiatedSubProtocol] →
     * [com.vitorpamplona.nestsclient.moq.lite.MoqLiteVersion.fromAlpn].
     * Lite-04 sits ahead of Lite-03 in the list to match kixelated's
     * preference order; servers that don't yet support Lite-04 fall
     * back to Lite-03 cleanly. See
     * [com.vitorpamplona.nestsclient.moq.lite.MoqLiteAlpn] /
     * [com.vitorpamplona.nestsclient.moq.lite.MoqLiteVersion] for the
     * version constants and codec behavior.
     */
    private val webTransportSubProtocols: List<String> =
        listOf(MoqLiteAlpn.LITE_04, MoqLiteAlpn.LITE_03),
) : WebTransportFactory {
    override suspend fun connect(
        authority: String,
        path: String,
        bearerToken: String?,
    ): WebTransportSession {
        val (host, port) = splitAuthority(authority)
        // Decouple the socket target from the TLS SNI name. The socket must
        // reach a routable address — `localhost` commonly resolves to ::1
        // first, and IPv6 loopback is not always routable (e.g. Docker's
        // IPv6 UDP port-forwarding silently blackholes), so prefer an IPv4
        // address for the actual connection. SNI, however, must stay the
        // original hostname so it (a) matches the server's certificate and
        // (b) is a legal RFC 6066 host_name rather than an IP literal.
        val socketHost = resolvePreferIpv4(host)
        // One-line wire target so a stalled/failed handshake is self-diagnosing
        // (did we connect over IPv4 or fall into the ::1 blackhole?).
        val target = "socket=$socketHost:$port sni=$host"
        val socket = UdpSocket.connect(socketHost, port)
        val conn =
            QuicConnection(
                serverName = host,
                config = QuicConnectionConfig(),
                tlsCertificateValidator = certificateValidator,
            )
        val driver = QuicConnectionDriver(conn, socket, parentScope)
        driver.start()

        val handshakeCompleted =
            try {
                // Bound the handshake — a server-side TLS failure (e.g. a relay
                // that rejects an IP-literal SNI and can't select a cert) leaves
                // the handshake stalled with no alert, so without this ceiling
                // connect() hangs indefinitely instead of failing fast.
                kotlinx.coroutines.withTimeoutOrNull(connectTimeoutMillis) {
                    conn.awaitHandshake()
                    true
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                // A parent-scope cancellation propagates out of withTimeoutOrNull
                // (it only swallows its OWN TimeoutCancellationException). Wrapping
                // it in HandshakeFailed would break structured concurrency — close
                // the driver but re-throw the cancellation untouched. Mirrors the
                // CancellationException handling on the post-CONNECT path below.
                driver.close()
                throw ce
            } catch (t: Throwable) {
                driver.close()
                throw WebTransportException(
                    kind = WebTransportException.Kind.HandshakeFailed,
                    message = "QUIC handshake failed [$target]: ${t.message}",
                    cause = t,
                )
            }
        if (handshakeCompleted == null) {
            driver.close()
            throw WebTransportException(
                kind = WebTransportException.Kind.HandshakeFailed,
                message =
                    "QUIC handshake stalled — no completion within ${connectTimeoutMillis}ms [$target] " +
                        "(a server-side TLS failure such as a rejected IP-literal SNI hangs here with no alert)",
            )
        }
        if (conn.status != QuicConnection.Status.CONNECTED) {
            driver.close()
            throw WebTransportException(
                kind = WebTransportException.Kind.HandshakeFailed,
                message = "QUIC handshake did not complete (status=${conn.status})",
            )
        }

        // Everything from here through readResponseStatus needs cleanup on
        // any exception — wrap so a thrown SocketException / coroutine cancel
        // doesn't leak the driver + UDP socket.
        try {
            // Open the H3 control stream and push SETTINGS.
            val controlStream = conn.openUniStream()
            val controlBytes =
                byteArrayOf(Http3StreamType.CONTROL.toByte()) + buildClientWebTransportSettings().encodeFrame()
            controlStream.send.enqueue(controlBytes)
            driver.wakeup()

            // RFC 9220 §3.1 strictly requires waiting for the server's SETTINGS
            // frame confirming SETTINGS_ENABLE_WEBTRANSPORT=1 before sending
            // CONNECT. Wiring peerSettings to gate the CONNECT requires
            // restructuring (the demux lives inside QuicWebTransportSessionState
            // which we don't build until after the request bidi opens).
            // Tolerant servers (aioquic, quic-go's interop) accept early CONNECT;
            // strict servers (Chromium) may close the stream. Tracked as a
            // known limitation of the v1 stack.

            // Open the Extended CONNECT request stream.
            val requestStream = conn.openBidiStream()
            val extraHeaders =
                if (webTransportSubProtocols.isNotEmpty()) {
                    listOf("wt-available-protocols" to encodeSfStringList(webTransportSubProtocols))
                } else {
                    emptyList()
                }
            val headers = buildExtendedConnectHeaders(authority, path, bearerToken, extraHeaders)
            requestStream.send.enqueue(encodeHeadersFrame(headers))
            driver.wakeup()

            // Wait for the response HEADERS and verify :status is 2xx before
            // declaring the WebTransport session open. Per RFC 9220 a non-2xx
            // status means the server rejected the upgrade.
            val response =
                kotlinx.coroutines.withTimeoutOrNull(connectTimeoutMillis) {
                    readConnectResponse(requestStream)
                }
            if (response == null) {
                val connDiag = describeConnectionState(conn, requestStream)
                driver.close()
                throw WebTransportException(
                    kind = WebTransportException.Kind.HandshakeFailed,
                    message =
                        "WebTransport CONNECT response timed out after ${connectTimeoutMillis}ms — $connDiag",
                )
            }
            if (response.status !in 200..299) {
                // Capture connection-level state BEFORE driver.close() — if the
                // relay tore the connection down, closeReason/closeErrorCode
                // are already set and tell us whether this was a CONNECTION_CLOSE
                // (e.g. H3_SETTINGS_ERROR from a rejected SETTINGS frame) or the
                // relay simply FIN'd the request bidi without an H3 response.
                val connDiag = describeConnectionState(conn, requestStream)
                driver.close()
                throw WebTransportException(
                    kind = WebTransportException.Kind.ConnectRejected,
                    message =
                        "WebTransport CONNECT returned :status=${response.status} — " +
                            "${response.diagnostic}; $connDiag",
                )
            }

            val state = QuicWebTransportSessionState(conn, driver, requestStream.streamId)
            return QuicWebTransportSession(state, response.subProtocol)
        } catch (we: WebTransportException) {
            throw we
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Preserve cancellation semantics — wrapping it in HandshakeFailed
            // would break structured concurrency. But still close the driver.
            driver.close()
            throw ce
        } catch (t: Throwable) {
            driver.close()
            throw WebTransportException(
                kind = WebTransportException.Kind.HandshakeFailed,
                message = "WebTransport setup failed: ${t.message}",
                cause = t,
            )
        }
    }

    /**
     * Drain bytes from [requestStream] through an [Http3FrameReader]
     * until a HEADERS frame arrives, then decode the QPACK field
     * section and pull out (`:status`, `wt-protocol`).
     *
     * `wt-protocol` (draft-ietf-webtrans-http3 §3.3, RFC 8941
     * SF-string) is the server's selection from the client-offered
     * `wt-available-protocols` list. Returned as a bare string with
     * surrounding quotes stripped; `null` when the server didn't
     * echo the header (e.g. older servers, or when the client
     * offered only one protocol). The header value MAY contain
     * commas if the server were to echo a list; we only expose the
     * first item — moq-lite servers always echo a single value.
     */
    private suspend fun readConnectResponse(requestStream: QuicStream): ConnectResponse {
        val reader = Http3FrameReader()
        val incoming = requestStream.incoming
        // Diagnostic accounting — when the CONNECT fails, :status=0 alone
        // can't tell us whether the relay never answered, FIN'd us with
        // garbage, or sent a HEADERS frame that lacked :status. Track
        // enough to disambiguate in the thrown exception message.
        var bytesSeen = 0
        val h3FramesSeen = mutableListOf<String>()
        try {
            incoming.collect { chunk ->
                bytesSeen += chunk.size
                reader.push(chunk)
                while (true) {
                    val frame = reader.next() ?: break
                    h3FramesSeen += frame::class.simpleName ?: "?"
                    if (frame is Http3Frame.Headers) {
                        val pairs = QpackDecoder().decodeFieldSection(frame.qpackPayload)
                        val status = pairs.firstOrNull { it.first == ":status" }?.second?.toIntOrNull() ?: 0
                        val subProtocol =
                            pairs
                                .firstOrNull { it.first.equals("wt-protocol", ignoreCase = true) }
                                ?.second
                                ?.let { parseFirstSfString(it) }
                        val diag =
                            if (status == 0) {
                                "HEADERS frame carried no parseable :status — fields=[${pairs.joinToString { it.first }}]"
                            } else {
                                "HEADERS received, status=$status"
                            }
                        throw HeadersReceived(status, subProtocol, diag)
                    }
                }
            }
        } catch (e: HeadersReceived) {
            return ConnectResponse(e.status, e.subProtocol, e.diagnostic)
        }
        // The incoming flow completed WITHOUT a HEADERS frame — the relay
        // ended (FIN'd) the request bidi without an H3 response. Note this
        // is a *clean* close: a RESET_STREAM would have thrown out of
        // `incoming.collect` and been wrapped as HandshakeFailed instead.
        return ConnectResponse(
            status = 0,
            subProtocol = null,
            diagnostic =
                "request stream ended with no HEADERS frame " +
                    "(bytesSeen=$bytesSeen, h3FramesSeen=$h3FramesSeen, requestStreamClosed=${requestStream.isClosed})",
        )
    }

    /**
     * One-line snapshot of the QUIC connection + request-stream state for
     * a failed CONNECT. Distinguishes "the relay sent a CONNECTION_CLOSE"
     * (status != CONNECTED, closeErrorCode/closeReason populated — e.g.
     * H3_SETTINGS_ERROR if it rejected our SETTINGS frame) from "the relay
     * left the connection up but closed just the request stream".
     */
    private fun describeConnectionState(
        conn: QuicConnection,
        requestStream: QuicStream,
    ): String =
        buildString {
            append("conn.status=").append(conn.status)
            conn.closeReason?.let { append(", closeReason='").append(it).append('\'') }
            if (conn.closeErrorCode != 0L) {
                append(", closeErrorCode=0x").append(conn.closeErrorCode.toString(16))
            }
            append(", requestStreamClosed=").append(requestStream.isClosed)
        }

    private data class ConnectResponse(
        val status: Int,
        val subProtocol: String?,
        /** Human-readable account of how the response was (or wasn't) received. */
        val diagnostic: String,
    )

    private class HeadersReceived(
        val status: Int,
        val subProtocol: String?,
        val diagnostic: String,
    ) : RuntimeException()

    /**
     * Parse the first item out of an RFC 8941 SF-list-of-strings
     * value (the format `wt-protocol` carries per
     * draft-ietf-webtrans-http3 §3.3). For "moq-lite-04" servers
     * echo a single quoted string; for completeness we tolerate a
     * comma-separated list and take the first. Returns `null` if
     * the value can't be parsed as an SF-string.
     *
     * This is a deliberately minimal parser — full RFC 8941
     * parameter handling isn't needed for the moq-lite use case
     * (no parameters are ever attached). Tolerates surrounding
     * whitespace per §3.1.
     */
    private fun parseFirstSfString(raw: String): String? {
        val firstItem = raw.substringBefore(',').trim()
        if (firstItem.length < 2 || firstItem.first() != '"' || firstItem.last() != '"') return null
        // SF-string only supports `\\` and `\"` escapes per §3.3.3.
        // moq-lite values are bare ASCII; just strip the quotes.
        return firstItem.substring(1, firstItem.length - 1)
    }

    /**
     * Encode [items] as an RFC 8941 Structured Field List of bare strings
     * (the format `wt-available-protocols` requires per draft-ietf-webtrans-http3
     * §3.3). Each entry becomes `"value"`; the items are comma-separated.
     *
     * The values we emit (e.g. `moq-lite-03`) are bare ASCII so we don't
     * need to handle escaping — assert it instead of silently mis-emitting.
     */
    private fun encodeSfStringList(items: List<String>): String {
        require(items.all { p -> p.all { it in 0x20.toChar()..0x7e.toChar() && it != '"' && it != '\\' } }) {
            "wt-available-protocols entry contains characters that need RFC 8941 escaping: $items"
        }
        return items.joinToString(", ") { "\"$it\"" }
    }

    /**
     * Resolve [host] to a concrete address string, preferring IPv4. Returns
     * an IPv4 literal when one exists, otherwise the first resolved address,
     * otherwise [host] unchanged (already an IP literal, or resolution
     * failed — let the socket layer surface the error). The returned IP
     * literal is only used as the UDP socket target; the caller still passes
     * the original [host] as the TLS SNI server name.
     */
    private fun resolvePreferIpv4(host: String): String =
        try {
            val addrs = java.net.InetAddress.getAllByName(host)
            (
                addrs.firstOrNull { it is java.net.Inet4Address }
                    ?: addrs.firstOrNull()
            )?.hostAddress ?: host
        } catch (_: java.net.UnknownHostException) {
            host
        }

    private fun splitAuthority(authority: String): Pair<String, Int> {
        val idx = authority.lastIndexOf(':')
        if (idx <= 0) return authority to 443
        val host = authority.substring(0, idx)
        val port = authority.substring(idx + 1).toIntOrNull() ?: 443
        return host to port
    }
}

/** Adapter that wraps the :quic [QuicWebTransportSessionState] in the nestsClient interface. */
class QuicWebTransportSession(
    private val state: QuicWebTransportSessionState,
    /**
     * The sub-protocol the server selected via `wt-protocol`
     * during Extended CONNECT. Captured by
     * [QuicWebTransportFactory.connect] from the parsed response
     * headers and passed through to this constructor. `null` when
     * the server didn't echo the header.
     */
    override val negotiatedSubProtocol: String? = null,
) : WebTransportSession {
    override val isOpen: Boolean get() = state.isOpen

    /**
     * Diagnostic-only passthrough to
     * [com.vitorpamplona.quic.connection.QuicConnection.flowControlSnapshot].
     * Used by `SendTraceScenario` to dump the connection's
     * flow-control accounting at known points (pre-pump, mid-pump,
     * post-pump-grace) when investigating loss patterns. Not part of
     * the [WebTransportSession] common interface — callers downcast
     * explicitly when they need it.
     */
    suspend fun quicFlowControlSnapshot(): com.vitorpamplona.quic.connection.QuicFlowControlSnapshot = state.connection.flowControlSnapshot()

    override suspend fun openBidiStream(): WebTransportBidiStream {
        val s = state.openBidiStream()
        return QuicBidiStreamAdapter(s, state.driver)
    }

    override suspend fun openUniStream(bestEffort: Boolean): WebTransportWriteStream {
        val s = state.openUniStream(bestEffort = bestEffort)
        return QuicUniWriteStreamAdapter(s, state.driver)
    }

    override fun incomingUniStreams(): Flow<WebTransportReadStream> =
        flow {
            // Surface only unidirectional WT streams whose prefix bytes
            // (0x54 + quarter session id) have been stripped. The H3 control
            // stream and QPACK encoder/decoder streams are drained internally
            // by the demux and never reach here.
            state.incomingStrippedStreams.collect { stripped ->
                if (stripped.isUnidirectional) {
                    emit(StrippedWtReadStreamAdapter(stripped))
                }
            }
        }

    override fun incomingBidiStreams(): Flow<WebTransportBidiStream> =
        flow {
            // Surface only peer-initiated bidi streams whose WT_BIDI_STREAM
            // prefix has been stripped by the demux. send/finish are
            // wired through the driver wakeup.
            state.incomingStrippedStreams.collect { stripped ->
                if (!stripped.isUnidirectional) {
                    emit(StrippedWtBidiStreamAdapter(stripped))
                }
            }
        }

    override suspend fun sendDatagram(payload: ByteArray): Boolean {
        state.sendDatagram(payload)
        return true
    }

    override fun incomingDatagrams(): Flow<ByteArray> =
        flow {
            while (state.isOpen) {
                val d = state.pollIncomingDatagram()
                if (d != null) emit(d)
                kotlinx.coroutines.delay(5)
            }
        }

    override suspend fun close(
        code: Int,
        reason: String,
    ) {
        state.close(code, reason)
    }
}

private class QuicBidiStreamAdapter(
    private val stream: QuicStream,
    private val driver: com.vitorpamplona.quic.connection.QuicConnectionDriver,
) : WebTransportBidiStream {
    override fun incoming(): Flow<ByteArray> = stream.incoming

    override suspend fun write(chunk: ByteArray) {
        stream.send.enqueue(chunk)
        driver.wakeup()
    }

    override suspend fun finish() {
        stream.send.finish()
        driver.wakeup()
    }

    override suspend fun reset(errorCode: Long) {
        stream.resetStream(errorCode)
        driver.wakeup()
    }

    override suspend fun stopSending(errorCode: Long) {
        stream.stopSending(errorCode)
        driver.wakeup()
    }

    override fun setPriority(priority: Int) {
        stream.priority = priority
    }
}

private class QuicReadStreamAdapter(
    private val stream: QuicStream,
    private val driver: com.vitorpamplona.quic.connection.QuicConnectionDriver,
) : WebTransportReadStream {
    override fun incoming(): Flow<ByteArray> = stream.incoming

    override suspend fun stopSending(errorCode: Long) {
        stream.stopSending(errorCode)
        driver.wakeup()
    }
}

/**
 * Write-only adapter over a locally-opened uni QUIC stream. The
 * underlying [QuicWebTransportSessionState.openUniStream] has already
 * pushed the WT framing prefix (0x54 + quarter session id), so the
 * caller's [write] payload goes straight onto the wire.
 */
private class QuicUniWriteStreamAdapter(
    private val stream: QuicStream,
    private val driver: com.vitorpamplona.quic.connection.QuicConnectionDriver,
) : WebTransportWriteStream {
    override suspend fun write(chunk: ByteArray) {
        stream.send.enqueue(chunk)
        driver.wakeup()
    }

    override suspend fun finish() {
        stream.send.finish()
        driver.wakeup()
    }

    override suspend fun reset(errorCode: Long) {
        stream.resetStream(errorCode)
        driver.wakeup()
    }

    override fun setPriority(priority: Int) {
        stream.priority = priority
    }
}

/** Adapter for a WT peer-initiated uni stream whose prefix has been stripped. */
private class StrippedWtReadStreamAdapter(
    private val stripped: com.vitorpamplona.quic.webtransport.StrippedWtStream,
) : WebTransportReadStream {
    override fun incoming(): Flow<ByteArray> = stripped.data

    override suspend fun stopSending(errorCode: Long) {
        stripped.stopSending(errorCode)
    }
}

/**
 * Adapter for a peer-initiated bidi WT stream whose WT_BIDI_STREAM prefix
 * has been stripped. Routes [write] / [finish] / [reset] / [stopSending]
 * through the demux's driver-aware closures so application bytes actually
 * leave the connection.
 */
private class StrippedWtBidiStreamAdapter(
    private val stripped: com.vitorpamplona.quic.webtransport.StrippedWtStream,
) : WebTransportBidiStream {
    init {
        check(!stripped.isUnidirectional) {
            "StrippedWtBidiStreamAdapter requires a bidi stream, got uni"
        }
    }

    override fun incoming(): Flow<ByteArray> = stripped.data

    override suspend fun write(chunk: ByteArray) {
        val send =
            stripped.send
                ?: error("peer-initiated bidi stream has no send half — demux didn't wire one")
        send(chunk)
    }

    override suspend fun finish() {
        val finish =
            stripped.finish
                ?: error("peer-initiated bidi stream has no finish — demux didn't wire one")
        finish()
    }

    override suspend fun reset(errorCode: Long) {
        // Bidi streams have a send side we own, so `reset` is wired by
        // the demux. Same defensive `?:` shape as in `write` / `finish`.
        val r =
            stripped.reset
                ?: error("peer-initiated bidi stream has no reset — demux didn't wire one")
        r(errorCode)
    }

    override suspend fun stopSending(errorCode: Long) {
        stripped.stopSending(errorCode)
    }

    /**
     * No-op: peer-initiated bidi streams arrive through the demux as a
     * [com.vitorpamplona.quic.webtransport.StrippedWtStream] which exposes
     * only `send`/`finish`/`reset`/`stopSending` closures, not the
     * underlying [QuicStream]. The moq-lite priority use case targets
     * locally-opened uni group streams only, so this path doesn't need
     * to model priority — see the [WebTransportWriteStream.setPriority]
     * contract.
     */
    override fun setPriority(priority: Int) = Unit
}
