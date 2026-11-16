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
package com.vitorpamplona.amethyst.commons.nipACWebRtcCalls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for PeerSessionManager — the extracted logic from CallController that
 * handles ICE candidate buffering, renegotiation glare, and answer routing.
 *
 * Uses FakePeerSession to simulate WebRTC PeerConnection behavior without
 * native libraries, making these runnable as JVM unit tests.
 */
class PeerSessionManagerTest {
    // Identities: alice < bob < carol (lexicographic order of hex chars)
    private val alice = "aaaa1111aaaa1111aaaa1111aaaa1111aaaa1111aaaa1111aaaa1111aaaa1111"
    private val bob = "bbbb2222bbbb2222bbbb2222bbbb2222bbbb2222bbbb2222bbbb2222bbbb2222"
    private val carol = "cccc3333cccc3333cccc3333cccc3333cccc3333cccc3333cccc3333cccc3333"

    private fun makeCandidate(label: String = "candidate:1") = IceCandidateData(sdp = label, sdpMid = "0", sdpMLineIndex = 0)

    // ========================================================================
    // ICE Candidate Buffering — Layer 1: Global Buffer
    // ========================================================================

    @Test
    fun iceCandidateNoSessionBufferedGlobally() {
        val manager = PeerSessionManager(localPubKey = bob)
        val candidate = makeCandidate("early-candidate")

        val action = manager.routeIceCandidate(alice, candidate)

        assertEquals(IceRouteAction.BUFFERED_GLOBALLY, action)
        assertEquals(1, manager.globalPendingCount(alice))
    }

    @Test
    fun multipleGlobalCandidatesBufferedForSamePeer() {
        val manager = PeerSessionManager(localPubKey = bob)

        manager.routeIceCandidate(alice, makeCandidate("c1"))
        manager.routeIceCandidate(alice, makeCandidate("c2"))
        manager.routeIceCandidate(alice, makeCandidate("c3"))

        assertEquals(3, manager.globalPendingCount(alice))
    }

    @Test
    fun globalBufferDrainedOnSessionCreation() {
        val manager = PeerSessionManager(localPubKey = bob)
        val fake = FakePeerSession()

        // Buffer 3 candidates before session exists
        manager.routeIceCandidate(alice, makeCandidate("c1"))
        manager.routeIceCandidate(alice, makeCandidate("c2"))
        manager.routeIceCandidate(alice, makeCandidate("c3"))
        assertEquals(3, manager.globalPendingCount(alice))

        // Create session — global candidates should drain into per-session buffer
        val entry = manager.registerSession(alice, fake)

        assertEquals(0, manager.globalPendingCount(alice), "Global buffer should be drained")
        assertEquals(3, entry.pendingIceCandidates.size, "Candidates should move to per-session buffer")
        assertEquals(0, fake.addedCandidates.size, "Candidates should NOT be added directly yet")
    }

    @Test
    fun globalBufferDrainedAndFlushedAfterRemoteDescription() {
        val manager = PeerSessionManager(localPubKey = bob)
        val fake = FakePeerSession()

        // Buffer candidates before session
        manager.routeIceCandidate(alice, makeCandidate("c1"))
        manager.routeIceCandidate(alice, makeCandidate("c2"))

        // Create session (drains global → per-session)
        manager.registerSession(alice, fake)
        assertEquals(2, manager.sessionPendingCount(alice))

        // Flush after remote description set
        val flushed = manager.flushPendingIceCandidates(alice)

        assertEquals(2, flushed)
        assertEquals(2, fake.addedCandidates.size, "Flushed candidates must reach the PeerConnection")
        assertEquals("c1", fake.addedCandidates[0].sdp)
        assertEquals("c2", fake.addedCandidates[1].sdp)
        assertEquals(0, manager.sessionPendingCount(alice))
    }

    // ========================================================================
    // ICE Candidate Buffering — Layer 2: Per-Session Buffer
    // ========================================================================

    @Test
    fun iceCandidateSessionExistsRemoteDescNotSetBufferedPerSession() {
        val manager = PeerSessionManager(localPubKey = bob)
        val fake = FakePeerSession()
        manager.registerSession(alice, fake)

        val action = manager.routeIceCandidate(alice, makeCandidate("mid-buffer"))

        assertEquals(IceRouteAction.BUFFERED_PER_SESSION, action)
        assertEquals(1, manager.sessionPendingCount(alice))
        assertEquals(0, fake.addedCandidates.size, "Should NOT add to PeerConnection yet")
    }

