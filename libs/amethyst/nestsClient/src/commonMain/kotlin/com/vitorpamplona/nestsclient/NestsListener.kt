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

import com.vitorpamplona.nestsclient.moq.MoqProtocolException
import com.vitorpamplona.nestsclient.moq.MoqSession
import com.vitorpamplona.nestsclient.moq.SubscribeFilter
import com.vitorpamplona.nestsclient.moq.SubscribeHandle
import com.vitorpamplona.nestsclient.moq.TrackNamespace
import com.vitorpamplona.nestsclient.transport.WebTransportSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High-level listener handle for an audio-room. Hides the layered HTTP +
 * WebTransport + MoQ wiring under one observable state machine so UI code
 * (and tests) can reason about the room as a single resource.
 *
 * Open one [NestsListener] per audio-room screen. To listen to multiple
 * speakers in the room, call [subscribeSpeaker] once per speaker pubkey.
 */
interface NestsListener {
    /** Live connection state — the UI typically shows a chip / spinner derived from this. */
    val state: StateFlow<NestsListenerState>

    /**
     * Subscribe to one speaker's audio track. nests publishes each speaker's
     * Opus stream under the namespace `["nests", <roomId>]` with the
     * speaker's pubkey hex as the track name.
     *
     * @param maxLatencyMs subscriber-side group-staleness tolerance (milliseconds)
     *   sent in the moq-lite SUBSCRIBE message. Per
     *   `kixelated/moq/doc/concept/layer/moq-lite.md` "Congestion": when the
     *   relay's per-track group queue can't drain fast enough, groups older
     *   than `maxLatencyMs` are evicted in favour of newer ones. `0`
     *   (default) means *unlimited* — the relay falls back to its own
     *   `MAX_GROUP_AGE = 30 s` default. For low-latency live audio set this
     *   to e.g. `500L` so the listener prefers fresh frames over stale
     *   buffered ones during transient relay backpressure. Ignored on the
     *   IETF MoQ-transport listener path (no equivalent field on that wire).
     *
     * @throws MoqProtocolException if the publisher rejects the subscription.
     * @throws IllegalStateException if the listener is not in [NestsListenerState.Connected].
     */
    suspend fun subscribeSpeaker(
        speakerPubkeyHex: String,
        maxLatencyMs: Long = 0L,
    ): SubscribeHandle

    /**
     * Subscribe to a speaker's `catalog.json` track — moq-lite's
     * canonical channel for "what is this broadcast publishing" JSON
     * metadata (codec, sample rate, optional speaker-side hints).
     * The publisher emits one JSON object per group; consumers
     * typically read the latest frame and parse it.
     *
     * Throws on the IETF reference path because moq-transport-17
     * doesn't define a catalog convention. Callers that want
     * cross-protocol behavior should `runCatching` this.
     *
     * @throws UnsupportedOperationException on the IETF listener.
     * @throws MoqProtocolException if the publisher rejects the subscription.
     * @throws IllegalStateException if the listener is not in [NestsListenerState.Connected].
     */
    suspend fun subscribeCatalog(speakerPubkeyHex: String): SubscribeHandle =
        throw UnsupportedOperationException(
            "subscribeCatalog is moq-lite-only; IETF listener has no catalog channel.",
        )

    /**
     * Cold flow of moq-lite ANNOUNCE updates for the room's
     * namespace. Each emission represents a publisher transitioning
     * Active (broadcast started) or inactive (broadcast ended).
     * Lets clients render an "actively broadcasting" indicator
     * independent of the kind-10312 `publishing` presence tag —
     * the JS reference's `useRoomAnnouncements` consumes this.
     *
     * The flow is COLD (re-collected starts a fresh subscription)
     * — different from [subscribeSpeaker], which returns a hot
     * SubscribeHandle backed by a fan-out [kotlinx.coroutines.flow.MutableSharedFlow].
     * The asymmetry is intentional: announce data is room-state
     * (one consumer, no buffered replay), while audio is per-frame
     * playout (multi-collect resilient, drop-oldest fan-out).
     *
     * Throws on the IETF reference path because moq-transport-17
     * doesn't define an announce-prefix flow. Throws at call time
     * (not at first collect) so callers can `runCatching` the
     * single call rather than wrapping every collect — same
     * timing as [subscribeCatalog].
     *
     * @throws UnsupportedOperationException on the IETF listener.
     * @throws IllegalStateException if the listener is not in [NestsListenerState.Connected].
     */
    fun announces(): Flow<RoomAnnouncement> =
        throw UnsupportedOperationException(
            "announces() is moq-lite-only; IETF listener has no announce-prefix flow.",
        )

