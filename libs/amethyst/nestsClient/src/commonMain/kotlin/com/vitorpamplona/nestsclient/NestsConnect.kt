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
package com.vitorpamplona.nestsclient

import com.vitorpamplona.nestsclient.audio.AudioCapture
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.moq.SubscribeHandle
import com.vitorpamplona.nestsclient.moq.lite.MoqLiteSession
import com.vitorpamplona.nestsclient.moq.lite.MoqLiteVersion
import com.vitorpamplona.nestsclient.transport.WebTransportException
import com.vitorpamplona.nestsclient.transport.WebTransportFactory
import com.vitorpamplona.nestsclient.transport.WebTransportSession
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Walk the full join-as-listener handshake against a nests-compatible audio
 * server:
 *
 *   1. Mint a JWT — POST `<authBase>/auth` with NIP-98 + namespace body
 *      (see [NestsClient.mintToken]).
 *   2. Open a [WebTransportSession] against the [room.endpoint] via
 *      [transport]. The path is `/<moqNamespace>?jwt=<token>` — moq-rs
 *      treats the URL path as `claims.root` and reads the JWT from
 *      `?jwt=`.
 *   3. Wrap the WT session in a [MoqLiteSession]. moq-lite Lite-03 has
 *      NO in-band SETUP message — the WT handshake itself is the
 *      handshake, version is selected by the ALPN `moq-lite-03`.
 *
 * The returned [NestsListener] is in state [NestsListenerState.Connected];
 * if any step fails, the listener is returned in
 * [NestsListenerState.Failed] with the underlying cause attached and the
 * transport torn down.
 *
 * @param room per-room config built from the NIP-53 kind 30312 event by
 *   the caller (UI / VM).
 * @param signer NIP-98 signer for the mintToken HTTP call.
 * @param scope where the moq-lite pumps live (typically the caller's
 *   ViewModel scope so they cancel when the screen leaves).
 */
suspend fun connectNestsListener(
    httpClient: NestsClient,
    transport: WebTransportFactory,
    scope: CoroutineScope,
    room: NestsRoomConfig,
    signer: NostrSigner,
): NestsListener {
    val state =
        MutableStateFlow<NestsListenerState>(
            NestsListenerState.Connecting(NestsListenerState.Connecting.ConnectStep.ResolvingRoom),
        )

    val token =
        try {
            httpClient.mintToken(room = room, publish = false, signer = signer)
        } catch (t: NestsException) {
            state.value = NestsListenerState.Failed("Auth failed: ${t.message}", t)
            return failedListener(state)
        }

    state.value = NestsListenerState.Connecting(NestsListenerState.Connecting.ConnectStep.OpeningTransport)

    val (authority, path) =
        try {
            buildRelayConnectTarget(room.endpoint, room.moqNamespace(), token)
        } catch (t: Throwable) {
            state.value =
                NestsListenerState.Failed(
                    "Malformed MoQ endpoint URL '${room.endpoint}': ${t.message}",
                    t,
                )
            return failedListener(state)
        }

    val webTransport =
        try {
            // moq-rs reads the JWT from the `?jwt=` query parameter and
            // ignores the Authorization header — bearer must be null here.
            transport.connect(authority = authority, path = path, bearerToken = null)
        } catch (t: WebTransportException) {
            state.value =
                NestsListenerState.Failed(
                    "WebTransport ${t.kind.name}: ${t.message}",
                    t,
                )
            return failedListener(state)
        }

    state.value = NestsListenerState.Connecting(NestsListenerState.Connecting.ConnectStep.MoqHandshake)

    // moq-lite has NO setup message — the WebTransport handshake itself
    // is the handshake. Version (Lite-03 vs Lite-04) is selected by the
    // ALPN exchange (`wt-available-protocols` / `wt-protocol`) and
    // surfaced via [MoqLiteSession.version]; the session itself derives
    // it from `transport.negotiatedSubProtocol`.
    val moq =
        try {
            MoqLiteSession.client(webTransport, scope)
        } catch (t: Throwable) {
            runCatching { webTransport.close(0, "moq-lite session init failed") }
            state.value = NestsListenerState.Failed("moq-lite session init failed: ${t.message}", t)
            return failedListener(state)
        }

    state.value = NestsListenerState.Connected(room, versionCode(moq.version))
    return MoqLiteNestsListener(
        session = moq,
        mutableState = state,
    )
}