    @Test
    fun iceCandidateSessionExistsRemoteDescSetAddedDirectly() {
        val manager = PeerSessionManager(localPubKey = bob)
        val fake = FakePeerSession()
        manager.registerSession(alice, fake)
        manager.flushPendingIceCandidates(alice) // marks remoteDescriptionSet = true

        val action = manager.routeIceCandidate(alice, makeCandidate("direct"))

        assertEquals(IceRouteAction.ADDED_DIRECTLY, action)
        assertEquals(1, fake.addedCandidates.size)
        assertEquals("direct", fake.addedCandidates[0].sdp)
    }

    @Test
    fun perSessionBufferNotClearedOnSessionCreationOnlyOnFlush() {
        val manager = PeerSessionManager(localPubKey = bob)
        val fake = FakePeerSession()

        // Buffer globally
        manager.routeIceCandidate(alice, makeCandidate("global-c"))

        // Create session → global drains to per-session
        manager.registerSession(alice, fake)

        // Buffer more per-session (remote desc not set yet)
        manager.routeIceCandidate(alice, makeCandidate("session-c"))

        assertEquals(2, manager.sessionPendingCount(alice))
        assertEquals(0, fake.addedCandidates.size)

        // Now flush
        manager.flushPendingIceCandidates(alice)

        assertEquals(2, fake.addedCandidates.size)
        assertEquals("global-c", fake.addedCandidates[0].sdp)
        assertEquals("session-c", fake.addedCandidates[1].sdp)
    }

    // ========================================================================
    // ICE Buffering: Candidates Buffered While Ringing MUST Be Preserved
    // ========================================================================

    @Test
    fun candidatesBufferedWhileRingingNotLostWhenAccepting() {
        val manager = PeerSessionManager(localPubKey = bob)
        val fake = FakePeerSession()

        // Phase 1: Ringing — candidates arrive, no session yet
        manager.routeIceCandidate(alice, makeCandidate("ringing-c1"))
        manager.routeIceCandidate(alice, makeCandidate("ringing-c2"))
        assertEquals(2, manager.globalPendingCount(alice))

        // Phase 2: Accept call — create session
        manager.registerSession(alice, fake)
        assertEquals(0, manager.globalPendingCount(alice), "Global buffer drained")
        assertEquals(2, manager.sessionPendingCount(alice), "Candidates preserved in session")

        // Phase 3: Set remote description and flush
        manager.flushPendingIceCandidates(alice)

        assertEquals(2, fake.addedCandidates.size)
        assertEquals("ringing-c1", fake.addedCandidates[0].sdp)
        assertEquals("ringing-c2", fake.addedCandidates[1].sdp)
    }

    // ========================================================================
    // ICE Buffering: Multi-Peer (Group Call)
    // ========================================================================

    @Test
    fun globalBuffersAreSeparatePerPeer() {
        val manager = PeerSessionManager(localPubKey = bob)

        manager.routeIceCandidate(alice, makeCandidate("alice-c"))
        manager.routeIceCandidate(carol, makeCandidate("carol-c"))

        assertEquals(1, manager.globalPendingCount(alice))
        assertEquals(1, manager.globalPendingCount(carol))
    }

    @Test
    fun registeringOneSessionDoesNotDrainOtherPeersGlobalBuffer() {
        val manager = PeerSessionManager(localPubKey = bob)

        manager.routeIceCandidate(alice, makeCandidate("alice-c"))
        manager.routeIceCandidate(carol, makeCandidate("carol-c"))

        manager.registerSession(alice, FakePeerSession())

        assertEquals(0, manager.globalPendingCount(alice), "Alice's global buffer drained")
        assertEquals(1, manager.globalPendingCount(carol), "Carol's global buffer untouched")
    }

    // ========================================================================
    // Renegotiation Glare Handling
    // ========================================================================

    @Test
    fun renegotiationNoGlareAcceptsRemoteOffer() {
        val manager = PeerSessionManager(localPubKey = bob)
        val fake = FakePeerSession(signalingState = SignalingState.STABLE)
        manager.registerSession(alice, fake)

        var accepted = false
        val resolution =
            manager.resolveRenegotiationGlare(alice, "remote-sdp") { entry ->
                accepted = true
            }

        assertEquals(GlareResolution.NO_GLARE, resolution)
        assertTrue(accepted, "Should accept remote offer when no glare")
    }

    @Test
    fun renegotiationGlareLocalWinsHigherPubkey() {
        // carol (higher) is local, alice (lower) is remote → carol wins
        val manager = PeerSessionManager(localPubKey = carol)
        val fake = FakePeerSession(signalingState = SignalingState.HAVE_LOCAL_OFFER)
        manager.registerSession(alice, fake)

        var accepted = false
        val resolution =
            manager.resolveRenegotiationGlare(alice, "remote-sdp") { accepted = true }

        assertEquals(GlareResolution.LOCAL_WINS, resolution)
        assertFalse(accepted, "Should NOT accept remote offer when local wins")
    }