    /**
     * Force the underlying transport / MoQ session to be torn down and
     * a fresh one opened in its place — without permanently closing
     * this listener. Used by the platform layer on a network change
     * (Wi-Fi ↔ cellular handover) to skip the ~30 s QUIC PTO that
     * would otherwise have to fire before the wrapper notices the
     * old socket is dead.
     *
     * Default no-op for non-reconnecting implementations (raw
     * [DefaultNestsListener] / [MoqLiteNestsListener]) — there's no
     * orchestrator to drive a reconnect, so a force recycle would
     * just close the listener. The reconnecting wrapper
     * (`ReconnectingHandle` from [connectReconnectingNestsListener])
     * overrides to close the active inner listener so its
     * orchestrator opens a fresh session.
     */
    suspend fun recycleSession() {
        // no-op — only the reconnecting wrapper has anywhere to
        // recycle to.
    }

    /** Tear down the MoQ session + underlying transport. Idempotent. */
    suspend fun close()
}

/**
 * One moq-lite ANNOUNCE update. The JS reference uses these to
 * drive the "live now" badge on each speaker's avatar.
 *
 * @param pubkey the suffix of the announce path — for nests
 *   audio rooms this is the speaker's pubkey hex (the broadcast
 *   path `<roomId>/<pubkey>` minus the `<roomId>` prefix the
 *   listener already established).
 * @param active true on the `Active` status (broadcast came up);
 *   false on `Ended` (broadcast went down).
 */
data class RoomAnnouncement(
    val pubkey: String,
    val active: Boolean,
)

/**
 * Lifecycle states of a [NestsListener].
 *
 * Resolved/connected information (room metadata, MoQ version) is carried on
 * [NestsListenerState.Connected] so the UI can render details without
 * threading them separately.
 */
sealed class NestsListenerState {
    /** No connection attempt has started yet. */
    data object Idle : NestsListenerState()

    /** A connect call is in flight. [step] indicates which substage. */
    data class Connecting(
        val step: ConnectStep,
    ) : NestsListenerState() {
        enum class ConnectStep {
            /** Calling `<service>/<roomId>` to obtain the MoQ endpoint + token. */
            ResolvingRoom,

            /** Opening the WebTransport ([:quic] + Extended CONNECT). */
            OpeningTransport,

            /** Running the MoQ CLIENT_SETUP / SERVER_SETUP exchange. */
            MoqHandshake,
        }
    }

    /** Connection is live. [room] reflects the (auth, endpoint, room) we connected to. */
    data class Connected(
        val room: NestsRoomConfig,
        val negotiatedMoqVersion: Long,
    ) : NestsListenerState()

    /**
     * The previous session dropped and the reconnect orchestrator
     * is waiting [delayMs] before trying again. [attempt] is
     * 1-indexed (1 = first retry after the original session
     * failed). UI shows a "Reconnecting…" chip; the session does
     * NOT retry while the listener stays in this state — the
     * orchestrator transitions through [Connecting] for the next
     * attempt and back to [Failed] / [Connected] once the open
     * call resolves.
     */
    data class Reconnecting(
        val attempt: Int,
        val delayMs: Long,
    ) : NestsListenerState()

    /** A connect attempt or live session failed. UI shows [reason] to the user. */
    data class Failed(
        val reason: String,
        val cause: Throwable? = null,
    ) : NestsListenerState()

    /** [close] has been called. Terminal. */
    data object Closed : NestsListenerState()
}

/**
 * IETF `draft-ietf-moq-transport-17` [NestsListener] reference
 * implementation. **Not used in production** — the production listener
 * path uses [MoqLiteNestsListener] over moq-lite Lite-03 (see
 * `nestsClient/plans/2026-04-26-moq-lite-gap.md`). Kept for the IETF
 * unit-test suite (`MoqSession`, `MoqCodec`) and for any future IETF
 * MoQ-transport target.
 *
 * Delegates to a [MoqSession] already set up on a
 * [WebTransportSession]; construction does NOT open the transport.
 */
class DefaultNestsListener internal constructor(
    private val session: MoqSession,
    private val roomNamespace: TrackNamespace,
    private val mutableState: MutableStateFlow<NestsListenerState>,
) : NestsListener {
    override val state: StateFlow<NestsListenerState> = mutableState.asStateFlow()

    override suspend fun subscribeSpeaker(
        speakerPubkeyHex: String,
        maxLatencyMs: Long,
    ): SubscribeHandle {
        check(state.value is NestsListenerState.Connected) {
            "NestsListener.subscribeSpeaker requires Connected state, was ${state.value}"
        }
        // IETF MoQ-transport-17 has no per-subscribe staleness tolerance
        // field on the wire — `maxLatencyMs` is ignored on this path.
        // Closest analogue would be the SUBSCRIBE filter, which is
        // already pinned to LatestGroup so a slow listener wakes up at
        // the freshest group rather than from history.
        return session.subscribe(
            namespace = roomNamespace,
            trackName = speakerPubkeyHex.encodeToByteArray(),
            filter = SubscribeFilter.LatestGroup,
        )
    }

    override suspend fun close() {
        if (state.value is NestsListenerState.Closed) return
        runCatching { session.close() }
        mutableState.value = NestsListenerState.Closed
    }
}
