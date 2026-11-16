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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip64Chess.ChessPosition
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.PieceType

/** A square as algebraic notation, `e4`. */
typealias ChessSquare = String

/** The two squares a move touched, for highlighting: where the piece left, and where it landed. */
@Immutable
data class ChessMoveSquares(
    val from: ChessSquare,
    val to: ChessSquare,
)

/** One position of a game: the board after [ply] half-moves, what was just played to reach it, and whose king is in check. */
@Immutable
class ChessPly(
    val ply: Int,
    val position: ChessPosition,
    val lastMove: ChessMoveSquares?,
    val checkSquare: ChessSquare?,
)

/**
 * Every position of a game, from the start to [history]'s last move, so the board can step back
 * through it. A move that does not replay ends the list where it stopped.
 */
@Immutable
class ChessReplay(
    val plies: List<ChessPly>,
) {
    val last: ChessPly get() = plies.last()

    fun at(ply: Int): ChessPly = plies[ply.coerceIn(0, plies.lastIndex)]

    companion object {
        fun of(history: List<String>): ChessReplay {
            val engine = ChessEngine()
            engine.reset()
            val plies = ArrayList<ChessPly>(history.size + 1)
            plies.add(ChessPly(0, engine.getPosition(), null, null))
            for (san in history) {
                val before = engine.getPosition()
                if (!engine.makeMove(san).success) break
                val after = engine.getPosition()
                plies.add(ChessPly(plies.size, after, moveSquares(before, after), if (engine.isInCheck()) kingSquare(after, after.activeColor) else null))
            }
            return ChessReplay(plies)
        }
    }
}

private val FILES = "abcdefgh"

private fun square(
    file: Int,
    rank: Int,
): ChessSquare = "${FILES[file]}${rank + 1}"

private fun kingSquare(
    position: ChessPosition,
    color: Color,
): ChessSquare? {
    for (file in 0..7) {
        for (rank in 0..7) {
            val piece = position.pieceAt(file, rank)
            if (piece != null && piece.type == PieceType.KING && piece.color == color) return square(file, rank)
        }
    }
    return null
}

/**
 * The squares a move touched, read off the boards before and after it (for any game the app
 * draws, live or a NIP-64 record): the mover's piece left one
 * square and arrived on another. Castling moves two pieces; the king's squares are the move.
 */
fun moveSquares(
    before: ChessPosition,
    after: ChessPosition,
): ChessMoveSquares? {
    val mover = before.activeColor
    val left = mutableListOf<Pair<ChessSquare, PieceType>>()
    val arrived = mutableListOf<Pair<ChessSquare, PieceType>>()
    for (file in 0..7) {
        for (rank in 0..7) {
            val was = before.pieceAt(file, rank)
            val now = after.pieceAt(file, rank)
            if (was == now) continue
            if (was != null && was.color == mover) left.add(square(file, rank) to was.type)
            if (now != null && now.color == mover) arrived.add(square(file, rank) to now.type)
        }
    }
    val from = left.firstOrNull { it.second == PieceType.KING } ?: left.singleOrNull() ?: return null
    val to = arrived.firstOrNull { it.second == from.second } ?: arrived.singleOrNull() ?: return null
    return ChessMoveSquares(from.first, to.first)
}

private val START_COUNT = mapOf(PieceType.PAWN to 8, PieceType.KNIGHT to 2, PieceType.BISHOP to 2, PieceType.ROOK to 2, PieceType.QUEEN to 1)

private val VALUE = mapOf(PieceType.PAWN to 1, PieceType.KNIGHT to 3, PieceType.BISHOP to 3, PieceType.ROOK to 5, PieceType.QUEEN to 9)

/** What each side has taken from the other, and who is ahead on material. */
@Immutable
class ChessMaterial(
    /** Black's pieces that White captured, cheapest first. */
    val capturedByWhite: List<PieceType>,
    /** White's pieces that Black captured, cheapest first. */
    val capturedByBlack: List<PieceType>,
    /** Material balance in pawns: positive when White is ahead. */
    val whiteAdvantage: Int,
) {
    fun capturedBy(color: Color) = if (color == Color.WHITE) capturedByWhite else capturedByBlack

    /** How far [color] is ahead, or 0 when it is not. */
    fun advantageOf(color: Color) = (if (color == Color.WHITE) whiteAdvantage else -whiteAdvantage).coerceAtLeast(0)

    companion object {
        /** Counts what is missing from the starting set. A promoted pawn counts as the piece it became. */
        fun of(position: ChessPosition): ChessMaterial {
            val counts = mutableMapOf<Pair<Color, PieceType>, Int>()
            for (file in 0..7) {
                for (rank in 0..7) {
                    val piece = position.pieceAt(file, rank) ?: continue
                    counts[piece.color to piece.type] = (counts[piece.color to piece.type] ?: 0) + 1
                }
            }

            fun missing(color: Color) =
                START_COUNT.flatMap { (type, start) ->
                    List((start - (counts[color to type] ?: 0)).coerceAtLeast(0)) { type }
                }

            fun total(color: Color) = counts.entries.sumOf { (key, n) -> if (key.first == color) (VALUE[key.second] ?: 0) * n else 0 }

            return ChessMaterial(
                capturedByWhite = missing(Color.BLACK),
                capturedByBlack = missing(Color.WHITE),
                whiteAdvantage = total(Color.WHITE) - total(Color.BLACK),
            )
        }
    }
}