    @Test
    fun renegotiationGlareRemoteWinsRollback() {
        // alice (lower) is local, carol (higher) is remote → carol wins, alice rolls back
        val manager = PeerSessionManager(localPubKey = alice)
        val fake = FakePeerSession(signalingState = SignalingState.HAVE_LOCAL_OFFER)
        manager.registerSession(carol, fake)

        var accepted = false
        val resolution =
            manager.resolveRenegotiationGlare(carol, "remote-sdp") { accepted = true }

        assertEquals(GlareResolution.REMOTE_WINS_ROLLBACK, resolution)
        assertTrue(fake.rolledBack, "Must call rollback on the session")
        assertTrue(accepted, "Must accept remote offer after rollback")
    }

    @Test
    fun renegotiationGlareNoSessionReturnsNoSession() {
        val manager = PeerSessionManager(localPubKey = bob)

        val resolution =
            manager.resolveRenegotiationGlare(alice, "sdp") { }

        assertEquals(GlareResolution.NO_SESSION, resolution)
    }

    // ========================================================================
    // Callee-to-Callee Mesh: Initiation Tiebreaker
    // ========================================================================

    @Test
    fun meshInitiationLowerPubkeyShouldInitiate() {
        val manager = PeerSessionManager(localPubKey = alice)

        assertTrue(manager.shouldInitiateOffer(bob), "Lower pubkey (alice) should initiate to higher (bob)")
        assertTrue(manager.shouldInitiateOffer(carol), "Lower pubkey (alice) should initiate to higher (carol)")
    }

    @Test
    fun meshInitiationHigherPubkeyShouldWait() {
        val manager = PeerSessionManager(localPubKey = carol)

        assertFalse(manager.shouldInitiateOffer(alice), "Higher pubkey (carol) should NOT initiate to lower (alice)")
        assertFalse(manager.shouldInitiateOffer(bob), "Higher pubkey (carol) should NOT initiate to lower (bob)")
    }

    // ========================================================================
    // Answer Routing
    // ========================================================================

    @Test
    fun routeAnswerNoSessionReturnsNoSession() {
        val manager = PeerSessionManager(localPubKey = alice)

        val action = manager.routeAnswer(bob, "answer-sdp")

        assertEquals(AnswerRouteAction.NO_SESSION, action)
    }

    @Test
    fun routeAnswerWrongSignalingStateIgnored() {
        val manager = PeerSessionManager(localPubKey = alice)
        val fake = FakePeerSession(signalingState = SignalingState.STABLE)
        manager.registerSession(bob, fake)

        val action = manager.routeAnswer(bob, "answer-sdp")

        assertEquals(AnswerRouteAction.IGNORED_WRONG_STATE, action)
        assertNull(fake.lastRemoteDescription, "Should NOT set remote description")
    }

    @Test
    fun routeAnswerHavingLocalOfferApplied() {
        val manager = PeerSessionManager(localPubKey = alice)
        val fake = FakePeerSession(signalingState = SignalingState.HAVE_LOCAL_OFFER)
        manager.registerSession(bob, fake)

        // Buffer some ICE candidates before answer arrives
        manager.routeIceCandidate(bob, makeCandidate("pre-answer-c"))

        val action = manager.routeAnswer(bob, "answer-sdp")

        assertEquals(AnswerRouteAction.APPLIED, action)
        assertNotNull(fake.lastRemoteDescription)
        assertEquals(SdpType.ANSWER, fake.lastRemoteDescription!!.first)
        assertEquals("answer-sdp", fake.lastRemoteDescription!!.second)
        // Flush should have happened
        assertEquals(1, fake.addedCandidates.size, "Buffered candidates should be flushed after answer")
    }

    // ========================================================================
    // Session Lifecycle
    // ========================================================================

    @Test
    fun removeSessionDisposesAndCleansUp() {
        val manager = PeerSessionManager(localPubKey = bob)
        val fake = FakePeerSession()
        manager.registerSession(alice, fake)
        manager.routeIceCandidate(alice, makeCandidate("c"))

        val removed = manager.removeSession(alice)

        assertNotNull(removed)
        assertFalse(manager.hasSession(alice))
        assertEquals(0, manager.globalPendingCount(alice))
    }

    @Test
    fun disposeAllDisposesAllSessions() {
        val manager = PeerSessionManager(localPubKey = bob)
        val fakeAlice = FakePeerSession()
        val fakeCarol = FakePeerSession()
        manager.registerSession(alice, fakeAlice)
        manager.registerSession(carol, fakeCarol)

        manager.disposeAll()

        assertTrue(fakeAlice.disposed)
        assertTrue(fakeCarol.disposed)
        assertTrue(manager.allSessionKeys().isEmpty())
    }

    // ========================================================================
    // Full Scenario: P2P Call with ICE Buffering
    // ========================================================================

