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

import com.vitorpamplona.amethyst.commons.nip64Chess.bot.ChessBotMove
import com.vitorpamplona.amethyst.commons.nip64Chess.bot.ChessBots
import com.vitorpamplona.quartz.nip64Chess.Color
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChessBotTest {
    private fun engineAfter(vararg sans: String) =
        ChessEngine().also { engine ->
            engine.reset()
            sans.forEach { assertTrue(engine.makeMove(it).success, it) }
        }

    @Test
    fun `minimax takes the mate in one`() {
        // 1. f3 e5 2. g4: Black mates with Qh4#.
        val engine = engineAfter("f3", "e5", "g4")
        assertEquals(ChessBotMove("d8", "h4"), ChessBots.minimax(depth = 2, random = Random(1)).choose(engine))
    }

    @Test
    fun `minimax takes a hanging queen`() {
        // 1. e4 d5 2. Qg4?? puts the queen where the c8 bishop takes it.
        val engine = engineAfter("e4", "d5", "Qg4")
        assertEquals(ChessBotMove("c8", "g4"), ChessBots.minimax(depth = 2, random = Random(1)).choose(engine))
    }

    @Test
    fun `the search leaves the engine where it found it`() {
        val engine = engineAfter("e4", "e5", "Nf3")
        val fen = engine.getFen()
        ChessBots.minimax(depth = 2, random = Random(3)).choose(engine)
        assertEquals(fen, engine.getFen())
        assertEquals(3, engine.getMoveHistory().size)
    }

    @Test
    fun `two bots play a legal game`() {
        val engine = engineAfter()
        val white = ChessBots.byName("minimax", depth = 1, random = Random(7))!!
        val black = ChessBots.byName("random", random = Random(8))!!
        repeat(60) {
            if (engine.isCheckmate() || engine.isStalemate()) return
            val bot = if (engine.getSideToMove() == Color.WHITE) white else black
            val move = assertNotNull(bot.choose(engine))
            val (square, promotion) = parsePromotion(move.to)
            assertTrue(engine.makeMove(move.from, square, promotion).success, "$move")
        }
        // Every move replays from the start: nothing the bots played was illegal.
        assertEquals(engine.getMoveHistory().size + 1, ChessReplay.of(engine.getMoveHistory()).plies.size)
    }
}
