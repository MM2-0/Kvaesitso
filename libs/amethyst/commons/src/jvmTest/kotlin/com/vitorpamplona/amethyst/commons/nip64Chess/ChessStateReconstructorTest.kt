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
package com.vitorpamplona.amethyst.commons.nip64Chess

import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.GameResult
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterGameEvents
import com.vitorpamplona.quartz.nip64Chess.jester.JesterProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for ChessStateReconstructor with Jester protocol.
 *
 * These tests verify that the reconstruction algorithm produces correct,
 * deterministic game states from Jester events (kind 30).
 *
 * Jester Protocol:
 * - Single event kind (30) for all chess messages
 * - Content is JSON with: version, kind, fen, move, history
 * - Start events have content.kind=0 and reference START_POSITION_HASH via e-tag
 * - Move events have content.kind=1 and reference [startEventId, headEventId] via e-tags
 * - Full move history is included in every move event
 * - No separate accept event - acceptance is implicit when opponent makes first move
 *
 * Test Categories:
 * 1. Basic game lifecycle (challenge, moves, end)
 * 2. Move ordering and reconstruction from history
 * 3. Game end conditions (checkmate, resignation)
 * 4. Viewer perspective (white player, black player, spectator)
 * 5. Determinism tests
 */
class ChessStateReconstructorTest {
    // Test pubkeys
    private val whitePubkey = "a".repeat(64)
    private val blackPubkey = "b".repeat(64)
    private val spectatorPubkey = "c".repeat(64)
    private val startEventId = "start-event-001"

    // ==========================================================================
    // 1. BASIC GAME LIFECYCLE TESTS
    // ==========================================================================

    @Test
    fun `reconstruct pending challenge - no moves yet`() {
        val events =
            JesterGameEvents(
                startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, blackPubkey),
                moves = emptyList(),
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result.isSuccess(), "Should reconstruct pending challenge")

