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
import com.vitorpamplona.nestsclient.audio.NestBroadcaster
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.moq.MoqProtocolException
import com.vitorpamplona.nestsclient.moq.MoqSession
import com.vitorpamplona.nestsclient.moq.TrackNamespace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * High-level handle for the speaker / host audio path. Mirror of
 * [NestsListener]: hides HTTP + WebTransport + MoQ wiring under one
 * observable state machine, with the added ability to broadcast our own
 * speaker track.
 *
 * Open one [NestsSpeaker] per audio-room screen where we have host or
 * speaker permission. Call [startBroadcasting] when the user taps "Talk";
 * call [BroadcastHandle.close] when they stop talking or leave the room.
 */
interface NestsSpeaker {
    /** Connection / broadcast state — drives the speaker UI's status chip. */
    val state: StateFlow<NestsSpeakerState>

    /**
     * Begin announcing our speaker track and pumping mic frames out as
     * OBJECT_DATAGRAMs.
     *
     * [onLevel] fires once per Opus frame that successfully reaches
     * the wire, with the peak amplitude of the underlying PCM frame
     * normalized to `[0, 1]`. Default no-op so callers that don't
     * render a local-speaking ring pay zero cost. The callback runs
     * on the speaker's coroutine scope (typically the caller's
     * `viewModelScope`) so the consumer must keep its handler
     * lightweight. See [com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster.start]
     * for the ground-truth-vs-UI-state rationale.
     *
     * @throws IllegalStateException if a broadcast is already running on
     *   this speaker or the session is not [NestsSpeakerState.Connected].
     * @throws MoqProtocolException if the peer rejects the ANNOUNCE.
     */
    suspend fun startBroadcasting(onLevel: (Float) -> Unit = { /* no-op */ }): BroadcastHandle

    /**
     * Force the underlying transport / MoQ session to be torn down and
     * a fresh one opened in its place — without permanently closing
     * this speaker. Mirror of [NestsListener.recycleSession]; same
     * use case (network handover) and same default no-op for non-
     * reconnecting implementations.
     *
     * The reconnecting wrapper from
     * [connectReconnectingNestsSpeaker] overrides to close the
     * inner speaker so its orchestrator opens a fresh session.
     * For moq-lite speakers, the long-lived
     * [com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster]
     * keeps capturing through the recycle and the
     * [BroadcastHandle.swapPublisher]-based pump retargets onto
     * the new session's publisher with no audible gap.
     */
    suspend fun recycleSession() {
        // no-op — only the reconnecting wrapper has anywhere to
        // recycle to.
    }

    /** Tear down the MoQ session + transport. Idempotent. */
    suspend fun close()
}

/** Active broadcast on one [NestsSpeaker]. Returned from [NestsSpeaker.startBroadcasting]. */
interface BroadcastHandle {
    /**
     * Toggle whether mic frames reach the wire. The capture pipeline keeps
     * running, so unmute is sample-accurate. Default unmuted.
     */
    suspend fun setMuted(muted: Boolean)

    /** Whether the next OBJECT_DATAGRAM the peer would receive is silenced. */
    val isMuted: Boolean

    /**
     * Stop publishing. Releases the mic + encoder, sends UNANNOUNCE +
     * SUBSCRIBE_DONE on the wire. Idempotent.
     */
    suspend fun close()
}

/** Lifecycle states of a [NestsSpeaker]. */
sealed class NestsSpeakerState {
    data object Idle : NestsSpeakerState()

    data class Connecting(
        val step: ConnectStep,
    ) : NestsSpeakerState() {
        enum class ConnectStep {
            ResolvingRoom,
            OpeningTransport,
            MoqHandshake,
        }
    }

    /** Connection live; ready for [NestsSpeaker.startBroadcasting]. */
    data class Connected(
        val room: NestsRoomConfig,
        val negotiatedMoqVersion: Long,
    ) : NestsSpeakerState()

    /** Currently announcing + emitting OBJECT_DATAGRAMs for our track. */
    data class Broadcasting(
        val room: NestsRoomConfig,
        val negotiatedMoqVersion: Long,
        val isMuted: Boolean,
    ) : NestsSpeakerState()

    /**
     * The previous session dropped and the reconnect orchestrator
     * is waiting [delayMs] before trying again. Mirror of
     * [NestsListenerState.Reconnecting]; see that class's KDoc for
     * the state-machine contract.
     */
    data class Reconnecting(
        val attempt: Int,
        val delayMs: Long,
    ) : NestsSpeakerState()

    data class Failed(
        val reason: String,
        val cause: Throwable? = null,
    ) : NestsSpeakerState()

    data object Closed : NestsSpeakerState()
}

