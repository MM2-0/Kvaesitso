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

/**
 * Platform-agnostic chess engine interface for move validation and generation
 * Implemented using expect/actual for JVM/Android platforms
 */
expect class ChessEngine() {
    /**
     * Get the current position as FEN string
     */
    fun getFen(): String

    /**
     * Load a position from FEN notation
     */
    fun loadFen(fen: String)

    /**
     * Reset to starting position
     */
    fun reset()

    /**
     * Make a move using Standard Algebraic Notation (SAN)
     * @param san Move in SAN format (e.g., "Nf3", "e4", "O-O")
     * @return MoveResult with success status and resulting position
     */
    fun makeMove(san: String): MoveResult

    /**
     * Make a move from source to destination square
     * @param from Source square in algebraic notation (e.g., "e2")
     * @param to Destination square in algebraic notation (e.g., "e4")
     * @param promotion Optional promotion piece type for pawn promotion
     * @return MoveResult with success status and resulting position
     */
    fun makeMove(
        from: String,
        to: String,
        promotion: PieceType? = null,
    ): MoveResult

    /**
     * Get all legal moves from the current position
     * @return List of legal moves in SAN notation
     */
    fun getLegalMoves(): List<String>

    /**
     * Get all legal moves for a specific square
     * @param square Square in algebraic notation (e.g., "e2")
     * @return List of destination squares that are legal
     */
    fun getLegalMovesFrom(square: String): List<String>

    /**
     * Check if a move is legal in the current position
     * @param san Move in SAN format
     * @return true if the move is legal
     */
    fun isLegalMove(san: String): Boolean

    /**
     * Check if a move from-to is legal
     */
    fun isLegalMove(
        from: String,
        to: String,
        promotion: PieceType? = null,
    ): Boolean

    /**
     * Check if current position is checkmate
     */
    fun isCheckmate(): Boolean

    /**
     * Check if current position is stalemate
     */
    fun isStalemate(): Boolean

    /**
     * Check if current position is in check
     */
    fun isInCheck(): Boolean

    /**
     * Undo the last move
     */
    fun undoMove()

    /**
     * Get the current position as ChessPosition
     */
    fun getPosition(): ChessPosition

    /**
     * Get side to move
     */
    fun getSideToMove(): Color

    /**
     * Get move history in SAN notation
     */
    fun getMoveHistory(): List<String>
}

/**
 * Result of attempting a chess move
 */
@Immutable
data class MoveResult(
    val success: Boolean,
    val san: String? = null, // Move in SAN notation if successful
    val position: ChessPosition? = null, // Resulting position if successful
    val error: String? = null, // Error message if unsuccessful
)