    @Test
    fun fullP2PFlowIceBufferingThroughAllPhases() {
        val manager = PeerSessionManager(localPubKey = bob)
        val fake = FakePeerSession()

        // Phase 1: Ringing — candidates arrive before session
        manager.routeIceCandidate(alice, makeCandidate("ringing-1"))
        manager.routeIceCandidate(alice, makeCandidate("ringing-2"))
        assertEquals(2, manager.globalPendingCount(alice))

        // Phase 2: Accept — create session (drains global → per-session)
        manager.registerSession(alice, fake)
        assertEquals(2, manager.sessionPendingCount(alice))

        // Phase 3: More candidates arrive before remote desc set
        manager.routeIceCandidate(alice, makeCandidate("pre-desc-1"))
        assertEquals(IceRouteAction.BUFFERED_PER_SESSION, manager.routeIceCandidate(alice, makeCandidate("pre-desc-2")))
        assertEquals(4, manager.sessionPendingCount(alice))

        // Phase 4: Remote description set → flush
        val flushed = manager.flushPendingIceCandidates(alice)
        assertEquals(4, flushed)
        assertEquals(4, fake.addedCandidates.size)

        // Phase 5: Late candidates arrive → added directly
        assertEquals(IceRouteAction.ADDED_DIRECTLY, manager.routeIceCandidate(alice, makeCandidate("post-desc")))
        assertEquals(5, fake.addedCandidates.size)
    }

    // ========================================================================
    // Full Scenario: Group Call with Mesh ICE Buffering
    // ========================================================================

    @Test
    fun groupCallMeshSetupWithIceBuffering() {
        // Bob is local. Alice (caller) and Carol (other callee) are peers.
        val manager = PeerSessionManager(localPubKey = bob)

        // Phase 1: Ringing — ICE from alice arrives before any session
        manager.routeIceCandidate(alice, makeCandidate("alice-ring-c"))

        // Phase 2: Accept — create session for alice
        val aliceFake = FakePeerSession()
        manager.registerSession(alice, aliceFake)
        manager.flushPendingIceCandidates(alice)
        assertEquals(1, aliceFake.addedCandidates.size)

        // Phase 3: Carol's ICE arrives before mesh session exists
        manager.routeIceCandidate(carol, makeCandidate("carol-early-c"))
        assertEquals(1, manager.globalPendingCount(carol))

        // Phase 4: Mesh setup — should bob initiate to carol?
        // bob < carol → bob should initiate
        assertTrue(manager.shouldInitiateOffer(carol))

        // Phase 5: Create session for carol (drains global)
        val carolFake = FakePeerSession()
        manager.registerSession(carol, carolFake)
        assertEquals(1, manager.sessionPendingCount(carol))

        // Phase 6: Carol's remote description set → flush
        manager.flushPendingIceCandidates(carol)
        assertEquals(1, carolFake.addedCandidates.size)
        assertEquals("carol-early-c", carolFake.addedCandidates[0].sdp)
    }

    // ========================================================================
    // Full Scenario: Renegotiation with Glare
    // ========================================================================

    @Test
    fun renegotiationGlareFullFlowLowerPubkeyRollsBack() {
        // Alice (lower) and Bob (higher) both send renegotiation offers simultaneously
        // Alice's manager handles Bob's incoming offer
        val aliceManager = PeerSessionManager(localPubKey = alice)
        val aliceFake = FakePeerSession(signalingState = SignalingState.HAVE_LOCAL_OFFER)
        aliceManager.registerSession(bob, aliceFake)
        aliceManager.flushPendingIceCandidates(bob)

        var acceptedSdp = ""
        val resolution =
            aliceManager.resolveRenegotiationGlare(bob, "bob-renego-sdp") { entry ->
                acceptedSdp = "bob-renego-sdp"
            }

        assertEquals(GlareResolution.REMOTE_WINS_ROLLBACK, resolution, "alice (lower) should lose to bob (higher)")
        assertTrue(aliceFake.rolledBack, "alice must rollback her local offer")
        assertEquals("bob-renego-sdp", acceptedSdp, "alice must accept bob's offer")

        // Bob's manager handles Alice's incoming offer
        val bobManager = PeerSessionManager(localPubKey = bob)
        val bobFake = FakePeerSession(signalingState = SignalingState.HAVE_LOCAL_OFFER)
        bobManager.registerSession(alice, bobFake)
        bobManager.flushPendingIceCandidates(alice)

        var bobAccepted = false
        val bobResolution =
            bobManager.resolveRenegotiationGlare(alice, "alice-renego-sdp") { bobAccepted = true }

        assertEquals(GlareResolution.LOCAL_WINS, bobResolution, "bob (higher) should win over alice (lower)")
        assertFalse(bobFake.rolledBack, "bob should NOT rollback")
        assertFalse(bobAccepted, "bob should ignore alice's offer")
    }
}