/**
 * IETF `draft-ietf-moq-transport-17` [NestsSpeaker] reference
 * implementation. **Not used in production** — the production speaker
 * path uses [MoqLiteNestsSpeaker] over moq-lite Lite-03 (see
 * `nestsClient/plans/2026-04-26-moq-lite-gap.md`). Kept for the IETF
 * unit-test suite (`NestsSpeakerTest`) and for any future IETF
 * MoQ-transport target.
 *
 * Wraps a connected [MoqSession] and plumbs an [NestBroadcaster]
 * through it on [startBroadcasting]. Construction does NOT open the
 * transport.
 */
class DefaultNestsSpeaker internal constructor(
    private val session: MoqSession,
    private val roomNamespace: TrackNamespace,
    private val speakerTrackName: ByteArray,
    private val captureFactory: () -> AudioCapture,
    private val encoderFactory: () -> OpusEncoder,
    private val scope: CoroutineScope,
    private val mutableState: MutableStateFlow<NestsSpeakerState>,
) : NestsSpeaker {
    override val state: StateFlow<NestsSpeakerState> = mutableState.asStateFlow()

    private val gate = Mutex()
    private var activeHandle: DefaultBroadcastHandle? = null

    override suspend fun startBroadcasting(onLevel: (Float) -> Unit): BroadcastHandle {
        gate.withLock {
            val current = state.value
            check(current is NestsSpeakerState.Connected) {
                "startBroadcasting requires Connected state, was $current"
            }
            check(activeHandle == null) { "speaker is already broadcasting" }

            val announce = session.announce(roomNamespace)
            val publisher =
                try {
                    announce.openTrack(speakerTrackName)
                } catch (t: Throwable) {
                    runCatching { announce.unannounce() }
                    throw t
                }
            val broadcaster =
                NestBroadcaster(
                    capture = captureFactory(),
                    encoder = encoderFactory(),
                    publisher = publisher,
                    scope = scope,
                )
            broadcaster.start(onLevel = onLevel)
            mutableState.value =
                NestsSpeakerState.Broadcasting(
                    room = current.room,
                    negotiatedMoqVersion = current.negotiatedMoqVersion,
                    isMuted = false,
                )
            val handle =
                DefaultBroadcastHandle(
                    broadcaster = broadcaster,
                    announce = announce,
                    parent = this,
                )
            activeHandle = handle
            return handle
        }
    }

    /**
     * Lockless: callable from inside [close] (which already holds [gate]) and
     * from [DefaultBroadcastHandle.close] (which doesn't). We compare-and-set
     * on activeHandle and update state atomically via the StateFlow.
     */
    internal fun broadcastClosed(handle: DefaultBroadcastHandle) {
        if (activeHandle !== handle) return
        activeHandle = null
        val current = mutableState.value
        if (current is NestsSpeakerState.Broadcasting) {
            mutableState.value =
                NestsSpeakerState.Connected(current.room, current.negotiatedMoqVersion)
        }
    }

    internal fun reportMuteState(muted: Boolean) {
        val current = mutableState.value
        if (current is NestsSpeakerState.Broadcasting) {
            mutableState.value = current.copy(isMuted = muted)
        }
    }

    override suspend fun close() {
        // Take the active handle under `gate` (so a concurrent
        // `startBroadcasting` can't observe a half-closed state), then
        // release the lock before calling `handle.close()` and
        // `session.close()` — both are long-running suspend operations
        // (broadcaster.stop awaits cancelAndJoin; session.close fires
        // SUBSCRIBE_DONE per attached subscriber and joins pumps). Holding
        // the gate through them would block any other API call on this
        // speaker for the entire teardown duration.
        val handle: DefaultBroadcastHandle?
        gate.withLock {
            if (state.value is NestsSpeakerState.Closed) return
            handle = activeHandle
            activeHandle = null
            mutableState.value = NestsSpeakerState.Closed
        }
        handle?.runCatching { close() }
        runCatching { session.close() }
    }
}

internal class DefaultBroadcastHandle(
    private val broadcaster: NestBroadcaster,
    private val announce: MoqSession.AnnounceHandle,
    private val parent: DefaultNestsSpeaker,
) : BroadcastHandle {
    @Volatile private var muted: Boolean = false

    @Volatile private var closed: Boolean = false

    override val isMuted: Boolean get() = muted

    override suspend fun setMuted(muted: Boolean) {
        if (closed) return
        this.muted = muted
        broadcaster.setMuted(muted)
        parent.reportMuteState(muted)
    }

    override suspend fun close() {
        if (closed) return
        closed = true
        runCatching { broadcaster.stop() }
        runCatching { announce.unannounce() }
        parent.broadcastClosed(this)
    }
}
