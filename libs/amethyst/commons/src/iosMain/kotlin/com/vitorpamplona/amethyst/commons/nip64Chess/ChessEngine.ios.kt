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

import com.vitorpamplona.quartz.nip64Chess.ChessPosition
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.PieceType

actual class ChessEngine actual constructor() {
    actual fun getFen(): String {
        TODO("Not yet implemented")
    }

    actual fun loadFen(fen: String) {
    }

    actual fun reset() {
    }

    actual fun makeMove(san: String): MoveResult {
        TODO("Not yet implemented")
    }

    actual fun makeMove(
        from: String,
        to: String,
        promotion: PieceType?,
    ): MoveResult {
        TODO("Not yet implemented")
    }

    actual fun getLegalMoves(): List<String> {
        TODO("Not yet implemented")
    }

    actual fun getLegalMovesFrom(square: String): List<String> {
        TODO("Not yet implemented")
    }

    actual fun isLegalMove(san: String): Boolean {
        TODO("Not yet implemented")
    }

    actual fun isLegalMove(
        from: String,
        to: String,
        promotion: PieceType?,
    ): Boolean {
        TODO("Not yet implemented")
    }

    actual fun isCheckmate(): Boolean {
        TODO("Not yet implemented")
    }

    actual fun isStalemate(): Boolean {
        TODO("Not yet implemented")
    }

    actual fun isInCheck(): Boolean {
        TODO("Not yet implemented")
    }

    actual fun undoMove() {
    }

    actual fun getPosition(): ChessPosition {
        TODO("Not yet implemented")
    }

    actual fun getSideToMove(): Color {
        TODO("Not yet implemented")
    }

    actual fun getMoveHistory(): List<String> {
        TODO("Not yet implemented")
    }
}
