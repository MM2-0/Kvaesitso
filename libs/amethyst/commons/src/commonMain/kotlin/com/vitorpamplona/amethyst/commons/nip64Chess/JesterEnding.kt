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
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.GameResult
import com.vitorpamplona.quartz.nip64Chess.GameTermination
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent

/**
 * How one Jester event ended its game: the winner (null for a draw), how, and the colour its
 * author played. Read from the event alone, so a feed can show it without loading the game.
 */
@Immutable
class JesterEnding(
    val winner: Color?,
    val termination: GameTermination?,
    val authorColor: Color,
)

/**
 * The ending this event carries: a `result` (ours and nostr.blue's), or a mating move with none
 * (jesterui's only ending). Null for a start, a move that leaves the game going, or a result it
 * cannot read.
 */
fun JesterEvent.ending(): JesterEnding? {
    if (!isMoveEvent()) return null
    val move = move()
    // A move is the last entry of the history, played by the side that was to move before it; an
    // end without a move (a resignation) comes from the side to move.
    val sideToMove = if (history().size % 2 == 0) Color.WHITE else Color.BLACK
    val authorColor = if (move != null) sideToMove.opposite() else sideToMove
    val termination = termination()?.let { tag -> GameTermination.entries.firstOrNull { it.name.equals(tag, ignoreCase = true) } }

    val result = result()
    if (result == null) {
        return if (move?.endsWith('#') == true) JesterEnding(authorColor, termination ?: GameTermination.CHECKMATE, authorColor) else null
    }
    val winner =
        when (GameResult.parse(result)) {
            GameResult.WHITE_WINS -> Color.WHITE
            GameResult.BLACK_WINS -> Color.BLACK
            GameResult.DRAW -> null
            GameResult.IN_PROGRESS -> return null
        }
    return JesterEnding(winner, termination, authorColor)
}
