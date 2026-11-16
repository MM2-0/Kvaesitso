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
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterGameEvents
import com.vitorpamplona.quartz.nip64Chess.jester.JesterProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for interoperability bugs when playing chess against other clients.
 *
 * Category 1: SAN Format Mismatch — different engines produce different SAN notation
 * Category 2: Reconstruction with variant SAN
 * Category 3: Open Challenge Spectator Detection
 * Category 4: Missing Start Event
 */
class ChessInteropBugTest {
    private val whitePubkey = "white_pubkey_abc123"
    private val blackPubkey = "black_pubkey_def456"
    private val spectatorPubkey = "spectator_pubkey_xyz789"
    private val startEventId = "start-event-001"

    // Italian Game opening to reach castling position:
    // 1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5
    private val italianGameMoves = listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5")

    // Queenside castling setup: 1. d4 d5 2. Nc3 Nf6 3. Bf4 e6 4. Qd2 Be7
    private val queensideCastlingSetup = listOf("d4", "d5", "Nc3", "Nf6", "Bf4", "e6", "Qd2", "Be7")

    // ==========================================================================
    // CATEGORY 1: SAN NORMALIZATION (ChessEngine)
    // ==========================================================================

    @Test
    fun `engine handles 0-0 kingside castling notation`() {
        val engine = ChessEngine()
        for (move in italianGameMoves) {
            val result = engine.makeMove(move)
            assertTrue(result.success, "Setup move $move should succeed")
        }
        val result = engine.makeMove("0-0")
        assertTrue(
            result.success,
            "0-0 (zeros) should be accepted for kingside castling",
        )
    }

    @Test
    fun `engine handles 0-0-0 queenside castling notation`() {
        val engine = ChessEngine()
        for (move in queensideCastlingSetup) {
            val result = engine.makeMove(move)
            assertTrue(result.success, "Setup move $move should succeed")
        }
        val result = engine.makeMove("0-0-0")
        assertTrue(
            result.success,
            "0-0-0 (zeros) should be accepted for queenside castling",
        )
    }

    @Test
    fun `engine handles 0-0 with check suffix`() {
        // Setup a position where kingside castling gives check
        // Use FEN to create a contrived position where O-O gives check
        // Easier: just verify 0-0+ is normalized to O-O (check suffix preserved)
        val engine = ChessEngine()
        for (move in italianGameMoves) {
            engine.makeMove(move)
        }
        // 0-0+ — the + may or may not be relevant depending on position,
        // but normalization should convert 0-0+ → O-O and strip +
        // In Italian Game position, O-O doesn't give check, so we just verify
        // the move is accepted (the engine ignores spurious +)
        val result = engine.makeMove("0-0+")
        assertTrue(
            result.success,
            "0-0+ should be normalized and accepted",
        )
    }

    @Test
    fun `engine handles 0-0-0 with check suffix`() {
        val engine = ChessEngine()
        for (move in queensideCastlingSetup) {
            engine.makeMove(move)
        }
        val result = engine.makeMove("0-0-0+")
        assertTrue(
            result.success,
            "0-0-0+ should be normalized and accepted",
        )
    }

    @Test
    fun `engine handles move with annotation !`() {
        val engine = ChessEngine()
        val result = engine.makeMove("e4!")
        assertTrue(result.success, "e4! should be accepted after stripping annotation")
    }

    @Test
    fun `engine handles move with annotation !!`() {
        val engine = ChessEngine()
        val result = engine.makeMove("e4")
        assertTrue(result.success)
        val result2 = engine.makeMove("e5")
        assertTrue(result2.success)
        val result3 = engine.makeMove("Nf3!!")
        assertTrue(result3.success, "Nf3!! should be accepted after stripping annotation")
    }

    @Test
    fun `engine handles move with annotation questionmark-bang`() {
        val engine = ChessEngine()
        val result = engine.makeMove("e4?!")
        assertTrue(result.success, "e4?! should be accepted after stripping annotation")
    }

