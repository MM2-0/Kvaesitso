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

import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.GameResult
import com.vitorpamplona.quartz.nip64Chess.GameTermination
import com.vitorpamplona.quartz.nip64Chess.PieceType
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * How long the player to move may stay silent before their opponent can claim the game: three
 * days, a common default for correspondence chess. Amethyst's policy; quartz takes it as a
 * parameter, so a game reads the same everywhere only when clients agree on it.
 */
const val CHESS_ABANDONMENT_SECONDS = 3 * 24 * 60 * 60L

/**
 * The event that plays [from]-[to] in this game for the viewer: a Jester move that follows the
 * current head, carries the whole history, tags the opponent, and ends the game when it mates or
 * stalemates. [to] may carry a promotion letter (`e8q`). Null when it is not the viewer's turn or
 * not a legal move. With [offerDraw] the move also offers a draw, unless it ends the game. Shared
 * by the board and `amy chess`, so both publish the same thing.
 */
fun ChessGameSnapshot.moveTemplate(
    from: String,
    to: String,
    offerDraw: Boolean = false,
): EventTemplate<JesterEvent>? {
    val myColor = myColor ?: return null
    if (!isMyTurn) return null

    val (square, promotion) = parsePromotion(to)
    val engine = ChessEngine()
    engine.loadFen(this.engine.getFen())
    val result = engine.makeMove(from, square, promotion)
    val san = result.san ?: return null
    if (!result.success) return null

    val history = state.moveHistory + san
    // Nobody to tag yet when the challenger opens an untaken game; the builder skips a blank key.
    val opponent = opponentPubkey.orEmpty()
    val head = state.headEventId
    return when {
        engine.isCheckmate() ->
            JesterEvent.buildEndMove(gameId, head, san, engine.getFen(), history, opponent, winnerResult(myColor), GameTermination.CHECKMATE)
        engine.isStalemate() ->
            JesterEvent.buildEndMove(gameId, head, san, engine.getFen(), history, opponent, GameResult.DRAW, GameTermination.STALEMATE)
        else ->
            JesterEvent.buildMove(gameId, head, san, engine.getFen(), history, opponent, drawOffered = offerDraw && opponent.isNotEmpty())
    }
}

/** The event that resigns this game for the viewer. Null when they hold no seat or it is over. */
fun ChessGameSnapshot.resignTemplate(): EventTemplate<JesterEvent>? {
    val myColor = myColor ?: return null
    if (isFinished || isOpenSeat) return null
    return JesterEvent.buildEndMove(
        startEventId = gameId,
        headEventId = state.headEventId,
        move = null,
        fen = engine.getFen(),
        history = state.moveHistory,
        opponentPubkey = opponentPubkey.orEmpty(),
        result = winnerResult(myColor.opposite()),
        termination = GameTermination.RESIGNATION,
    )
}

/** The event that accepts the opponent's standing draw offer. Null when there is none to accept. */
fun ChessGameSnapshot.acceptDrawTemplate(): EventTemplate<JesterEvent>? {
    if (!canAcceptDraw) return null
    return JesterEvent.buildEndMove(
        startEventId = gameId,
        headEventId = state.headEventId,
        move = null,
        fen = engine.getFen(),
        history = state.moveHistory,
        opponentPubkey = opponentPubkey.orEmpty(),
        result = GameResult.DRAW,
        termination = GameTermination.DRAW_AGREEMENT,
    )
}

/**
 * The event that claims the game because the opponent, to move, has been silent for
 * [CHESS_ABANDONMENT_SECONDS]. Null until [ChessGameSnapshot.canClaimAbandonment] at [now].
 */
fun ChessGameSnapshot.claimAbandonmentTemplate(now: Long = TimeUtils.now()): EventTemplate<JesterEvent>? {
    val myColor = myColor ?: return null
    if (!canClaimAbandonment(now)) return null
    return JesterEvent.buildEndMove(
        startEventId = gameId,
        headEventId = state.headEventId,
        move = null,
        fen = engine.getFen(),
        history = state.moveHistory,
        opponentPubkey = opponentPubkey.orEmpty(),
        result = winnerResult(myColor),
        termination = GameTermination.ABANDONMENT,
        createdAt = now,
    )
}

private fun winnerResult(winner: Color) = if (winner == Color.WHITE) GameResult.WHITE_WINS else GameResult.BLACK_WINS

/** Splits a promotion suffix off a target square: `e8q` is `e8` promoting to a queen. */
internal fun parsePromotion(to: String): Pair<String, PieceType?> {
    if (to.length != 3) return to to null
    val promotion =
        when (to.last().lowercaseChar()) {
            'q' -> PieceType.QUEEN
            'r' -> PieceType.ROOK
            'b' -> PieceType.BISHOP
            'n' -> PieceType.KNIGHT
            else -> null
        }
    return if (promotion != null) to.take(2) to promotion else to to null
}
