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

import com.vitorpamplona.nestsclient.transport.WebTransportFactory
import com.vitorpamplona.nestsclient.transport.WebTransportSession
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReconnectingNestsSpeakerTest {
    private val room =
        NestsRoomConfig(
            authBaseUrl = "https://relay.example.com/api/v1/nests",
            endpoint = "https://relay.example.com/moq",
            hostPubkey = "0".repeat(64),
            roomId = "abc",
        )
    private val signer = NostrSignerInternal(KeyPair())

    // The custom `connector` lambda below short-circuits the real
    // mintToken / WebTransport handshake, so these two sentinels
    // only satisfy the function signature — they're never invoked.
    private val httpClient =
        object : NestsClient {
            override suspend fun mintToken(
                room: NestsRoomConfig,
                publish: Boolean,
                signer: NostrSigner,
            ): String = error("not invoked — connector overrides")
        }
    private val transport =
        object : WebTransportFactory {
            override suspend fun connect(
                authority: String,
                path: String,
                bearerToken: String?,
            ): WebTransportSession = error("not invoked — connector overrides")
        }

    /**
     * Scripted [BroadcastHandle] paired with a [ScriptedSpeaker].
     * Tracks `setMuted` / `close` calls and exposes the latest
     * desired-mute value so tests can assert that the wrapper
     * replayed mute intent on a fresh handle after a recycle.
     */
    private class ScriptedBroadcastHandle : BroadcastHandle {
        // Atomic plumbing — the broadcast pump on Dispatchers.Default
        // races against the runBlocking-thread assertions, same
        // reasoning as ReconnectingNestsListenerTest's ScriptedListener.
        // Backing-property names match the public projection so
        // ktlint's `standard:backing-property-naming` rule passes.
        private val muted = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)
        private val setMutedCount = AtomicInteger(0)
        private val closeCount = AtomicInteger(0)

        override val isMuted: Boolean get() = muted.get()
        val isClosed: Boolean get() = closed.get()
        val setMutedCalls: Int get() = setMutedCount.get()
        val closeCalls: Int get() = closeCount.get()

        override suspend fun setMuted(muted: Boolean) {
            this.muted.set(muted)
            setMutedCount.incrementAndGet()
        }

        override suspend fun close() {
            closed.set(true)
            closeCount.incrementAndGet()
        }
    }

    /**
     * Scripted [NestsSpeaker] that opens in
     * [NestsSpeakerState.Connected] by default. Each call to
     * [startBroadcasting] returns a fresh [ScriptedBroadcastHandle]
     * the test can introspect.
     */
    private class ScriptedSpeaker(
        connectedRoom: NestsRoomConfig,
        moqVersion: Long = 1,
    ) : NestsSpeaker {
        private val mutableState =
            MutableStateFlow<NestsSpeakerState>(
                NestsSpeakerState.Connected(connectedRoom, moqVersion),
            )
        override val state: StateFlow<NestsSpeakerState> = mutableState.asStateFlow()

        private val _startCount = AtomicInteger(0)
        val startCount: Int get() = _startCount.get()

        // CopyOnWriteArrayList — the broadcast pump runs on
        // Dispatchers.Default while tests read `handles[0]` from the
        // runBlocking thread. A plain `mutableListOf` is neither
        // thread-safe nor publishes its writes; observed flake on
        // Ubuntu CI where the test thread sees startCount==1 (volatile
        // read on AtomicInteger) before the subsequent list mutation
        // becomes visible. Using a thread-safe list AND ordering the
        // list mutation BEFORE the AtomicInteger increment ensures
        // that any reader who sees startCount>=N also sees N entries
        // already published into `handles`.
        val handles: MutableList<ScriptedBroadcastHandle> =
            java.util.concurrent.CopyOnWriteArrayList()

        override suspend fun startBroadcasting(onLevel: (Float) -> Unit): BroadcastHandle {
            val handle = ScriptedBroadcastHandle()
            handles += handle
            // Increment AFTER appending to `handles` so the volatile
            // write on AtomicInteger publishes the list mutation —
            // tests poll on startCount and then read handles[index].
            _startCount.incrementAndGet()
            // Mirror the production speaker's contract: transition
            // Connected → Broadcasting on startBroadcasting.
            val current = mutableState.value
            if (current is NestsSpeakerState.Connected) {
                mutableState.value =
                    NestsSpeakerState.Broadcasting(
                        room = current.room,
                        negotiatedMoqVersion = current.negotiatedMoqVersion,
                        isMuted = false,
                    )
            }
            return handle
        }

        override suspend fun close() {
            mutableState.value = NestsSpeakerState.Closed
        }

        fun fail(reason: String) {
            mutableState.value = NestsSpeakerState.Failed(reason)
        }
    }

    /**
     * Pre-fail the connector for the first N attempts so the
     * orchestrator's failure path is exercised.
     */
    private class ScriptedFailure : NestsSpeaker {
        override val state: StateFlow<NestsSpeakerState> =
            MutableStateFlow(NestsSpeakerState.Failed("scripted-fail")).asStateFlow()

        override suspend fun startBroadcasting(onLevel: (Float) -> Unit): BroadcastHandle = error("never connected")

        override suspend fun close() {}
    }

    @Test
    fun startBroadcasting_returns_handle_against_first_session() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob())
            try {
                val first = ScriptedSpeaker(room)
                val reconnecting =
                    connectReconnectingNestsSpeaker(
                        httpClient = httpClient,
                        transport = transport,
                        scope = scope,
                        room = room,
                        signer = signer,
                        speakerPubkeyHex = "speaker-pubkey",
                        captureFactory = { error("not invoked — connector overrides") },
                        encoderFactory = { error("not invoked — connector overrides") },
                        policy = NestsReconnectPolicy(initialDelayMs = 1L),
                        connector = { first },
                    )

                val handle = reconnecting.startBroadcasting()

                // Wait for the pump to actually open the underlying
                // broadcast against the first session.
                withTimeout(5_000L) {
                    while (first.startCount == 0) delay(5)
                }

                assertEquals(1, first.startCount)
                assertEquals(1, first.handles.size)
                assertFalse(handle.isMuted, "fresh handle should not be muted")

                reconnecting.close()
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun proactive_token_refresh_recycles_speaker_without_failure_state() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob())
            try {
                val first = ScriptedSpeaker(room)
                val second = ScriptedSpeaker(room)
                val third = ScriptedSpeaker(room)
                val speakersInOrder = mutableListOf(first, second, third)

                // Synchronized list — the watcher coroutine appends
                // concurrently with the assertion's `.any { ... }`,
                // and a plain ArrayList raises CME on iteration. The
                // listener test gets away with a plain list because
                // its watcher is cancelled by `reconnecting.close()`
                // before the assertion runs in some timing windows;
                // we don't rely on that here.
                val seenStates: MutableList<NestsSpeakerState> =
                    java.util.Collections.synchronizedList(mutableListOf())

                val reconnecting =
                    connectReconnectingNestsSpeaker(
                        httpClient = httpClient,
                        transport = transport,
                        scope = scope,
                        room = room,
                        signer = signer,
                        speakerPubkeyHex = "speaker-pubkey",
                        captureFactory = { error("unused") },
                        encoderFactory = { error("unused") },
                        // 50 ms refresh — small enough to fire
                        // quickly; large enough that openOnce
                        // resolves Connected first.
                        tokenRefreshAfterMs = 50L,
                        connector = { speakersInOrder.removeAt(0) },
                    )

                val watcher =
                    scope.launch {
                        reconnecting.state.collect { seenStates += it }
                    }

                val handle = reconnecting.startBroadcasting()

                // Wait for at least 2 underlying speakers to have
                // been consumed (initial + first refresh).
                withTimeout(5_000L) {
                    while (speakersInOrder.size > 1) delay(10)
                }

                // Wait for the broadcast pump to have started a
                // broadcast on the second session.
                withTimeout(5_000L) {
                    while (second.startCount == 0) delay(5)
                }

                // Critical postcondition: the wrapper's outward
                // state must NEVER show Reconnecting or Failed
                // during a clean refresh — the user-visible UI
                // stays Broadcasting (or briefly Connected during
                // the cutover) throughout.
                //
                // Cancel the watcher BEFORE the assertion so we can
                // iterate seenStates without racing against further
                // appends. (synchronizedList only guards individual
                // ops, not iteration.)
                watcher.cancel()
                watcher.join()
                val snapshot: List<NestsSpeakerState> = synchronized(seenStates) { seenStates.toList() }
                val sawReconnecting = snapshot.any { it is NestsSpeakerState.Reconnecting }
                val sawFailed = snapshot.any { it is NestsSpeakerState.Failed }
                assertTrue(
                    !sawReconnecting && !sawFailed,
                    "proactive refresh must not surface Reconnecting/Failed; saw=$snapshot",
                )

                // Both speakers should have seen exactly one
                // startBroadcasting — the pump re-issues against
                // the new session, doesn't double-broadcast on
                // the old one.
                assertEquals(1, first.startCount)
                assertEquals(1, second.startCount)

                handle.close()
                reconnecting.close()
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun setMuted_intent_replays_on_new_session_after_refresh() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob())
            try {
                val first = ScriptedSpeaker(room)
                val second = ScriptedSpeaker(room)
                val speakersInOrder = mutableListOf(first, second)

                val reconnecting =
                    connectReconnectingNestsSpeaker(
                        httpClient = httpClient,
                        transport = transport,
                        scope = scope,
                        room = room,
                        signer = signer,
                        speakerPubkeyHex = "speaker-pubkey",
                        captureFactory = { error("unused") },
                        encoderFactory = { error("unused") },
                        tokenRefreshAfterMs = 80L,
                        connector = { speakersInOrder.removeAt(0) },
                    )

                val handle = reconnecting.startBroadcasting()

                withTimeout(5_000L) {
                    while (first.startCount == 0) delay(5)
                }

                // User mutes mid-broadcast on the first session.
                handle.setMuted(true)

                withTimeout(5_000L) {
                    while (first.handles[0].setMutedCalls == 0) delay(5)
                }
                assertTrue(first.handles[0].isMuted, "first handle should be muted by user toggle")

                // Wait for refresh to swap to the second session AND
                // for the pump to replay mute intent on the new handle.
                // `startCount > 0` is published from inside
                // ScriptedSpeaker.startBroadcasting BEFORE the pump
                // gets to run `if (desiredMuted) handle.setMuted(true)`,
                // so polling startCount alone races the replay step
                // under load (observed flake on CI). Wait for the
                // post-condition the assertion is about instead.
                withTimeout(5_000L) {
                    while (second.handles.isEmpty() || !second.handles[0].isMuted) delay(5)
                }

                // Critical postcondition: the new underlying handle
                // must have inherited the muted=true intent without
                // the user calling setMuted again.
                assertTrue(
                    second.handles[0].isMuted,
                    "post-refresh handle must inherit muted=true; setMutedCalls=${second.handles[0].setMutedCalls}",
                )

                handle.close()
                reconnecting.close()
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun close_cancels_pump_and_closes_live_handle() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob())
            try {
                val only = ScriptedSpeaker(room)
                val reconnecting =
                    connectReconnectingNestsSpeaker(
                        httpClient = httpClient,
                        transport = transport,
                        scope = scope,
                        room = room,
                        signer = signer,
                        speakerPubkeyHex = "speaker-pubkey",
                        captureFactory = { error("unused") },
                        encoderFactory = { error("unused") },
                        connector = { only },
                    )

                val handle = reconnecting.startBroadcasting()

                withTimeout(5_000L) {
                    while (only.startCount == 0) delay(5)
                }
                val live = only.handles[0]

                handle.close()

                // Live handle should be closed exactly once after
                // close(). The pump-side close+the user-side close
                // race; the handle's own idempotence guarantees
                // exactly one effective close.
                withTimeout(5_000L) {
                    while (!live.isClosed) delay(5)
                }
                assertTrue(live.isClosed)

                reconnecting.close()
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun first_attempt_failure_throws_from_connect() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob())
            try {
                // Connector returns a speaker already in Failed
                // state — connectReconnectingNestsSpeaker should
                // surface this as a thrown NestsException rather
                // than returning a wrapper in Failed state, so the
                // VM's `try/catch` around the connect call lights
                // up.
                var threw: Throwable? = null
                try {
                    connectReconnectingNestsSpeaker(
                        httpClient = httpClient,
                        transport = transport,
                        scope = scope,
                        room = room,
                        signer = signer,
                        speakerPubkeyHex = "speaker-pubkey",
                        captureFactory = { error("unused") },
                        encoderFactory = { error("unused") },
                        policy = NestsReconnectPolicy(maxAttempts = 1),
                        connector = { ScriptedFailure() },
                    )
                } catch (t: Throwable) {
                    threw = t
                }

                assertTrue(
                    threw is NestsException,
                    "first-attempt failure should throw NestsException; got $threw",
                )
                assertTrue(
                    threw.message?.contains("scripted-fail") == true,
                    "exception should preserve underlying reason; got ${threw.message}",
                )
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun startBroadcasting_after_close_throws() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob())
            try {
                val only = ScriptedSpeaker(room)
                val reconnecting =
                    connectReconnectingNestsSpeaker(
                        httpClient = httpClient,
                        transport = transport,
                        scope = scope,
                        room = room,
                        signer = signer,
                        speakerPubkeyHex = "speaker-pubkey",
                        captureFactory = { error("unused") },
                        encoderFactory = { error("unused") },
                        connector = { only },
                    )

                reconnecting.close()
                withTimeout(5_000L) {
                    reconnecting.state.first { it is NestsSpeakerState.Closed }
                }

                var threw: Throwable? = null
                try {
                    reconnecting.startBroadcasting()
                } catch (t: Throwable) {
                    threw = t
                }
                assertTrue(threw is IllegalStateException, "must reject startBroadcasting on closed wrapper; got $threw")
            } finally {
                scope.cancel()
            }
        }
}