/**
 * Synthetic version code reported on [NestsListenerState.Connected.negotiatedMoqVersion]
 * for moq-lite Lite-03 sessions. moq-lite negotiates the wire version
 * via ALPN rather than an in-band SETUP exchange, so there is no real
 * "version" the peer reports back. The constant carries the ALPN suffix
 * `-03` in the low bytes for diagnostic display.
 */
const val MOQ_LITE_03_VERSION: Long = 0x6D71_6C03L

/**
 * Synthetic version code for moq-lite Lite-04 sessions — same shape
 * as [MOQ_LITE_03_VERSION] with the ALPN suffix `-04` in the low
 * bytes.
 */
const val MOQ_LITE_04_VERSION: Long = 0x6D71_6C04L

/** Map a [MoqLiteVersion] to its synthetic [MOQ_LITE_03_VERSION]-family code. */
internal fun versionCode(version: MoqLiteVersion): Long =
    when (version) {
        MoqLiteVersion.LITE_03 -> MOQ_LITE_03_VERSION
        MoqLiteVersion.LITE_04 -> MOQ_LITE_04_VERSION
    }

/**
 * Build a no-op [NestsListener] in a Failed state for callers that want a
 * uniform return type even on early-failure paths.
 */
private fun failedListener(state: MutableStateFlow<NestsListenerState>): NestsListener =
    object : NestsListener {
        override val state = state

        override suspend fun subscribeSpeaker(
            speakerPubkeyHex: String,
            maxLatencyMs: Long,
        ): SubscribeHandle = error("listener never connected: ${state.value}")

        override suspend fun close() {
            if (state.value !is NestsListenerState.Closed) {
                state.value = NestsListenerState.Closed
            }
        }
    }

/**
 * Speaker / host counterpart of [connectNestsListener]. Walks the same
 * three-step HTTP → WebTransport → moq-lite session sequence the
 * listener does; the difference is downstream: [NestsSpeaker.startBroadcasting]
 * claims a broadcast suffix on the moq-lite session and starts pumping
 * Opus frames out as one uni stream per group.
 *
 * @param speakerPubkeyHex this user's pubkey hex. Used as the moq-lite
 *   broadcast suffix the relay routes to subscribers
 *   (`MoqLiteSubscribe.broadcast == speakerPubkeyHex`); the JS reference
 *   mints the same value via `Path.from(identity)`
 *   (`@moq/publish/screen-B680RFft.js`).
 * @param captureFactory builds an [AudioCapture] (one per broadcast).
 *   Android passes `{ AudioRecordCapture() }`.
 * @param encoderFactory builds an [OpusEncoder] (one per broadcast).
 *   Android passes `{ MediaCodecOpusEncoder() }`.
 * @param framesPerGroup how many Opus frames to pack into one moq-lite
 *   group / QUIC uni stream. See
 *   [com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster.framesPerGroup]
 *   for the production stream-cliff rationale that motivates the
 *   default of 5.
 */
