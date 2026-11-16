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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.sun.org.apache.xml.internal.serializer.utils.Utils.messages
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.nestsclient.NestsClient
import com.vitorpamplona.nestsclient.NestsException
import com.vitorpamplona.nestsclient.NestsListener
import com.vitorpamplona.nestsclient.NestsListenerState
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.audio.AudioPlayer
import com.vitorpamplona.nestsclient.audio.OpusDecoder
import com.vitorpamplona.nestsclient.moq.SubscribeHandle
import com.vitorpamplona.nestsclient.transport.WebTransportFactory
import com.vitorpamplona.nestsclient.transport.WebTransportSession
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Drives [NestViewModel] with a fake [NestsListenerConnector] and
 * verifies its state-flow transitions:
 *   - connect() shows Connecting before the underlying connector returns
 *   - listener emits Connected → uiState collapses to Connected
 *   - listener emits Failed → uiState becomes Failed(reason)
 *   - setMuted updates uiState and propagates to active subscriptions
 *   - disconnect() returns to Idle and tears down the listener
 *
 * Subscribe-side (subscribeSpeaker → NestPlayer.play) is exercised via
 * the existing nestsClient tests; the cross-module audio glue would need
 * an internal SubscribeHandle constructor that's intentionally not visible
 * here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NestViewModelTest {
    // Tracks every VM created by `newViewModel` so each test can dispose them
    // before runTest exits. `connect()` starts an infinite cliff-detector
    // loop in viewModelScope; without an explicit teardown that loop spins
    // forever under runTest's virtual scheduler and the test wedges until
    // the real-time runTest deadline (60 s × N tests => :commons:jvmTest
    // hangs). [runVmTest] wraps each test body with a finally that calls
    // `disconnect()` (which cancels cliffDetectorJob via teardown()) so the
    // scheduler can become idle and runTest can return.
    private val createdVms = mutableListOf<NestViewModel>()

    @BeforeTest
    fun setupMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        // Belt-and-braces: if a test crashes before its finally runs, this
        // still tears down so the next test starts clean.
        createdVms.forEach { runCatching { it.disconnect() } }
        createdVms.clear()
        Dispatchers.resetMain()
    }

    /**
     * Test wrapper that guarantees every VM created via [newViewModel] is
     * disconnected before [runTest] tries to drain the test scheduler. The
     * 10-second [runTest] timeout is a safety net — a healthy test in this
     * class finishes in milliseconds; if we trip the timeout it almost
     * certainly means a new code path is leaving viewModelScope coroutines
     * running and needs its own teardown call.
     */
    private fun runVmTest(body: suspend TestScope.() -> Unit) =
        runTest(timeout = 10.seconds) {
            try {
                body()
            } finally {
                createdVms.forEach { runCatching { it.disconnect() } }
                createdVms.clear()
            }
        }

    @Test
    fun connectShowsConnectingThenConnected() =
        runVmTest {
            val fakeListener = FakeNestsListener()
            val vm = newViewModel { fakeListener }

            vm.connect()
            // Initial UI shows the resolving substep before the connector
            // (suspend) has a chance to actually return — that's the
            // ResolvingRoom optimistic enter we set on connect().
            assertIs<ConnectionUiState.Connecting>(vm.uiState.value.connection)

            // Connector resolves with the listener; it's still Idle until we
            // emit something. Drive it Connected directly.
            fakeListener.emit(NestsListenerState.Connected(room = ROOM_CONFIG, negotiatedMoqVersion = 0xff000011))

            assertEquals(ConnectionUiState.Connected, vm.uiState.value.connection)
        }

    @Test
    fun listenerFailedSurfacesAsUiFailed() =
        runVmTest {
            val fakeListener = FakeNestsListener()
            val vm = newViewModel { fakeListener }

            vm.connect()
            fakeListener.emit(NestsListenerState.Failed("relay rejected", IllegalStateException("oops")))

            val ui = vm.uiState.value.connection
            assertIs<ConnectionUiState.Failed>(ui)
            assertEquals("relay rejected", ui.reason)
        }

    @Test
    fun connectorThrowsBecomesUiFailed() =
        runVmTest {
            val vm = newViewModel { throw NestsException("dns blew up") }

            vm.connect()

            val ui = vm.uiState.value.connection
            assertIs<ConnectionUiState.Failed>(ui)
            assertEquals("dns blew up", ui.reason)
        }

    @Test
    fun setMutedFlipsUiStateAndIsRetained() =
        runVmTest {
            val vm = newViewModel { FakeNestsListener() }

            assertFalse(vm.uiState.value.isMuted)
            vm.setMuted(true)
            assertTrue(vm.uiState.value.isMuted)
            vm.setMuted(false)
            assertFalse(vm.uiState.value.isMuted)
        }

    @Test
    fun onStageNowDefaultsTrueAndSetOnStageFlipsIt() =
        runVmTest {
            val vm = newViewModel { FakeNestsListener() }

            // Defaults to true so a freshly-joined speaker advertises
            // onstage=1 on the first heartbeat (the heartbeat in
            // NestActivityContent reads this as the tag value).
            assertTrue(vm.uiState.value.onStageNow)
            vm.setOnStage(false)
            assertFalse(vm.uiState.value.onStageNow)
            vm.setOnStage(true)
            assertTrue(vm.uiState.value.onStageNow)
        }

    @Test
    fun onPresenceEventPopulatesPresencesMapAndDedupesByPubkey() =
        runVmTest {
            val vm = newViewModel { FakeNestsListener() }

            val alice = "a".repeat(64)
            val bob = "b".repeat(64)

            fun ev(
                pk: String,
                createdAt: Long,
                handRaised: Boolean,
            ) = com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent(
                id = "0".repeat(64),
                pubKey = pk,
                createdAt = createdAt,
                tags = arrayOf(arrayOf("a", "30312:host:room"), arrayOf("hand", if (handRaised) "1" else "0")),
                content = "",
                sig = "0".repeat(128),
            )

            vm.onPresenceEvent(ev(alice, 100L, handRaised = false))
            vm.onPresenceEvent(ev(bob, 100L, handRaised = false))
            vm.onPresenceEvent(ev(alice, 200L, handRaised = true))

            val snap = vm.presences.value
            assertEquals(setOf(alice, bob), snap.keys)
            assertTrue(snap[alice]!!.handRaised)
            assertEquals(200L, snap[alice]!!.updatedAtSec)
        }

    @Test
    fun evictStalePresencesDropsOldPeers() =
        runVmTest {
            val vm = newViewModel { FakeNestsListener() }
            val alice = "a".repeat(64)
            val bob = "b".repeat(64)

            vm.onPresenceEvent(
                com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent(
                    id = "0".repeat(64),
                    pubKey = alice,
                    createdAt = 100L,
                    tags = arrayOf(arrayOf("a", "30312:host:room")),
                    content = "",
                    sig = "0".repeat(128),
                ),
            )
            vm.onPresenceEvent(
                com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent(
                    id = "0".repeat(64),
                    pubKey = bob,
                    createdAt = 500L,
                    tags = arrayOf(arrayOf("a", "30312:host:room")),
                    content = "",
                    sig = "0".repeat(128),
                ),
            )

            vm.evictStalePresences(olderThanSec = 400L)
            assertEquals(setOf(bob), vm.presences.value.keys)
        }

    @Test
    fun onChatEventAccumulatesMessagesSortedByCreatedAt() =
        runVmTest {
            val vm = newViewModel { FakeNestsListener() }
            val alice = "a".repeat(64)

            fun chat(
                id: String,
                createdAt: Long,
                body: String,
            ) = LiveActivitiesChatMessageEvent(
                id = id,
                pubKey = alice,
                createdAt = createdAt,
                tags = arrayOf(arrayOf("a", "30312:host:room")),
                content = body,
                sig = "0".repeat(128),
            )

            fun note(event: LiveActivitiesChatMessageEvent): Note {
                val note = Note(event.id)
                note.event = event
                return note
            }

            // Out-of-order arrival on the relay must still produce a
            // chronological transcript on screen.
            vm.onChatEvent(note(chat(id = "1".repeat(64), createdAt = 200L, body = "second")))
            vm.onChatEvent(note(chat(id = "2".repeat(64), createdAt = 100L, body = "first")))

            val messages = vm.chat.value
            assertEquals(2, messages.size)
            assertEquals("second", messages[0].event!!.content)
            assertEquals("first", messages[1].event!!.content)
        }

    @Test
    fun onChatEventDedupesByEventId() =
        runVmTest {
            val vm = newViewModel { FakeNestsListener() }
            val alice = "a".repeat(64)
            val msg =
                LiveActivitiesChatMessageEvent(
                    id = "1".repeat(64),
                    pubKey = alice,
                    createdAt = 100L,
                    tags = arrayOf(arrayOf("a", "30312:host:room")),
                    content = "hello",
                    sig = "0".repeat(128),
                )

            val note = Note(msg.id)
            note.event = msg

            // Same id re-emitted by the relay on reconnect must not
            // produce a duplicate row.
            vm.onChatEvent(note)
            vm.onChatEvent(note)

            assertEquals(1, vm.chat.value.size)
        }

    @Test
    fun onReactionEventGroupsBySenderAndEvictsOnTick() =
        runVmTest {
            val vm = newViewModel { FakeNestsListener() }
            val alice = "a".repeat(64)
            val bob = "b".repeat(64)

            // Vary id per reaction so the dedup gate doesn't collapse
            // distinct kind-7s; the relay-replay dedup case has its own
            // assertion below.
            var nextId = 1

            fun rxn(
                from: String,
                to: String,
                content: String,
                createdAt: Long,
                id: String = "%064x".format(nextId++),
            ) = com.vitorpamplona.quartz.nip25Reactions.ReactionEvent(
                id = id,
                pubKey = from,
                createdAt = createdAt,
                tags = arrayOf(arrayOf("a", "30312:host:room"), arrayOf("p", to)),
                content = content,
                sig = "0".repeat(128),
            )

            // Two reactions sent BY alice land within the 30-s window.
            // The aggregator groups by sender (so the floating chip
            // rises from the reactor's avatar), NOT by the `p`-tag
            // target — both land under alice's key.
            vm.onReactionEvent(rxn(alice, bob, "🔥", 100L), nowSec = 100L)
            vm.onReactionEvent(rxn(alice, bob, "👏", 105L), nowSec = 105L)
            assertEquals(2, vm.recentReactions.value[alice]!!.size)

            // LocalCache.observeEvents re-emits the full matching list
            // on every cache mutation; the same kind-7 must collapse
            // into one overlay entry instead of stacking on each replay.
            val replayed = rxn(alice, bob, "🔥", 100L, id = "f".repeat(64))
            vm.onReactionEvent(replayed, nowSec = 105L)
            vm.onReactionEvent(replayed, nowSec = 105L)
            assertEquals(3, vm.recentReactions.value[alice]!!.size)

            // Tick advances past the window — all reactions evicted.
            vm.evictReactions(olderThanSec = 200L)
            assertEquals(emptyMap(), vm.recentReactions.value)
        }

    @Test
    fun onKickFlipsWasKickedAndDisconnects() =
        runVmTest {
            val fakeListener = FakeNestsListener()
            val vm = newViewModel { fakeListener }
            vm.connect()
            fakeListener.emit(NestsListenerState.Connected(room = ROOM_CONFIG, negotiatedMoqVersion = 0xff000011))
            assertEquals(ConnectionUiState.Connected, vm.uiState.value.connection)
            assertFalse(vm.wasKicked.value)

            vm.onKick()

            assertTrue(vm.wasKicked.value)
            // disconnect() flips connection back to Idle.
            assertEquals(ConnectionUiState.Idle, vm.uiState.value.connection)
        }

    @Test
    fun onKickIsIdempotent() =
        runVmTest {
            val fakeListener = FakeNestsListener()
            val vm = newViewModel { fakeListener }
            vm.connect()
            fakeListener.emit(NestsListenerState.Connected(room = ROOM_CONFIG, negotiatedMoqVersion = 0xff000011))

            vm.onKick()
            vm.onKick()

            assertTrue(vm.wasKicked.value)
        }

    @Test
    fun publishingNowDerivesFromBroadcastStateAndMute() {
        // Idle / connecting / failed: never publishing.
        assertFalse(NestUiState().publishingNow)
        assertFalse(NestUiState(broadcast = BroadcastUiState.Connecting).publishingNow)
        assertFalse(NestUiState(broadcast = BroadcastUiState.Failed("boom")).publishingNow)

        // Broadcasting unmuted: publishing.
        assertTrue(
            NestUiState(broadcast = BroadcastUiState.Broadcasting(isMuted = false)).publishingNow,
        )

        // Broadcasting muted: NOT publishing — matches the wire-tag
        // semantics ("publishing=1" means actually pushing packets,
        // not "hold a slot but silenced").
        assertFalse(
            NestUiState(broadcast = BroadcastUiState.Broadcasting(isMuted = true)).publishingNow,
        )
    }

    @Test
    fun connectIsIdempotentWhileConnecting() =
        runVmTest {
            val fakeListener = FakeNestsListener()
            var connectCalls = 0
            val vm =
                newViewModel {
                    connectCalls++
                    fakeListener
                }

            vm.connect()
            vm.connect()
            vm.connect()

            assertEquals(1, connectCalls, "connect() should not re-fire while a Connecting flow is in flight")
        }

    @Test
    fun disconnectReturnsToIdleAndClosesListener() =
        runVmTest {
            val fakeListener = FakeNestsListener()
            val vm = newViewModel { fakeListener }

            vm.connect()
            fakeListener.emit(NestsListenerState.Connected(ROOM_CONFIG, 0xff000011))
            assertEquals(ConnectionUiState.Connected, vm.uiState.value.connection)

            vm.disconnect()

            assertEquals(ConnectionUiState.Idle, vm.uiState.value.connection)
            assertTrue(fakeListener.closeCallCount > 0, "listener.close() should run on disconnect()")
        }

    @Test
    fun connectingStepMapsThroughToUiStep() =
        runVmTest {
            val fakeListener = FakeNestsListener()
            val vm = newViewModel { fakeListener }

            vm.connect()
            fakeListener.emit(
                NestsListenerState.Connecting(
                    NestsListenerState.Connecting.ConnectStep.OpeningTransport,
                ),
            )

            val ui = vm.uiState.value.connection
            assertIs<ConnectionUiState.Connecting>(ui)
            assertEquals(ConnectionUiState.Step.OpeningTransport, ui.step)
        }

    @Test
    fun speakingNowClearsOnTeardown() =
        runVmTest {
            val fakeListener = FakeNestsListener()
            val vm = newViewModel { fakeListener }

            vm.connect()
            fakeListener.emit(NestsListenerState.Connected(ROOM_CONFIG, 0xff000011))
            // Speaking-now is empty until an object arrives — exercising the
            // timeout-based clearing requires a live SubscribeHandle, which is
            // covered in nestsClient's pipe tests. Here we just verify the
            // teardown contract: speakingNow returns to empty after disconnect.
            vm.disconnect()

            assertTrue(
                vm.uiState.value.speakingNow
                    .isEmpty(),
            )
            assertTrue(
                vm.uiState.value.activeSpeakers
                    .isEmpty(),
            )
        }

    private fun TestScope.newViewModel(connect: suspend (CoroutineScope) -> NestsListener): NestViewModel =
        NestViewModel(
            httpClient = NoopNestsClient,
            transport = NoopWebTransportFactory,
            decoderFactory = { _, _ -> NoopOpusDecoder },
            playerFactory = { _, _ -> NoopAudioPlayer() },
            signer = NoopSigner,
            room = ROOM_CONFIG,
            connector =
                NestsListenerConnector { _, _, scope, _, _ ->
                    connect(scope)
                },
            // Wire to the test's backgroundScope so close calls run during
            // the test rather than escaping to the real GlobalScope.
            cleanupScope = backgroundScope,
        ).also { createdVms.add(it) }

    private class FakeNestsListener : NestsListener {
        private val mutable = MutableStateFlow<NestsListenerState>(NestsListenerState.Idle)
        override val state: StateFlow<NestsListenerState> = mutable.asStateFlow()
        var closeCallCount: Int = 0
            private set

        fun emit(s: NestsListenerState) {
            mutable.value = s
        }

        override suspend fun subscribeSpeaker(
            speakerPubkeyHex: String,
            maxLatencyMs: Long,
        ): SubscribeHandle = error("subscribeSpeaker not exercised in these tests — see NestPlayerTest in :nestsClient")

        override suspend fun close() {
            closeCallCount++
            mutable.value = NestsListenerState.Closed
        }
    }

    private object NoopNestsClient : NestsClient {
        override suspend fun mintToken(
            room: NestsRoomConfig,
            publish: Boolean,
            signer: NostrSigner,
        ): String = error("mintToken not used (connector seam bypasses it)")
    }

    private object NoopWebTransportFactory : WebTransportFactory {
        override suspend fun connect(
            authority: String,
            path: String,
            bearerToken: String?,
        ): WebTransportSession = error("connect not used (connector seam bypasses it)")
    }

    private object NoopOpusDecoder : OpusDecoder {
        override fun decode(opusPacket: ByteArray): ShortArray = ShortArray(0)

        override fun release() {}
    }

    private class NoopAudioPlayer : AudioPlayer {
        override fun start() {}

        override suspend fun enqueue(pcm: ShortArray) {}

        override fun setMuted(muted: Boolean) {}

        override fun stop() {}
    }

    private object NoopSigner : NostrSigner(pubKey = "0".repeat(64)) {
        override fun isWriteable(): Boolean = false

        override fun hasForegroundSupport(): Boolean = false

        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T = error("sign not used in this VM test")

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: String,
        ): String = error("not used")

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: String,
        ): String = error("not used")

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: String,
        ): String = error("not used")

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: String,
        ): String = error("not used")

        override suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent = error("not used")

        override suspend fun deriveKey(nonce: String): String = error("not used")

        override suspend fun signPsbt(psbtHex: String): String = error("not used")
    }

    companion object {
        private val ROOM_CONFIG =
            NestsRoomConfig(
                authBaseUrl = "https://relay.example.test/api/v1/nests",
                endpoint = "https://relay.example.test/moq",
                hostPubkey = "0".repeat(64),
                roomId = "test-room",
            )
    }
}
