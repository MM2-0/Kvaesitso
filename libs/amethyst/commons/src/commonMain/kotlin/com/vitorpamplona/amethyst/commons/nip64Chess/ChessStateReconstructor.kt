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
import com.vitorpamplona.quartz.nip64Chess.GameResult
import com.vitorpamplona.quartz.nip64Chess.GameTermination
import com.vitorpamplona.quartz.nip64Chess.jester.JesterGameEvents
import com.vitorpamplona.quartz.utils.Log

/**
 * Turns a Jester game's events into the state the board shows.
 *
 * Which moves count, who holds each seat and which move-less ending stands is quartz's
 * [JesterGameEvents.mainLine], so every client reads a game the same way. This adds what needs a
 * chess engine: the line is replayed and stops at the first illegal move, a mate or stalemate the
 * board shows ends a game whose final move did not say so (jesterui), and a final move that claims
 * a mate or stalemate the board does not show is not believed.
 */
object ChessStateReconstructor {
    /**
     * @param events The collected Jester events for a single game
     * @param viewerPubkey The pubkey of the user viewing the game (determines perspective)
     * @param abandonmentAfterSeconds How long a player may stay silent before their opponent's
     *   abandonment claim counts (see [CHESS_ABANDONMENT_SECONDS])
     */
    fun reconstruct(
        events: JesterGameEvents,
        viewerPubkey: String,
        abandonmentAfterSeconds: Long = CHESS_ABANDONMENT_SECONDS,
    ): ReconstructionResult {
        val startEvent = events.startEvent ?: return ReconstructionResult.Error("No start event found")
        val line = events.mainLine(abandonmentAfterSeconds) ?: return ReconstructionResult.Error("No start event found")
        val whitePubkey = line.white
        val blackPubkey = line.black

        val viewerRole =
            when (viewerPubkey) {
                whitePubkey -> ViewerRole.WHITE_PLAYER
                blackPubkey -> ViewerRole.BLACK_PLAYER
                else -> ViewerRole.SPECTATOR
            }
        val playerColor = if (viewerRole == ViewerRole.BLACK_PLAYER) Color.BLACK else Color.WHITE
        val opponentPubkey =
            when (viewerRole) {
                ViewerRole.WHITE_PLAYER -> blackPubkey ?: ""
                else -> whitePubkey ?: ""
            }

        // Replay the line; an illegal move ends it where it stood.
        val engine = ChessEngine()
        val appliedMoveNumbers = mutableSetOf<Int>()
        var isDesynced = false
        for ((index, san) in line.history.withIndex()) {
            if (engine.makeMove(san).success) {
                appliedMoveNumbers.add(index + 1)
            } else {
                isDesynced = true
                Log.d("chessdebug") { "[Reconstructor] move #${index + 1} '$san' does not replay; the game stops before it" }
                break
            }
        }
        val playedMoves = line.moves.take(appliedMoveNumbers.size)
        val lastPlayed = playedMoves.lastOrNull()

        val ending = line.ending?.takeIf { end -> !isDesynced && (end.move() == null || end == lastPlayed) }
        val claimedTermination = ending?.termination()?.let { tag -> GameTermination.entries.firstOrNull { it.name.equals(tag, ignoreCase = true) } }
        // A final move may claim a mate or stalemate; only the board can confirm one.
        val believedEnding =
            ending?.takeIf {
                when (claimedTermination) {
                    GameTermination.CHECKMATE -> engine.isCheckmate()
                    GameTermination.STALEMATE -> engine.isStalemate()
                    else -> true
                }
            }

        val gameStatus =
            when {
                believedEnding?.result() != null -> GameStatus.Finished(parseGameResult(believedEnding.result()))
                engine.isCheckmate() -> GameStatus.Finished(if (engine.getSideToMove() == Color.WHITE) GameResult.BLACK_WINS else GameResult.WHITE_WINS)
                engine.isStalemate() -> GameStatus.Finished(GameResult.DRAW)
                else -> GameStatus.InProgress
            }

        // How it ended: what the ending says, or, when it says nothing (jesterui), the board.
        val termination =
            when {
                gameStatus !is GameStatus.Finished -> null
                believedEnding != null && claimedTermination != null -> claimedTermination
                engine.isCheckmate() -> GameTermination.CHECKMATE
                engine.isStalemate() -> GameTermination.STALEMATE
                else -> null
            }

        val state =
            ReconstructedGameState(
                startEventId = startEvent.id,
                headEventId = lastPlayed?.id ?: startEvent.id,
                whitePubkey = whitePubkey,
                blackPubkey = blackPubkey,
                viewerRole = viewerRole,
                playerColor = playerColor,
                opponentPubkey = opponentPubkey,
                isPendingChallenge = viewerPubkey == startEvent.pubKey && playedMoves.isEmpty(),
                currentPosition = engine.getPosition(),
                moveHistory = engine.getMoveHistory(),
                gameStatus = gameStatus,
                isDesynced = isDesynced,
                pendingDrawOffer = if (gameStatus is GameStatus.Finished || isDesynced) null else line.drawOfferedBy?.let { if (it == Color.WHITE) whitePubkey else blackPubkey },
                appliedMoveNumbers = appliedMoveNumbers,
                challengeCreatedAt = startEvent.createdAt,
                timeControl = null,
                lastMoveAt = lastPlayed?.createdAt,
                termination = termination,
            )

        return ReconstructionResult.Success(state, engine)
    }