suspend fun connectNestsSpeaker(
    httpClient: NestsClient,
    transport: WebTransportFactory,
    scope: CoroutineScope,
    room: NestsRoomConfig,
    signer: NostrSigner,
    speakerPubkeyHex: String,
    captureFactory: () -> AudioCapture,
    encoderFactory: () -> OpusEncoder,
    framesPerGroup: Int =
        com.vitorpamplona.nestsclient.audio
            .NestMoqLiteBroadcaster.DEFAULT_FRAMES_PER_GROUP,
    /**
     * Per-broadcast audio shape. Defaults to mono so existing call sites
     * keep the prior behaviour. Caller is responsible for matching the
     * channel count to the encoder + capture they pass — see
     * [AudioBroadcastConfig].
     */
    broadcastConfig: AudioBroadcastConfig = AudioBroadcastConfig(),
): NestsSpeaker {
    val state =
        MutableStateFlow<NestsSpeakerState>(
            NestsSpeakerState.Connecting(NestsSpeakerState.Connecting.ConnectStep.ResolvingRoom),
        )

    val token =
        try {
            httpClient.mintToken(room = room, publish = true, signer = signer)
        } catch (t: NestsException) {
            state.value = NestsSpeakerState.Failed("Auth failed: ${t.message}", t)
            return failedSpeaker(state)
        }

    state.value = NestsSpeakerState.Connecting(NestsSpeakerState.Connecting.ConnectStep.OpeningTransport)

    val (authority, path) =
        try {
            buildRelayConnectTarget(room.endpoint, room.moqNamespace(), token)
        } catch (t: Throwable) {
            state.value =
                NestsSpeakerState.Failed(
                    "Malformed MoQ endpoint URL '${room.endpoint}': ${t.message}",
                    t,
                )
            return failedSpeaker(state)
        }

    val webTransport =
        try {
            // moq-rs reads the JWT from the `?jwt=` query parameter and
            // ignores the Authorization header — bearer must be null here.
            transport.connect(authority = authority, path = path, bearerToken = null)
        } catch (t: WebTransportException) {
            state.value =
                NestsSpeakerState.Failed(
                    "WebTransport ${t.kind.name}: ${t.message}",
                    t,
                )
            return failedSpeaker(state)
        }

    state.value = NestsSpeakerState.Connecting(NestsSpeakerState.Connecting.ConnectStep.MoqHandshake)

    // moq-lite has NO setup message. Same logic as the listener path —
    // version is selected by the ALPN exchange and derived inside
    // [MoqLiteSession.version].
    val moq =
        try {
            MoqLiteSession.client(webTransport, scope)
        } catch (t: Throwable) {
            runCatching { webTransport.close(0, "moq-lite session init failed") }
            state.value = NestsSpeakerState.Failed("moq-lite session init failed: ${t.message}", t)
            return failedSpeaker(state)
        }

    state.value = NestsSpeakerState.Connected(room, versionCode(moq.version))
    return MoqLiteNestsSpeaker(
        session = moq,
        speakerPubkeyHex = speakerPubkeyHex,
        captureFactory = captureFactory,
        encoderFactory = encoderFactory,
        scope = scope,
        mutableState = state,
        framesPerGroup = framesPerGroup,
        broadcastConfig = broadcastConfig,
    )
}

/** Mirror of [failedListener] for the speaker path. */
private fun failedSpeaker(state: MutableStateFlow<NestsSpeakerState>): NestsSpeaker =
    object : NestsSpeaker {
        override val state = state

        override suspend fun startBroadcasting(onLevel: (Float) -> Unit): BroadcastHandle = error("speaker never connected: ${state.value}")

        override suspend fun close() {
            if (state.value !is NestsSpeakerState.Closed) {
                state.value = NestsSpeakerState.Closed
            }
        }
    }

/**
 * Build the WebTransport connect target for a nests room.
 *
 *   - **Authority** comes from the kind-30312 `endpoint` URL (host + port).
 *   - **Path** is `/<moqNamespace>?jwt=<token>`. The JS reference client
 *     overwrites `relayUrl.pathname` with the namespace literal — moq-rs
 *     compares this path to `claims.root` for ANNOUNCE / SUBSCRIBE
 *     authorisation, so any other path on the relay returns
 *     `401 IncorrectRoot`.
 *   - **Token** is delivered as the `?jwt=` query parameter (NOT an
 *     `Authorization` header — moq-rs only reads the query param). Per the
 *     ES256 JWT alphabet (base64url `[A-Za-z0-9_-]` + `.`) the token never
 *     contains characters reserved in a query string, so no encoding is
 *     applied. The path itself contains `:` and `/`, both legal in
 *     `pchar` per RFC 3986.
 */
internal fun buildRelayConnectTarget(
    endpoint: String,
    namespace: String,
    token: String,
): Pair<String, String> {
    val (authority, _) = parseEndpoint(endpoint)
    // Percent-encode any character in [namespace] that would terminate the
    // URL path (`?`, `#`, ` `) or otherwise break parsing — `roomId` comes
    // from the kind-30312 `d` tag, which NIP-53 does NOT constrain to a
    // safe charset, so a `d` tag containing `?` or `#` would otherwise
    // truncate the path and split the JWT into the wrong slot.
    val path = "/" + percentEncodePath(namespace) + "?jwt=" + token
    return authority to path
}

