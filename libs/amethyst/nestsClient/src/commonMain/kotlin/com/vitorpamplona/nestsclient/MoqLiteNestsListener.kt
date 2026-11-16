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

import com.vitorpamplona.nestsclient.moq.MoqObject
import com.vitorpamplona.nestsclient.moq.SubscribeHandle
import com.vitorpamplona.nestsclient.moq.SubscribeOk
import com.vitorpamplona.nestsclient.moq.lite.MoqLiteAnnounceStatus
import com.vitorpamplona.nestsclient.moq.lite.MoqLiteSession
import com.vitorpamplona.nestsclient.moq.lite.MoqLiteSubscribeException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Moq-lite-backed [NestsListener]. Wraps a connected [MoqLiteSession]
 * and exposes the same listener API the IETF [DefaultNestsListener]
 * does, so [connectNestsListener] can swap the framing layer without
 * changing the public surface that [com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel]
 * and downstream UI consume.
 *
 * Subscription mapping per the audio-rooms NIP draft + nests JS
 * reference (`@moq/watch/broadcast-Djx16jPC.js:6237`,
 * `@moq/publish/screen-B680RFft.js:5641`):
 *   - subscribeSpeaker(pubkey) → MoqLiteSession.subscribe(
 *         broadcast = pubkey, track = "audio/data")
 *   - subscribeCatalog(pubkey) → MoqLiteSession.subscribe(
 *         broadcast = pubkey, track = "catalog.json"). The catalog
 *     publishes one JSON object per group describing the broadcast
 *     (codec, sample rate, optional speaker-side hints). Not
 *     required for audio-only listening — the API exists so a
 *     consumer can show codec / live indicators when wanted.
 *
 * Frame adaptation: each [com.vitorpamplona.nestsclient.moq.lite.MoqLiteFrame]
 * is wrapped in a [MoqObject] so existing decoder / player code keeps
 * working unchanged. moq-lite frames carry no per-frame envelope, so
 * [MoqObject.objectId] is synthesised as a per-subscription monotonic
 * counter and [MoqObject.publisherPriority] uses the IETF default.
 */