    private fun parseGameResult(result: String?): GameResult =
        when (result) {
            "1-0" -> GameResult.WHITE_WINS
            "0-1" -> GameResult.BLACK_WINS
            "1/2-1/2" -> GameResult.DRAW
            else -> GameResult.IN_PROGRESS
        }
}

/**
 * The viewer's role in the game.
 */
enum class ViewerRole {
    WHITE_PLAYER,
    BLACK_PLAYER,
    SPECTATOR,
}

/**
 * Result of game state reconstruction.
 */
sealed class ReconstructionResult {
    data class Success(
        val state: ReconstructedGameState,
        val engine: ChessEngine,
    ) : ReconstructionResult()

    data class Error(
        val message: String,
    ) : ReconstructionResult()

    fun isSuccess(): Boolean = this is Success

    fun getOrNull(): ReconstructedGameState? = (this as? Success)?.state

    fun getEngineOrNull(): ChessEngine? = (this as? Success)?.engine
}

/**
 * Reconstructed game state from Jester events.
 * This is an immutable snapshot of the game at the time of reconstruction.
 */
@Immutable
data class ReconstructedGameState(
    /** The start event ID - this is the game identifier in Jester protocol */
    val startEventId: String,
    /** The current head event ID - used for linking the next move */
    val headEventId: String,
    val whitePubkey: String?,
    val blackPubkey: String?,
    val viewerRole: ViewerRole,
    val playerColor: Color,
    val opponentPubkey: String,
    val isPendingChallenge: Boolean,
    val currentPosition: ChessPosition,
    val moveHistory: List<String>,
    val gameStatus: GameStatus,
    val isDesynced: Boolean,
    /** The player whose draw offer stands, until it is accepted or answered with a move. */
    val pendingDrawOffer: String?,
    val appliedMoveNumbers: Set<Int>,
    val challengeCreatedAt: Long,
    val timeControl: String?,
    /** When the newest move was published; null before the first one. */
    val lastMoveAt: Long? = null,
    /** How a finished game ended, when the final move says so or the board shows it; null otherwise. */
    val termination: GameTermination? = null,
) {
    /** Legacy alias for startEventId */
    @Deprecated("Use startEventId instead", ReplaceWith("startEventId"))
    val gameId: String get() = startEventId

    fun isPlayerTurn(): Boolean =
        when (viewerRole) {
            ViewerRole.SPECTATOR -> false
            ViewerRole.WHITE_PLAYER -> currentPosition.activeColor == Color.WHITE
            ViewerRole.BLACK_PLAYER -> currentPosition.activeColor == Color.BLACK
        }

    fun isFinished(): Boolean = gameStatus is GameStatus.Finished

    fun hasOpponentDrawOffer(playerPubkey: String): Boolean = pendingDrawOffer != null && pendingDrawOffer != playerPubkey

    fun hasOurDrawOffer(playerPubkey: String): Boolean = pendingDrawOffer == playerPubkey
}

/** Whether a game is still being played. */
sealed class GameStatus {
    data object InProgress : GameStatus()

    data class Finished(
        val result: GameResult,
    ) : GameStatus()
}