    @Test
    fun `engine handles standard O-O still works`() {
        val engine = ChessEngine()
        for (move in italianGameMoves) {
            engine.makeMove(move)
        }
        val result = engine.makeMove("O-O")
        assertTrue(result.success, "Standard O-O should still work (regression check)")
    }

    @Test
    fun `engine handles standard O-O-O still works`() {
        val engine = ChessEngine()
        for (move in queensideCastlingSetup) {
            engine.makeMove(move)
        }
        val result = engine.makeMove("O-O-O")
        assertTrue(result.success, "Standard O-O-O should still work (regression check)")
    }

    @Test
    fun `engine handles Qxf7# checkmate notation`() {
        val engine = ChessEngine()
        val setup = listOf("e4", "e5", "Qh5", "Nc6", "Bc4", "Nf6")
        for (move in setup) {
            engine.makeMove(move)
        }
        val result = engine.makeMove("Qxf7#")
        assertTrue(result.success, "Qxf7# should be accepted")
        assertTrue(engine.isCheckmate(), "Position should be checkmate")
    }

    @Test
    fun `engine handles Qh5 check notation`() {
        val engine = ChessEngine()
        // 1. e4 e5 2. Qh5 — Qh5 doesn't give check here but let's verify it works
        engine.makeMove("e4")
        engine.makeMove("e5")
        val result = engine.makeMove("Qh5")
        assertTrue(result.success, "Qh5 should be accepted")
    }

    @Test
    fun `engine stores normalized SAN in history`() {
        val engine = ChessEngine()
        for (move in italianGameMoves) {
            engine.makeMove(move)
        }
        // Castle with zero notation
        engine.makeMove("0-0")
        val history = engine.getMoveHistory()
        assertEquals(
            "O-O",
            history.last(),
            "History should store normalized O-O, not 0-0",
        )
    }

    @Test
    fun `engine stores normalized SAN without annotations in history`() {
        val engine = ChessEngine()
        engine.makeMove("e4!")
        val history = engine.getMoveHistory()
        assertEquals(
            "e4",
            history.last(),
            "History should store e4, not e4!",
        )
    }

    // ==========================================================================
    // CATEGORY 2: RECONSTRUCTION WITH VARIANT SAN
    // ==========================================================================

    @Test
    fun `reconstructor handles castling with 0-0 in history`() {
        val historyWithZeros = italianGameMoves + "0-0"
        val finalMove =
            createMoveEvent(
                id = "move-007",
                pubKey = whitePubkey,
                startEventId = startEventId,
                headEventId = "move-006",
                move = "0-0",
                fen = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQ1RK1 b kq - 5 4",
                history = historyWithZeros,
                opponentPubkey = blackPubkey,
            )

        val events =
            JesterGameEvents(
                startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, blackPubkey),
                moves = withEarlierMoves(finalMove),
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(
            result.isSuccess(),
            "Reconstruction with 0-0 in history should succeed",
        )

        val state = result.getOrNull()!!
        assertEquals(7, state.moveHistory.size)
        assertFalse(state.isDesynced, "Should not be desynced")
    }

    @Test
    fun `reconstructor handles castling with 0-0-0 in history`() {
        val historyWithZeros = queensideCastlingSetup + "0-0-0"
        val finalMove =
            createMoveEvent(
                id = "move-009",
                pubKey = whitePubkey,
                startEventId = startEventId,
                headEventId = "move-008",
                move = "0-0-0",
                fen = "rnbqk2r/ppp1bppp/4pn2/3p4/3P1B2/2N5/PPPQPPPP/2KR1BNR b kq - 6 5",
                history = historyWithZeros,
                opponentPubkey = blackPubkey,
            )

        val events =
            JesterGameEvents(
                startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, blackPubkey),
                moves = withEarlierMoves(finalMove),
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(
            result.isSuccess(),
            "Reconstruction with 0-0-0 in history should succeed",
        )

        val state = result.getOrNull()!!
        assertEquals(9, state.moveHistory.size)
        assertFalse(state.isDesynced, "Should not be desynced")
    }

