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
package com.vitorpamplona.amethyst.commons.nip64Chess.bot

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessEngine
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessMaterial
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.PieceType
import kotlin.random.Random

/** A move as a bot plays it: two squares, with a promotion letter on [to] (`e8q`), the way the board hands moves over. */
@Immutable
data class ChessBotMove(
    val from: String,
    val to: String,
)

/**
 * Picks a move for the side to move in an engine's position, or null when it has none. It may
 * try moves on the engine but must leave it as it found it.
 *
 * These are test opponents, there to play games end to end through the app and relays, not to
 * play well. They are written here rather than wrapping an outside engine because the strong ones
 * (Stockfish) are GPL and cannot ship in an MIT app.
 */
fun interface ChessBot {
    fun choose(engine: ChessEngine): ChessBotMove?
}

object ChessBots {
    const val RANDOM = "random"
    const val MINIMAX = "minimax"

    /** Any legal move. */
    fun random(random: Random = Random.Default) = ChessBot { engine -> legalMoves(engine).randomOrNull(random) }

    /**
     * The move that keeps the most material [depth] half-moves ahead (alpha-beta negamax), mating
     * when it can and taking the fastest mate. Ties are broken at random, so two bots of the same
     * depth do not play the same game every time.
     */
    fun minimax(
        depth: Int = 2,
        random: Random = Random.Default,
    ) = ChessBot { engine ->
        val moves = legalMoves(engine).shuffled(random)
        var best: ChessBotMove? = null
        var bestScore = Int.MIN_VALUE
        var alpha = -INFINITY
        for (move in moves) {
            if (!play(engine, move)) continue
            val score = -negamax(engine, depth - 1, -INFINITY, -alpha)
            engine.undoMove()
            if (score > bestScore) {
                bestScore = score
                best = move
            }
            if (score > alpha) alpha = score
        }
        best
    }

    /** The bot called [name] (`random`, `minimax`), or null for a name there is none of. */
    fun byName(
        name: String,
        depth: Int = 2,
        random: Random = Random.Default,
    ): ChessBot? =
        when (name.lowercase()) {
            RANDOM -> random(random)
            MINIMAX -> minimax(depth.coerceIn(1, 4), random)
            else -> null
        }
}

private const val INFINITY = 1_000_000
private const val MATE = 100_000

private fun negamax(
    engine: ChessEngine,
    depth: Int,
    alphaIn: Int,
    beta: Int,
): Int {
    val moves = legalMoves(engine)
    if (moves.isEmpty()) {
        // Mated (the sooner, the worse for the side to move) or stalemated.
        return if (engine.isInCheck()) -(MATE + depth) else 0
    }
    if (depth <= 0) return evaluate(engine)

    var alpha = alphaIn
    var best = -INFINITY
    for (move in moves) {
        if (!play(engine, move)) continue
        val score = -negamax(engine, depth - 1, -beta, -alpha)
        engine.undoMove()
        if (score > best) best = score
        if (score > alpha) alpha = score
        if (alpha >= beta) break
    }
    return best
}

/** Material in centipawns, from the side to move's point of view. */
private fun evaluate(engine: ChessEngine): Int {
    val white = ChessMaterial.of(engine.getPosition()).whiteAdvantage * 100
    return if (engine.getSideToMove() == Color.WHITE) white else -white
}

private fun play(
    engine: ChessEngine,
    move: ChessBotMove,
): Boolean {
    val promotion = if (move.to.length == 3) PieceType.QUEEN else null
    return engine.makeMove(move.from, move.to.take(2), promotion).success
}

/** Every legal move of the side to move; a pawn reaching the last rank promotes to a queen. */
internal fun legalMoves(engine: ChessEngine): List<ChessBotMove> {
    val position = engine.getPosition()
    val side = engine.getSideToMove()
    val moves = ArrayList<ChessBotMove>(40)
    for (file in 0..7) {
        for (rank in 0..7) {
            val piece = position.pieceAt(file, rank) ?: continue
            if (piece.color != side) continue
            val from = "${'a' + file}${rank + 1}"
            val lastRank = if (side == Color.WHITE) '8' else '1'
            engine.getLegalMovesFrom(from).forEach { to ->
                val promotes = piece.type == PieceType.PAWN && to[1] == lastRank
                moves.add(ChessBotMove(from, if (promotes) to + "q" else to))
            }
        }
    }
    return moves
}