/**
 * Percent-encode the bytes of [s] that aren't legal in an RFC 3986 URI
 * `pchar`. We preserve `:` and `/` literally because the namespace uses
 * them as structural separators (`nests/<kind>:<host_pubkey>:<roomId>`),
 * and the relay compares the path against its claim root using the same
 * canonical form. Anything else — including `?`, `#`, `&`, ` `, control
 * bytes, and any non-ASCII — is encoded.
 */
private fun percentEncodePath(s: String): String {
    val bytes = s.encodeToByteArray()
    val out = StringBuilder(bytes.size)
    for (raw in bytes) {
        val b = raw.toInt() and 0xFF
        val c = b.toChar()
        val safe =
            (c in 'A'..'Z') ||
                (c in 'a'..'z') ||
                (c in '0'..'9') ||
                c == '-' || c == '_' || c == '.' || c == '~' ||
                c == '!' || c == '$' || c == '\'' || c == '(' || c == ')' ||
                c == '*' || c == '+' || c == ',' || c == ';' || c == '=' ||
                c == ':' || c == '@' || c == '/'
        if (safe) {
            out.append(c)
        } else {
            out.append('%')
            out.append(HEX[b ushr 4])
            out.append(HEX[b and 0x0F])
        }
    }
    return out.toString()
}

private val HEX = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

/**
 * Split a typical nests endpoint URL such as `https://relay.example.com/moq`
 * or `https://relay.example.com:4443/api/v1/moq?room=abc` into the
 * WebTransport `(authority, path)` pair. WebTransport authority is
 * `host[:port]` (port omitted when it's the protocol default); path is
 * everything after the authority including any query string. Defaults to
 * `/` when the URL has no path.
 *
 * Hand-rolled rather than pulling in a URL library so this stays in
 * commonMain with no extra dependency.
 */
internal fun parseEndpoint(endpoint: String): Pair<String, String> {
    val schemeEnd = endpoint.indexOf("://")
    require(schemeEnd > 0) { "endpoint must include a scheme (got '$endpoint')" }
    val scheme = endpoint.substring(0, schemeEnd).lowercase()
    val rest = endpoint.substring(schemeEnd + 3)

    val pathSep = rest.indexOf('/')
    val (authorityRaw, pathRaw) =
        if (pathSep < 0) {
            rest to "/"
        } else {
            rest.substring(0, pathSep) to rest.substring(pathSep)
        }

    require(authorityRaw.isNotEmpty()) { "endpoint must include an authority (got '$endpoint')" }

    val hasUserInfo = authorityRaw.contains('@')
    require(!hasUserInfo) { "endpoint must not include userinfo (got '$endpoint')" }

    // IPv6 literal authorities use `[host]:port` form (RFC 3986 §3.2.2);
    // a naive `lastIndexOf(':')` finds a colon *inside* the address and
    // breaks parsing. Detect the bracketed form first and split on the
    // colon after `]`.
    val (hostPart, portStr) =
        if (authorityRaw.startsWith('[')) {
            val closeBracket = authorityRaw.indexOf(']')
            // closeBracket==1 would mean the literal "[]" — empty IPv6
            // address. Reject so callers can't accidentally pass an
            // unconfigured placeholder.
            require(closeBracket > 1) { "malformed IPv6 authority: '$authorityRaw'" }
            val host = authorityRaw.substring(0, closeBracket + 1)
            val tail = authorityRaw.substring(closeBracket + 1)
            when {
                tail.isEmpty() -> host to null
                tail.startsWith(':') -> host to tail.substring(1)
                else -> error("malformed IPv6 authority tail '$tail' in '$endpoint'")
            }
        } else {
            val portSep = authorityRaw.lastIndexOf(':')
            if (portSep >= 0) {
                authorityRaw.substring(0, portSep) to authorityRaw.substring(portSep + 1)
            } else {
                authorityRaw to null
            }
        }

    // Strip the port if it's the scheme default so the on-the-wire authority is
    // canonical.
    val authority =
        if (portStr != null) {
            val port = portStr.toIntOrNull() ?: error("malformed port '$portStr' in '$endpoint'")
            val defaultPort =
                when (scheme) {
                    "https", "wss" -> 443
                    "http", "ws" -> 80
                    else -> -1
                }
            if (port == defaultPort) hostPart else "$hostPart:$port"
        } else {
            hostPart
        }

    return authority to pathRaw
}