    @Test
    fun `reconstructor handles mixed SAN formats in history`() {
        val mixedHistory = listOf("e4", "e5", "Nf3!", "Nc6", "Bc4", "Bc5", "0-0")
        val finalMove =
            createMoveEvent(
                id = "move-007",
                pubKey = whitePubkey,
                startEventId = startEventId,
                headEventId = "move-006",
                move = "0-0",
                fen = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQ1RK1 b kq - 5 4",
                history = mixedHistory,
                opponentPubkey = blackPubkey,
            )

        val events =
            JesterGameEvents(
                startEvent = createStartEvent(startEventId, whitePubkey, Color.WHITE, blackPubkey),
                moves = withEarlierMoves(finalMove),
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(
            result.isSuccess(),
            "Reconstruction with mixed SAN formats should succeed",
        )

        val state = result.getOrNull()!!
        assertEquals(7, state.moveHistory.size)
        assertFalse(state.isDesynced, "Should not be desynced")
    }

    @Test
    fun `reconstructor handles jester-style content without playerColor`() {
        val events =
            JesterGameEvents(
                startEvent = createOpenChallengeStartEventNoColor(startEventId, whitePubkey),
                moves = emptyList(),
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        assertEquals(
            whitePubkey,
            state.whitePubkey,
            "Challenger should default to WHITE when playerColor is absent",
        )
    }

    // ==========================================================================
    // CATEGORY 3: OPEN CHALLENGE SPECTATOR DETECTION
    // ==========================================================================

    @Test
    fun `open challenge with no p-tag no playerColor - only challenger moves - viewer is spectator`() {
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
                startEvent = createOpenChallengeStartEvent(startEventId, whitePubkey),
                moves = listOf(move1),
            )

        val result = ChessStateReconstructor.reconstruct(events, spectatorPubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        assertEquals(ViewerRole.SPECTATOR, state.viewerRole, "Third party should be SPECTATOR")
        assertFalse(state.isPlayerTurn(), "Spectators never have a turn")
    }

    @Test
    fun `open challenge with no p-tag - acceptor made moves - viewer is player`() {
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
                startEvent = createOpenChallengeStartEvent(startEventId, whitePubkey),
                moves = listOf(move1, move2),
            )

        val result = ChessStateReconstructor.reconstruct(events, blackPubkey)
        assertTrue(result.isSuccess())

        val state = result.getOrNull()!!
        assertEquals(
            ViewerRole.BLACK_PLAYER,
            state.viewerRole,
            "Acceptor who made a move should be BLACK_PLAYER",
        )
        assertEquals(Color.BLACK, state.playerColor)
        assertEquals(whitePubkey, state.opponentPubkey)
    }

    @Test
    fun `open challenge - challenger chose black via content - roles swap correctly`() {
        // Challenger chose black, so the acceptor will be white
        val move1 =
            createMoveEvent(
                id = "move-001",
                pubKey = blackPubkey, // Acceptor plays as white (first move)
                startEventId = startEventId,
                headEventId = startEventId,
                move = "e4",
                fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                history = listOf("e4"),
                opponentPubkey = whitePubkey,
            )

        val events =
            JesterGameEvents(
                // Challenger (whitePubkey) chose BLACK
                startEvent = createStartEvent(startEventId, whitePubkey, Color.BLACK, null),
                moves = listOf(move1),
            )

        val challengerResult = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(challengerResult.isSuccess())
        val challengerState = challengerResult.getOrNull()!!
        assertEquals(ViewerRole.BLACK_PLAYER, challengerState.viewerRole, "Challenger chose black")
        assertEquals(Color.BLACK, challengerState.playerColor)

        val acceptorResult = ChessStateReconstructor.reconstruct(events, blackPubkey)
        assertTrue(acceptorResult.isSuccess())
        val acceptorState = acceptorResult.getOrNull()!!
        assertEquals(ViewerRole.WHITE_PLAYER, acceptorState.viewerRole, "Acceptor should be white")
        assertEquals(Color.WHITE, acceptorState.playerColor)
    }

    @Test
    fun `jester-style content with version as integer`() {
        val content = """{"version":0,"kind":0,"fen":"${JesterProtocol.FEN_START}","history":[]}"""
        val event =
            JesterEvent(
                id = startEventId,
                pubKey = whitePubkey,
                createdAt = 1000,
                tags =
                    arrayOf(
                        arrayOf("e", JesterProtocol.START_POSITION_HASH),
                    ),
                content = content,
                sig = "sig-start",
            )

        assertTrue(event.isStartEvent(), "Event with version as integer should parse as start event")
        assertEquals(JesterProtocol.FEN_START, event.fen())
        assertTrue(event.history().isEmpty())
    }

    // ==========================================================================
    // CATEGORY 4: MISSING START EVENT
    // ==========================================================================

    @Test
    fun `reconstruction fails clearly with no start event`() {
        val events = JesterGameEvents(startEvent = null, moves = emptyList())

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result is ReconstructionResult.Error)
        assertEquals("No start event found", result.message)
    }