class MoqLiteNestsListener internal constructor(
    private val session: MoqLiteSession,
    private val mutableState: MutableStateFlow<NestsListenerState>,
) : NestsListener {
    override val state: StateFlow<NestsListenerState> = mutableState.asStateFlow()

    /**
     * `internal` accessor for diagnostics: a test downcasts the
     * returned [WebTransportSession] to a platform-specific type
     * (e.g. `QuicWebTransportSession`) to read flow-control counters
     * from the underlying QUIC connection. Not part of the public
     * [com.vitorpamplona.nestsclient.NestsListener] surface.
     */
    internal val transport
        get() = session.transport

    override suspend fun subscribeSpeaker(
        speakerPubkeyHex: String,
        maxLatencyMs: Long,
    ): SubscribeHandle =
        wrapSubscription(
            broadcast = speakerPubkeyHex,
            track = AUDIO_TRACK,
            maxLatencyMs = maxLatencyMs,
            // Audio frames arrive in kixelated/moq `hang` "legacy"
            // container layout: each moq-lite frame is
            // `varint(timestamp_us) + raw_opus_packet`. Strip the
            // leading varint so downstream decoders see a pristine
            // Opus packet exactly as if it came from
            // [com.vitorpamplona.nestsclient.audio.OpusEncoder].
            stripLegacyTimestamp = true,
        )

    override suspend fun subscribeCatalog(speakerPubkeyHex: String): SubscribeHandle =
        wrapSubscription(
            broadcast = speakerPubkeyHex,
            track = CATALOG_TRACK,
            maxLatencyMs = 0L,
            // Catalog frames carry raw JSON bytes — no container
            // wrapping (the catalog itself is what tells you which
            // container the audio track uses).
            stripLegacyTimestamp = false,
        )

    private suspend fun wrapSubscription(
        broadcast: String,
        track: String,
        maxLatencyMs: Long,
        stripLegacyTimestamp: Boolean,
    ): SubscribeHandle {
        check(state.value is NestsListenerState.Connected) {
            "NestsListener.subscribe requires Connected state, was ${state.value}"
        }
        val handle =
            try {
                session.subscribe(broadcast = broadcast, track = track, maxLatencyMillis = maxLatencyMs)
            } catch (e: MoqLiteSubscribeException) {
                // Surface protocol-level rejections (audio OR catalog)
                // through MoqProtocolException, matching the IETF
                // listener's contract — keeps moq-lite types out of
                // UI / VM consumers regardless of which track was
                // refused.
                throw com.vitorpamplona.nestsclient.moq.MoqProtocolException(
                    "moq-lite subscribe rejected: ${e.message}",
                    e,
                )
            }

        val objectIdSeq = AtomicLong(0L)
        val mapped =
            handle.frames.map { frame ->
                val payload = if (stripLegacyTimestamp) stripLegacyTimestampPrefix(frame.payload) else frame.payload
                MoqObject(
                    trackAlias = handle.id,
                    groupId = frame.groupSequence,
                    objectId = objectIdSeq.getAndIncrement(),
                    publisherPriority = MoqLiteSession.DEFAULT_PRIORITY,
                    payload = payload,
                )
            }

        return SubscribeHandle(
            subscribeId = handle.id,
            trackAlias = handle.id, // moq-lite has no separate alias; reuse id
            ok = synthesizedOk(handle.ok),
            objects = mapped,
            unsubscribeAction = { handle.unsubscribe() },
            broadcastEndedCountProbe = { handle.broadcastEndedCount },
        )
    }

    override fun announces(): Flow<RoomAnnouncement> =
        // `channelFlow` (NOT `flow`) is mandatory here. The downstream
        // emission source is `handle.updates`, a MutableSharedFlow
        // populated by a launched bidi pump on the session's scope.
        // SharedFlow's `collect { lambda }` resumes the lambda inline
        // when emit happens — so when the pump emits a chunk, the
        // collect lambda runs on the pump's coroutine, not the
        // collector's. `flow {}` enforces the "emit only from the
        // builder coroutine" invariant and throws
        // `IllegalStateException: Flow invariant is violated` the
        // moment we call `emit()` from a different coroutine. Two-
        // phone production logs (commit 1fc8dbc, run 15:34:40)
        // showed exactly this — the first Active arrived, the
        // collect lambda fired, emit() threw, the wrapper's
        // `runCatching { listener.announces().collect { emit(it) } }`
        // swallowed it, `_announcedSpeakers` stayed empty forever,
        // and the cliff detector reported `active=1 announced=0`
        // indefinitely. `channelFlow` is the documented fix
        // (the error message itself recommends it): emissions go
        // through a buffered channel that's safe across coroutines.
        channelFlow {
            check(state.value is NestsListenerState.Connected) {
                "NestsListener.announces requires Connected state, was ${state.value}"
            }
            // Empty prefix → the relay sends every active broadcast
            // under our session's namespace (one per speaker). The
            // suffix is the broadcast's path component within the
            // room — for nests this is the speaker pubkey hex.
            val handle = session.announce(prefix = "")
            // Run the inner collect on a child of channelFlow's scope
            // so we can `awaitClose` on the producer side and let
            // close handle the unsub side-effect. Without the launch,
            // we'd block here in `collect` forever and never reach
            // `awaitClose` — but channelFlow's contract requires
            // awaitClose for clean shutdown.
            val pump =
                launch {
                    try {
                        handle.updates.collect { announce ->
                            send(
                                RoomAnnouncement(
                                    pubkey = announce.suffix,
                                    active = announce.status == MoqLiteAnnounceStatus.Active,
                                ),
                            )
                        }
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (_: Throwable) {
                        // updates flow died — close the channel so the
                        // consumer sees end-of-flow and the wrapper
                        // can decide what to do (typically wait for
                        // the next activeListener emission).
                    } finally {
                        // Close the announce bidi on the same scope
                        // that owned the pump. `handle.close` is
                        // suspend (it FINs the bidi and joins the
                        // session-side pump); run it under
                        // NonCancellable so cancellation of this
                        // coroutine doesn't skip the FIN.
                        withContext(NonCancellable) {
                            runCatching { handle.close() }
                        }
                    }
                }
            awaitClose {
                // Caller cancelled, OR the wrapping `collectLatest`
                // restarted (listener swap). Cancel the pump; its
                // finally above runs the suspend close on a
                // NonCancellable context.
                pump.cancel()
            }
        }

    override suspend fun close() {
        if (state.value is NestsListenerState.Closed) return
        try {
            session.close()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            mutableState.value = NestsListenerState.Closed
            throw ce
        } catch (_: Throwable) {
            // Best-effort.
        }
        mutableState.value = NestsListenerState.Closed
    }

    private fun synthesizedOk(ok: com.vitorpamplona.nestsclient.moq.lite.MoqLiteSubscribeOk): SubscribeOk =
        SubscribeOk(
            subscribeId = 0L, // not used by downstream consumers
            expiresMs = ok.maxLatencyMillis,
            groupOrder = 0x01,
            // moq-lite has no "content exists" signal — assume no history.
            contentExists = false,
            largestGroupId = null,
            largestObjectId = null,
        )

    companion object {
        /**
         * Track name nests publishers use for the Opus audio stream.
         * Source: `@moq/publish/screen-B680RFft.js:5641`
         * (`static TRACK = "audio/data"`).
         */
        const val AUDIO_TRACK: String = "audio/data"

        /**
         * Catalog track name moq-lite watchers subscribe to first to
         * receive a JSON description of the broadcast. Used by
         * [subscribeCatalog].
         */
        const val CATALOG_TRACK: String = "catalog.json"

        /**
         * Strip the leading `varint(timestamp_us)` prefix from a
         * kixelated/moq `hang` "legacy" container frame, returning
         * the bare codec payload (e.g. an Opus packet) for downstream
         * decoders. The varint length is encoded in the top 2 bits of
         * the first byte per RFC 9000 §16: `00`→1, `01`→2, `10`→4,
         * `11`→8 bytes. Returns the payload unchanged when the
         * varint header would overrun the payload (malformed frame —
         * surface upstream rather than silently mask).
         */
        internal fun stripLegacyTimestampPrefix(payload: ByteArray): ByteArray {
            if (payload.isEmpty()) return payload
            val tag = (payload[0].toInt() ushr 6) and 0x3
            val varintLen = 1 shl tag
            if (payload.size < varintLen) return payload
            return payload.copyOfRange(varintLen, payload.size)
        }
    }
}