        val state = result.getOrNull()!!
        assertTrue(state.isPendingChallenge, "Game should be pending")
        assertEquals(startEventId, state.startEventId)
        assertEquals(whitePubkey, state.whitePubkey)
        assertEquals(ViewerRole.WHITE_PLAYER, state.viewerRole)
        assertEquals(Color.WHITE, state.playerColor)
        assertTrue(state.moveHistory.isEmpty(), "No moves yet")
        assertEquals(GameStatus.InProgress, state.gameStatus)
    }

    @Test
    fun `reconstruct game with initial moves - e4 e5`() {
        val move1 =
            createMoveEvent(
                id = "move-001",
                pubKey = whitePubkey,
                startEventId = startEventId,
                headEventId = startEventId,
                move = "e4",
                fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                history = listOf("e4"),
                opponentPubkey = blackPubkey,
            )
        val move2 =
            createMoveEvent(
                id = "move-002",
                pubKey = blackPubkey,
                startEventId = startEventId,
                headEventId = "move-001",
                move = "e5",
                fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2",
                history = listOf("e4", "e5"),
                opponentPubkey = whitePubkey,
            )

        val events =
            JesterGameEvents(
                startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, blackPubkey),
                moves = listOf(move1, move2),
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        assertEquals(2, state.moveHistory.size)
        assertEquals("e4", state.moveHistory[0])
        assertEquals("e5", state.moveHistory[1])
        assertTrue(state.isPlayerTurn(), "White's turn after e4 e5")
        assertEquals(Color.WHITE, state.currentPosition.activeColor)
    }

    @Test
    fun `reconstruct game - acceptance is implicit via first opponent move`() {
        // In Jester, there's no separate accept event
        // Game is considered accepted when opponent makes their first move
        val move1 =
            createMoveEvent(
                id = "move-001",
                pubKey = whitePubkey,
                startEventId = startEventId,
                headEventId = startEventId,
                move = "e4",
                fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                history = listOf("e4"),
                opponentPubkey = blackPubkey,
            )

        val events =
            JesterGameEvents(
                startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, blackPubkey),
                moves = listOf(move1),
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        assertFalse(state.isPendingChallenge, "Game is active with moves")
        assertEquals(blackPubkey, state.blackPubkey)
        assertEquals(Color.BLACK, state.currentPosition.activeColor, "Black's turn after e4")
    }

    // ==========================================================================
    // 2. MOVE RECONSTRUCTION FROM HISTORY
    // ==========================================================================

    @Test
    fun `reconstruct from latest move with full history`() {
        // In Jester, each move contains the full history
        // Reconstruction uses the move with the longest history
        val move1 =
            createMoveEvent(
                id = "move-001",
                pubKey = whitePubkey,
                startEventId = startEventId,
                headEventId = startEventId,
                move = "e4",
                fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                history = listOf("e4"),
                opponentPubkey = blackPubkey,
                createdAt = 1000,
            )
        val move2 =
            createMoveEvent(
                id = "move-002",
                pubKey = blackPubkey,
                startEventId = startEventId,
                headEventId = "move-001",
                move = "e5",
                fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2",
                history = listOf("e4", "e5"),
                opponentPubkey = whitePubkey,
                createdAt = 2000,
            )
        val move3 =
            createMoveEvent(
                id = "move-003",
                pubKey = whitePubkey,
                startEventId = startEventId,
                headEventId = "move-002",
                move = "Nf3",
                fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
                history = listOf("e4", "e5", "Nf3"),
                opponentPubkey = blackPubkey,
                createdAt = 3000,
            )

        val events =
            JesterGameEvents(
                startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, blackPubkey),
                moves = listOf(move1, move2, move3),
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        assertEquals(3, state.moveHistory.size)
        assertEquals(listOf("e4", "e5", "Nf3"), state.moveHistory)
        assertEquals("move-003", state.headEventId, "Head should be latest move")
    }

    @Test
    fun `reconstruct with out-of-order moves - uses longest history`() {
        // Events might arrive out of order, but we use the longest history
        val move1 =
            createMoveEvent(
                id = "move-001",
                pubKey = whitePubkey,
                startEventId = startEventId,
                headEventId = startEventId,
                move = "e4",
                fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                history = listOf("e4"),
                opponentPubkey = blackPubkey,
            )
        val move2 =
            createMoveEvent(
                id = "move-002",
                pubKey = blackPubkey,
                startEventId = startEventId,
                headEventId = "move-001",
                move = "e5",
                fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2",
                history = listOf("e4", "e5"),
                opponentPubkey = whitePubkey,
            )

        // Events arrive in reverse order
        val events =
            JesterGameEvents(
                startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, blackPubkey),
                moves = listOf(move2, move1), // Reverse order
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        assertEquals(2, state.moveHistory.size)
        assertEquals("e4", state.moveHistory[0], "First move should be e4")
        assertEquals("e5", state.moveHistory[1], "Second move should be e5")
    }

    @Test
    fun `reconstruct with duplicate moves - uses longest history`() {
        val move1 =
            createMoveEvent(
                id = "move-001",
                pubKey = whitePubkey,
                startEventId = startEventId,
                headEventId = startEventId,
                move = "e4",
                fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                history = listOf("e4"),
                opponentPubkey = blackPubkey,
            )
        val move1Duplicate =
            createMoveEvent(
                id = "move-001-dup",
                pubKey = whitePubkey,
                startEventId = startEventId,
                headEventId = startEventId,
                move = "e4",
                fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                history = listOf("e4"),
                opponentPubkey = blackPubkey,
            )
        val move2 =
            createMoveEvent(
                id = "move-002",
                pubKey = blackPubkey,
                startEventId = startEventId,
                headEventId = "move-001",
                move = "e5",
                fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2",
                history = listOf("e4", "e5"),
                opponentPubkey = whitePubkey,
            )

        val events =
            JesterGameEvents(
                startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, blackPubkey),
                moves = listOf(move1, move1Duplicate, move2),
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        // Move2 has the longest history (2 moves), so that's what we reconstruct
        assertEquals(2, state.moveHistory.size, "Should have 2 moves from longest history")
    }

    // ==========================================================================
    // 3. GAME END CONDITIONS TESTS
    // ==========================================================================

    @Test
    fun `reconstruct scholar's mate - checkmate by engine detection`() {
        // Scholar's mate: 1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7#
        val finalMove =
            createMoveEvent(
                id = "move-007",
                pubKey = whitePubkey,
                startEventId = startEventId,
                headEventId = "move-006",
                move = "Qxf7#",
                fen = "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4",
                history = listOf("e4", "e5", "Qh5", "Nc6", "Bc4", "Nf6", "Qxf7#"),
                opponentPubkey = blackPubkey,
            )

        val events =
            JesterGameEvents(
                startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, blackPubkey),
                moves = withEarlierMoves(finalMove),
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        assertEquals(7, state.moveHistory.size)

        val engine = result.getEngineOrNull()!!
        assertTrue(engine.isCheckmate(), "Engine should detect checkmate")

        assertTrue(state.gameStatus is GameStatus.Finished)
        assertEquals(
            GameResult.WHITE_WINS,
            state.gameStatus.result,
            "White wins by checkmate",
        )
    }

    @Test
    fun `reconstruct game with resignation - result in move content`() {
        // In Jester, resignation is indicated by result field in move content
        val resignationMove =
            createMoveEventWithResult(
                id = "move-002",
                pubKey = blackPubkey, // Black resigns
                startEventId = startEventId,
                headEventId = "move-001",
                move = "e5",
                fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2",
                history = listOf("e4", "e5"),
                opponentPubkey = whitePubkey,
                result = "1-0", // White wins by resignation
                termination = "resignation",
            )

        val events =
            JesterGameEvents(
                startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, blackPubkey),
                moves = withEarlierMoves(resignationMove),
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        assertTrue(state.gameStatus is GameStatus.Finished)
        assertEquals(
            GameResult.WHITE_WINS,
            state.gameStatus.result,
            "White wins by resignation",
        )
    }

    @Test
    fun `reconstruct fool's mate - 4 move checkmate for black`() {
        // 1. f3 e5 2. g4 Qh4#
        val finalMove =
            createMoveEvent(
                id = "move-004",
                pubKey = blackPubkey,
                startEventId = startEventId,
                headEventId = "move-003",
                move = "Qh4#",
                fen = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3",
                history = listOf("f3", "e5", "g4", "Qh4#"),
                opponentPubkey = whitePubkey,
            )

        val events =
            JesterGameEvents(
                startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, blackPubkey),
                moves = withEarlierMoves(finalMove),
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        assertTrue(state.gameStatus is GameStatus.Finished)
        assertEquals(
            GameResult.BLACK_WINS,
            state.gameStatus.result,
            "Black wins by fool's mate",
        )
    }

    // ==========================================================================
    // 4. VIEWER PERSPECTIVE TESTS
    // ==========================================================================

    @Test
    fun `reconstruct as white player - correct perspective`() {
        val events = createBasicGameEvents()

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        assertEquals(ViewerRole.WHITE_PLAYER, state.viewerRole)
        assertEquals(Color.WHITE, state.playerColor)
        assertEquals(blackPubkey, state.opponentPubkey)
    }

    @Test
    fun `reconstruct as black player - correct perspective`() {
        val events = createBasicGameEvents()

        val result = ChessStateReconstructor.reconstruct(events, blackPubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        assertEquals(ViewerRole.BLACK_PLAYER, state.viewerRole)
        assertEquals(Color.BLACK, state.playerColor)
        assertEquals(whitePubkey, state.opponentPubkey)
    }

    @Test
    fun `reconstruct as spectator - white perspective, no turn`() {
        val events = createBasicGameEvents()

        val result = ChessStateReconstructor.reconstruct(events, spectatorPubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        assertEquals(ViewerRole.SPECTATOR, state.viewerRole)
        assertEquals(Color.WHITE, state.playerColor, "Spectators see from white's perspective")
        assertFalse(state.isPlayerTurn(), "Spectators never have a turn")
    }

    @Test
    fun `challenger is black - roles reversed correctly`() {
        // Challenger chooses black
        val move1 =
            createMoveEvent(
                id = "move-001",
                pubKey = whitePubkey, // Opponent (white) makes first move
                startEventId = startEventId,
                headEventId = startEventId,
                move = "e4",
                fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                history = listOf("e4"),
                opponentPubkey = blackPubkey,
            )

        val events =
            JesterGameEvents(
                startEvent = createStartEvent(startEventId, blackPubkey, Color.BLACK, whitePubkey),
                moves = listOf(move1),
            )

        val whiteResult = ChessStateReconstructor.reconstruct(events, whitePubkey)
        val blackResult = ChessStateReconstructor.reconstruct(events, blackPubkey)

        assertTrue(whiteResult.isSuccess())
        assertTrue(blackResult.isSuccess())

        val whiteState = whiteResult.getOrNull()!!
        val blackState = blackResult.getOrNull()!!

        assertEquals(whitePubkey, whiteState.whitePubkey)
        assertEquals(blackPubkey, whiteState.blackPubkey)
        assertEquals(ViewerRole.WHITE_PLAYER, whiteState.viewerRole)
        assertEquals(ViewerRole.BLACK_PLAYER, blackState.viewerRole)
    }

    // ==========================================================================
    // 5. ERROR HANDLING TESTS
    // ==========================================================================

    @Test
    fun `reconstruct without start event - should fail`() {
        val events = JesterGameEvents(startEvent = null, moves = emptyList())

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result is ReconstructionResult.Error)
        assertEquals("No start event found", result.message)
    }

    @Test
    fun `reconstruct with empty events - should fail`() {
        val events = JesterGameEvents.empty()

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result is ReconstructionResult.Error)
    }

    // ==========================================================================
    // 6. CASTLING AND SPECIAL MOVES TESTS
    // ==========================================================================

    @Test
    fun `reconstruct game with kingside castling`() {
        // 1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. O-O
        val finalMove =
            createMoveEvent(
                id = "move-007",
                pubKey = whitePubkey,
                startEventId = startEventId,
                headEventId = "move-006",
                move = "O-O",
                fen = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQ1RK1 b kq - 5 4",
                history = listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "O-O"),
                opponentPubkey = blackPubkey,
            )

        val events =
            JesterGameEvents(
                startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, blackPubkey),
                moves = withEarlierMoves(finalMove),
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        assertEquals(7, state.moveHistory.size)
        assertEquals("O-O", state.moveHistory[6])
        assertEquals(Color.BLACK, state.currentPosition.activeColor, "Black's turn after white castled")
    }

    // ==========================================================================
    // 7. DETERMINISM TESTS
    // ==========================================================================

    @Test
    fun `same events always produce same state`() {
        val events = createBasicGameEvents()

        // Reconstruct multiple times
        val results =
            (1..5).map {
                ChessStateReconstructor.reconstruct(events, whitePubkey)
            }

        // All should succeed
        assertTrue(results.all { it.isSuccess() })

        // All states should be identical
        val states = results.map { it.getOrNull()!! }
        val first = states.first()

        states.forEach { state ->
            assertEquals(first.startEventId, state.startEventId)
            assertEquals(first.moveHistory, state.moveHistory)
            assertEquals(first.gameStatus, state.gameStatus)
            assertEquals(first.currentPosition.activeColor, state.currentPosition.activeColor)
            assertEquals(first.currentPosition.moveNumber, state.currentPosition.moveNumber)
            assertEquals(first.appliedMoveNumbers, state.appliedMoveNumbers)
        }
    }

    @Test
    fun `different move list order produces same state`() {
        val move1 =
            createMoveEvent(
                id = "move-001",
                pubKey = whitePubkey,
                startEventId = startEventId,
                headEventId = startEventId,
                move = "e4",
                fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                history = listOf("e4"),
                opponentPubkey = blackPubkey,
            )
        val move2 =
            createMoveEvent(
                id = "move-002",
                pubKey = blackPubkey,
                startEventId = startEventId,
                headEventId = "move-001",
                move = "e5",
                fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2",
                history = listOf("e4", "e5"),
                opponentPubkey = whitePubkey,
            )
        val move3 =
            createMoveEvent(
                id = "move-003",
                pubKey = whitePubkey,
                startEventId = startEventId,
                headEventId = "move-002",
                move = "Nf3",
                fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
                history = listOf("e4", "e5", "Nf3"),
                opponentPubkey = blackPubkey,
            )

        val startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, blackPubkey)

        // Order 1: moves in order
        val events1 = JesterGameEvents(startEvent, listOf(move1, move2, move3))

        // Order 2: moves reversed (but move3 has longest history, so result is same)
        val events2 = JesterGameEvents(startEvent, listOf(move3, move1, move2))

        val result1 = ChessStateReconstructor.reconstruct(events1, whitePubkey)
        val result2 = ChessStateReconstructor.reconstruct(events2, whitePubkey)

        assertTrue(result1.isSuccess())
        assertTrue(result2.isSuccess())

        val state1 = result1.getOrNull()!!
        val state2 = result2.getOrNull()!!

        assertEquals(state1.moveHistory, state2.moveHistory, "Move history should be identical")
        assertEquals(state1.appliedMoveNumbers, state2.appliedMoveNumbers)
    }

    // ==========================================================================
    // 8. OPEN CHALLENGE TESTS
    // ==========================================================================

    @Test
    fun `reconstruct open challenge - no specific opponent`() {
        val events =
            JesterGameEvents(
                startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, null), // Open challenge
                moves = emptyList(),
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        assertTrue(state.isPendingChallenge)
        assertEquals(whitePubkey, state.whitePubkey)
        assertEquals(null, state.blackPubkey, "No opponent assigned yet for open challenge")
    }

    @Test
    fun `reconstruct open challenge with first opponent move`() {
        // When an unknown player makes the first move, they become the opponent
        val move1 =
            createMoveEvent(
                id = "move-001",
                pubKey = whitePubkey,
                startEventId = startEventId,
                headEventId = startEventId,
                move = "e4",
                fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                history = listOf("e4"),
                opponentPubkey = blackPubkey, // Now we know the opponent
            )

        val events =
            JesterGameEvents(
                startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, null), // Open challenge
                moves = listOf(move1),
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        assertFalse(state.isPendingChallenge)
        assertEquals(whitePubkey, state.whitePubkey)
        // Opponent is determined from move events when not specified in start
    }

    // ==========================================================================
    // HELPER FUNCTIONS
    // ==========================================================================

    private fun createBasicGameEvents(): JesterGameEvents {
        val move1 =
            createMoveEvent(
                id = "move-001",
                pubKey = whitePubkey,
                startEventId = startEventId,
                headEventId = startEventId,
                move = "e4",
                fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                history = listOf("e4"),
                opponentPubkey = blackPubkey,
            )
        val move2 =
            createMoveEvent(
                id = "move-002",
                pubKey = blackPubkey,
                startEventId = startEventId,
                headEventId = "move-001",
                move = "e5",
                fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2",
                history = listOf("e4", "e5"),
                opponentPubkey = whitePubkey,
            )

        return JesterGameEvents(
            startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, blackPubkey),
            moves = listOf(move1, move2),
        )
    }

    private fun createStartEvent(
        id: String,
        challengerPubkey: String,
        challengerColor: Color,
        opponentPubkey: String?,
        createdAt: Long = 1000,
    ): JesterEvent {
        val tags =
            mutableListOf(
                arrayOf("e", JesterProtocol.START_POSITION_HASH),
            )
        opponentPubkey?.let { tags.add(arrayOf("p", it)) }

        val colorString = if (challengerColor == Color.WHITE) "white" else "black"
        val content = """{"version":"0","kind":0,"fen":"${JesterProtocol.FEN_START}","history":[],"nonce":"test123","playerColor":"$colorString"}"""

        return JesterEvent(
            id = id,
            pubKey = challengerPubkey,
            createdAt = createdAt,
            tags = tags.toTypedArray(),
            content = content,
            sig = "sig-start",
        )
    }

    /** [last] and the moves before it, one per ply of its history, by the two players in turn: a whole game as relays deliver it. */
    private fun withEarlierMoves(last: JesterEvent): List<JesterEvent> {
        val history = last.history()
        val earlier = if (last.move() == null) history.size else history.size - 1
        return (1..earlier).map { n ->
            createMoveEvent(
                id = "move-%03d".format(n),
                pubKey = if (n % 2 == 1) whitePubkey else blackPubkey,
                startEventId = startEventId,
                headEventId = if (n == 1) startEventId else "move-%03d".format(n - 1),
                move = history[n - 1],
                fen = "",
                history = history.take(n),
                opponentPubkey = if (n % 2 == 1) blackPubkey else whitePubkey,
            )
        } + last
    }

    private fun createMoveEvent(
        id: String,
        pubKey: String,
        startEventId: String,
        headEventId: String,
        move: String,
        fen: String,
        history: List<String>,
        opponentPubkey: String,
        createdAt: Long = 2000,
    ): JesterEvent {
        val historyJson = history.joinToString(",") { "\"$it\"" }
        val content = """{"version":"0","kind":1,"fen":"$fen","move":"$move","history":[$historyJson]}"""

        return JesterEvent(
            id = id,
            pubKey = pubKey,
            createdAt = createdAt,
            tags =
                arrayOf(
                    arrayOf("e", startEventId),
                    arrayOf("e", headEventId),
                    arrayOf("p", opponentPubkey),
                ),
            content = content,
            sig = "sig-move",
        )
    }

    private fun createMoveEventWithResult(
        id: String,
        pubKey: String,
        startEventId: String,
        headEventId: String,
        move: String,
        fen: String,
        history: List<String>,
        opponentPubkey: String,
        result: String,
        termination: String,
        createdAt: Long = 2000,
    ): JesterEvent {
        val historyJson = history.joinToString(",") { "\"$it\"" }
        val content = """{"version":"0","kind":1,"fen":"$fen","move":"$move","history":[$historyJson],"result":"$result","termination":"$termination"}"""

        return JesterEvent(
            id = id,
            pubKey = pubKey,
            createdAt = createdAt,
            tags =
                arrayOf(
                    arrayOf("e", startEventId),
                    arrayOf("e", headEventId),
                    arrayOf("p", opponentPubkey),
                ),
            content = content,
            sig = "sig-move",
        )
    }
}