    @Test
    fun `reconstruction with moves but no start event`() {
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
                startEvent = null,
                moves = listOf(move1),
            )

        val result = ChessStateReconstructor.reconstruct(events, whitePubkey)
        assertTrue(result is ReconstructionResult.Error, "Should return Error, not crash")
        assertEquals("No start event found", result.message)
    }

    // ==========================================================================
    // HELPER FUNCTIONS
    // ==========================================================================

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

    private fun createOpenChallengeStartEvent(
        id: String,
        challengerPubkey: String,
        createdAt: Long = 1000,
    ): JesterEvent {
        val content = """{"version":"0","kind":0,"fen":"${JesterProtocol.FEN_START}","history":[],"nonce":"test123","playerColor":"white"}"""

        return JesterEvent(
            id = id,
            pubKey = challengerPubkey,
            createdAt = createdAt,
            tags =
                arrayOf(
                    arrayOf("e", JesterProtocol.START_POSITION_HASH),
                ),
            content = content,
            sig = "sig-start",
        )
    }

    private fun createOpenChallengeStartEventNoColor(
        id: String,
        challengerPubkey: String,
        createdAt: Long = 1000,
    ): JesterEvent {
        val content = """{"version":"0","kind":0,"fen":"${JesterProtocol.FEN_START}","history":[],"nonce":"test123"}"""

        return JesterEvent(
            id = id,
            pubKey = challengerPubkey,
            createdAt = createdAt,
            tags =
                arrayOf(
                    arrayOf("e", JesterProtocol.START_POSITION_HASH),
                ),
            content = content,
            sig = "sig-start",
        )
    }

    // ==========================================================================
    // CATEGORY 5: JESTER CONTENT SERIALIZATION
    // ==========================================================================

    @Test
    fun `serialized start content includes version fen and history fields`() {
        // Jester's isStartGameEvent checks arrayEquals(json.history, [])
        // which fails if history field is absent from JSON
        val content =
            com.vitorpamplona.quartz.nip64Chess.jester.JesterContent(
                kind = 0,
                nonce = "test1234",
                playerColor = "white",
            )
        val json =
            com.vitorpamplona.quartz.nip01Core.core.JsonMapper
                .toJson(content)

        assertTrue(json.contains("\"version\""), "JSON must include version field: $json")
        assertTrue(json.contains("\"fen\""), "JSON must include fen field: $json")
        assertTrue(json.contains("\"history\""), "JSON must include history field: $json")
        assertTrue(json.contains("\"history\":[]"), "history must be empty array: $json")
    }

    @Test
    fun `serialized move content includes version fen and history fields`() {
        val content =
            com.vitorpamplona.quartz.nip64Chess.jester.JesterContent(
                kind = 1,
                fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                move = "e4",
                history = listOf("e4"),
            )
        val json =
            com.vitorpamplona.quartz.nip01Core.core.JsonMapper
                .toJson(content)

        assertTrue(json.contains("\"version\""), "JSON must include version field: $json")
        assertTrue(json.contains("\"fen\""), "JSON must include fen field: $json")
        assertTrue(json.contains("\"history\""), "JSON must include history field: $json")
    }

    // ==========================================================================
    // HELPERS
    // ==========================================================================

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
}
