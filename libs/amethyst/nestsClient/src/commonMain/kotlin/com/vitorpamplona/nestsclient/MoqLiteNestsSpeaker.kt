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
import com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.moq.lite.MoqLiteHangCatalog
import com.vitorpamplona.nestsclient.moq.lite.MoqLitePublisherHandle
import com.vitorpamplona.nestsclient.moq.lite.MoqLiteSession
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Moq-lite-backed [NestsSpeaker]. Mirrors [MoqLiteNestsListener] on the
 * publish side: takes a connected [MoqLiteSession] and exposes the
 * existing [NestsSpeaker] API so [connectNestsSpeaker] can swap the
 * framing layer without changing any downstream consumers.
 *
 * Wire-flow per [MoqLiteSession.publish]:
 *   - the session opens a publisher state when [startBroadcasting] is
 *     called, then services every relay-opened Announce / Subscribe
 *     bidi automatically.
 *   - frames pushed via [MoqLitePublisherHandle.send] go on a fresh
 *     uni stream per group, framed as `varint(size) + payload`.
 */
class MoqLiteNestsSpeaker internal constructor(
    private val session: MoqLiteSession,
    private val speakerPubkeyHex: String,
    private val captureFactory: () -> AudioCapture,
    private val encoderFactory: () -> OpusEncoder,
    private val scope: CoroutineScope,
    private val mutableState: MutableStateFlow<NestsSpeakerState>,
    /**
     * How many Opus frames to pack into one moq-lite group / QUIC uni
     * stream. Forwarded to [NestMoqLiteBroadcaster.framesPerGroup] —
     * see that field's kdoc for the production stream-cliff rationale.
     * Defaults to [NestMoqLiteBroadcaster.DEFAULT_FRAMES_PER_GROUP].
     */
    private val framesPerGroup: Int = NestMoqLiteBroadcaster.DEFAULT_FRAMES_PER_GROUP,
    /**
     * Per-broadcast audio shape (channel count, future bitrate
     * variants). Threaded into the catalog payload the speaker emits
     * on the `catalog.json` track, so listeners see the shape the
     * caller's encoder actually produces. Caller MUST construct the
     * encoder + capture with a matching channel layout — see
     * [AudioBroadcastConfig] for the contract. Defaults to mono so
     * existing call sites that don't pass a config keep the prior
     * behaviour.
     */
    private val broadcastConfig: AudioBroadcastConfig = AudioBroadcastConfig(),
) : NestsSpeaker,
    HotSwappablePublisherSource {
    override val state: StateFlow<NestsSpeakerState> = mutableState.asStateFlow()

    private val gate = Mutex()
    private var activeHandle: MoqLiteBroadcastHandle? = null

    override suspend fun startBroadcasting(onLevel: (Float) -> Unit): BroadcastHandle {
        gate.withLock {
            val current = state.value
            check(current is NestsSpeakerState.Connected) {
                "startBroadcasting requires Connected state, was $current"
            }
            check(activeHandle == null) { "speaker is already broadcasting" }

            // Per the audio-rooms NIP draft + JS reference
            // (`@moq/publish/screen-B680RFft.js:5641`), publishers
            // claim a broadcast suffix equal to their pubkey hex and
            // the audio data sits on track "audio/data". The publisher
            // must be track-scoped because listeners typically open
            // BOTH a catalog subscribe AND an audio subscribe per
            // speaker; without the track filter the publisher would
            // route Opus frames onto whichever subscribe arrived first
            // (in practice the catalog one) and the audio subscription
            // would silently starve.
            val publisher =
                session.publish(
                    broadcastSuffix = speakerPubkeyHex,
                    track = MoqLiteNestsListener.AUDIO_TRACK,
                )
            // From here on, the publisher is registered in the session
            // and has a live announce/subscribe path. Anything that
            // throws before we hand a working handle back to the caller
            // must close the publisher to avoid leaking it inside the
            // session's `activePublisher` slot — otherwise a subsequent
            // startBroadcasting fails with "already publishing" and the
            // publisher's announce state never tears down.
            //
            // Companion catalog publisher: the kixelated/moq browser
            // watcher (and any standards-aligned moq-lite consumer)
            // discovers a broadcast's tracks by subscribing to the
            // `catalog.json` track and parsing the latest group's
            // payload as a JSON manifest. Without this track our
            // broadcasts are *invisible* to the canonical web watcher
            // even though our audio frames are streaming fine — the
            // watcher has nothing to subscribe to. Open it alongside
            // audio so a single broadcast advertises both tracks.
            val catalogPublisher =
                try {
                    session.publish(
                        broadcastSuffix = speakerPubkeyHex,
                        track = MoqLiteNestsListener.CATALOG_TRACK,
                    )
                } catch (t: Throwable) {
                    runCatching { publisher.close() }
                    throw t
                }
            val broadcaster =
                try {
                    NestMoqLiteBroadcaster(
                        capture = captureFactory(),
                        encoder = encoderFactory(),
                        initialPublisher = publisher,
                        scope = scope,
                        framesPerGroup = framesPerGroup,
                    ).also {
                        it.start(
                            onTerminalFailure = {
                                // Broadcaster bailed after sustained
                                // publisher.send failures. Flip to
                                // Failed so the reconnect orchestrator
                                // sees a terminal state and recycles
                                // the session — without this signal the
                                // outward state stays on Broadcasting
                                // and the room is silently mute.
                                reportBroadcastTerminalFailure()
                            },
                            onLevel = onLevel,
                        )
                    }
                } catch (t: Throwable) {
                    runCatching { catalogPublisher.close() }
                    runCatching { publisher.close() }
                    throw t
                }
            // Catalog emit-on-subscribe: every time the relay opens a
            // SUBSCRIBE bidi for catalog.json, fire the hook to write
            // one group + FIN. moq-lite serves new listeners from the
            // relay's per-track latest-group cache, so emitting once
            // per relay-side subscribe is enough — late-joining
            // watchers behind the same relay get the cached blob
            // without us having to maintain a periodic re-emit loop.
            // Set BEFORE the relay can race a SUBSCRIBE in; in
            // practice the relay's SUBSCRIBE bidi takes a network
            // round-trip after our ANNOUNCE Active, so this is safe
            // even though the setter is non-suspending.
            val catalogJson =
                MoqLiteHangCatalog.opus48kJsonBytes(
                    audioTrackName = MoqLiteNestsListener.AUDIO_TRACK,
                    numberOfChannels = broadcastConfig.channelCount,
                )
            catalogPublisher.setOnNewSubscriber {
                runCatchingCancellable {
                    catalogPublisher.send(catalogJson)
                    catalogPublisher.endGroup()
                }
            }
            mutableState.value =
                NestsSpeakerState.Broadcasting(
                    room = current.room,
                    negotiatedMoqVersion = current.negotiatedMoqVersion,
                    isMuted = false,
                )
            val handle =
                MoqLiteBroadcastHandle(
                    broadcaster = broadcaster,
                    publisher = publisher,
                    catalogPublisher = catalogPublisher,
                    parent = this,
                )
            activeHandle = handle
            return handle
        }
    }

    /**
     * [HotSwappablePublisherSource] implementation. See the interface
     * kdoc — this method mints a fresh publisher on the session
     * WITHOUT spinning up a broadcaster on top of it. Used by the
     * reconnect wrapper's hot-swap path; not called from the
     * non-reconnecting path which goes through [startBroadcasting].
     */
    override suspend fun openPublisherForHotSwap(
        track: String,
        startSequence: Long,
    ): MoqLitePublisherHandle =
        session.publish(
            broadcastSuffix = speakerPubkeyHex,
            track = track,
            startSequence = startSequence,
        )

    /**
     * Compare-and-clear that runs from inside [close] (already holds
     * [gate]) and from [MoqLiteBroadcastHandle.close] (doesn't).
     * Mirrors [DefaultNestsSpeaker.broadcastClosed].
     */
    internal fun broadcastClosed(handle: MoqLiteBroadcastHandle) {
        if (activeHandle !== handle) return
        activeHandle = null
        val current = mutableState.value
        if (current is NestsSpeakerState.Broadcasting) {
            mutableState.value =
                NestsSpeakerState.Connected(current.room, current.negotiatedMoqVersion)
        }
    }

    /**
     * [HotSwappablePublisherSource] implementation. The hot-swap pump
     * drives publishing through [openPublisherForHotSwap] rather than
     * [startBroadcasting], so it has to flip the state machine into
     * Broadcasting itself. Connected → Broadcasting on first call;
     * re-applies the mute intent if already Broadcasting (a session
     * swap re-enters this with the new session still in Connected, so
     * the common case is the Connected branch). No-op once terminal.
     */
    override fun reportBroadcasting(isMuted: Boolean) {
        val current = mutableState.value
        mutableState.value =
            when (current) {
                is NestsSpeakerState.Connected -> {
                    NestsSpeakerState.Broadcasting(
                        room = current.room,
                        negotiatedMoqVersion = current.negotiatedMoqVersion,
                        isMuted = isMuted,
                    )
                }

                is NestsSpeakerState.Broadcasting -> {
                    current.copy(isMuted = isMuted)
                }

                else -> {
                    return
                }
            }
    }

    /**
     * Called from the broadcaster's `onTerminalFailure` callback (off
     * the speaker's coroutine). Transitions the speaker to `Failed` so
     * the reconnect orchestrator (`ReconnectingNestsSpeaker`) observes
     * a terminal state and recycles the session. No-op if the speaker
     * is already in a terminal state.
     *
     * Also exposed via [HotSwappablePublisherSource.reportBroadcastTerminalFailure]
     * so the hot-swap pump (which owns its own long-lived broadcaster)
     * can drive the same orchestrator-reconnect path the legacy
     * `startBroadcasting` flow does.
     */
    override fun reportBroadcastTerminalFailure() {
        val current = mutableState.value
        if (current is NestsSpeakerState.Failed || current is NestsSpeakerState.Closed) return
        mutableState.value =
            NestsSpeakerState.Failed(
                reason = "broadcast pipeline gave up — likely transport loss",
            )
    }

    internal fun reportMuteState(muted: Boolean) {
        val current = mutableState.value
        if (current is NestsSpeakerState.Broadcasting) {
            mutableState.value = current.copy(isMuted = muted)
        }
    }

    override suspend fun close() {
        // Take + clear under [gate] so a concurrent `startBroadcasting`
        // can't observe a half-closed state, then run the long-running
        // suspends (handle.close + session.close) outside the lock.
        val handle: MoqLiteBroadcastHandle?
        gate.withLock {
            if (state.value is NestsSpeakerState.Closed) return
            handle = activeHandle
            activeHandle = null
            mutableState.value = NestsSpeakerState.Closed
        }
        // Don't `runCatching { handle.close() }` — that swallows
        // CancellationException too, breaking structured cancellation
        // when the parent scope is cancelling teardown.
        if (handle != null) {
            try {
                handle.close()
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Best-effort — already cleared activeHandle.
            }
        }
        try {
            session.close()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Best-effort.
        }
    }
}
