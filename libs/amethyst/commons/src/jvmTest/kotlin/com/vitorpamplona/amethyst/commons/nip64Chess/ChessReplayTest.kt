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
import com.vitorpamplona.quartz.nip64Chess.PieceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the board highlights and counts while stepping through a game. */
class ChessReplayTest {
    @Test
    fun `each ply knows the squares its move touched`() {
        val replay = ChessReplay.of(listOf("e4", "e5", "Nf3"))
        assertEquals(4, replay.plies.size)
        assertNull(replay.at(0).lastMove)
        assertEquals(ChessMoveSquares("e2", "e4"), replay.at(1).lastMove)
        assertEquals(ChessMoveSquares("e7", "e5"), replay.at(2).lastMove)
        assertEquals(ChessMoveSquares("g1", "f3"), replay.last.lastMove)
    }

    @Test
    fun `castling is the king's move`() {
        val replay = ChessReplay.of(listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "O-O"))
        assertEquals(ChessMoveSquares("e1", "g1"), replay.last.lastMove)
    }

    @Test
    fun `en passant is the capturing pawn's move`() {
        val replay = ChessReplay.of(listOf("e4", "a6", "e5", "d5", "exd6"))
        assertEquals(ChessMoveSquares("e5", "d6"), replay.last.lastMove)
    }

    @Test
    fun `promotion lands on the promotion square`() {
        val replay = ChessReplay.of(listOf("h4", "g5", "hxg5", "h6", "gxh6", "Bg7", "hxg7", "Nf6", "gxh8=Q"))
        assertEquals(10, replay.plies.size)
        assertEquals(ChessMoveSquares("g7", "h8"), replay.last.lastMove)
    }

    @Test
    fun `a check marks the king in check`() {
        val replay = ChessReplay.of(listOf("e4", "f5", "Qh5+"))
        assertEquals("e8", replay.last.checkSquare)
        assertNull(replay.at(2).checkSquare)
    }

    @Test
    fun `a move that does not replay ends the replay there`() {
        // Black has no knight that reaches f3.
        val replay = ChessReplay.of(listOf("e4", "Nf3"))
        assertEquals(2, replay.plies.size)
    }

    @Test
    fun `material counts captures and the balance`() {
        // 1. e4 d5 2. exd5 Qxd5: each side lost a pawn.
        val even = ChessMaterial.of(ChessReplay.of(listOf("e4", "d5", "exd5", "Qxd5")).last.position)
        assertEquals(listOf(PieceType.PAWN), even.capturedByWhite)
        assertEquals(listOf(PieceType.PAWN), even.capturedByBlack)
        assertEquals(0, even.whiteAdvantage)

        // 1. e4 d5 2. exd5: White is a pawn up.
        val up = ChessMaterial.of(ChessReplay.of(listOf("e4", "d5", "exd5")).last.position)
        assertEquals(1, up.whiteAdvantage)
        assertEquals(1, up.advantageOf(Color.WHITE))
        assertEquals(0, up.advantageOf(Color.BLACK))
    }

    @Test
    fun `an illegal pawn move is refused, not played`() {
        // chesslib parses "e4" for Black after 1. e4 as the e7 pawn jumping to e4 and taking.
        val engine = ChessEngine()
        engine.reset()
        engine.makeMove("e4")
        assertFalse(engine.makeMove("e4").success)
        assertFalse(engine.isLegalMove("e4"))
        assertTrue(engine.makeMove("e5").success)
        assertEquals(2, ChessReplay.of(listOf("e4", "e4", "e5")).plies.size)
    }
}
