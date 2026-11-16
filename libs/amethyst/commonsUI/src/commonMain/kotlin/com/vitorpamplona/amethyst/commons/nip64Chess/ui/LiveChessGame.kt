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
package com.vitorpamplona.amethyst.commons.nip64Chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessGameSnapshot
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessMaterial
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.chess_accept_draw
import com.vitorpamplona.amethyst.commons.resources.chess_back_to_game
import com.vitorpamplona.amethyst.commons.resources.chess_claim_win
import com.vitorpamplona.amethyst.commons.resources.chess_color_black
import com.vitorpamplona.amethyst.commons.resources.chess_color_white
import com.vitorpamplona.amethyst.commons.resources.chess_create_game
import com.vitorpamplona.amethyst.commons.resources.chess_draw_offer_armed
import com.vitorpamplona.amethyst.commons.resources.chess_draw_offer_sent
import com.vitorpamplona.amethyst.commons.resources.chess_draw_offered_to_you
import com.vitorpamplona.amethyst.commons.resources.chess_opponent_hint
import com.vitorpamplona.amethyst.commons.resources.chess_opponent_inactive_days
import com.vitorpamplona.amethyst.commons.resources.chess_opponent_invalid
import com.vitorpamplona.amethyst.commons.resources.chess_opponent_label
import com.vitorpamplona.amethyst.commons.resources.chess_play_as
import com.vitorpamplona.amethyst.commons.resources.chess_random_color
import com.vitorpamplona.amethyst.commons.resources.chess_resign
import com.vitorpamplona.amethyst.commons.resources.chess_resign_confirm_text
import com.vitorpamplona.amethyst.commons.resources.chess_resign_confirm_title
import com.vitorpamplona.amethyst.commons.resources.new_chess_game
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * The new-game dialog: pick a side (or let chance pick it), and optionally the one person who may
 * take the other seat. With no opponent the game is an open seat anyone can take.
 *
 * @param onCreateGame gets the opponent's hex key (null for an open seat) and the creator's colour.
 */
@Composable
fun NewChessGameDialog(
    onDismiss: () -> Unit,
    onCreateGame: (opponentPubkey: String?, playerColor: Color) -> Unit,
) {
    var opponentPubkey by remember { mutableStateOf("") }
    // null is "random", resolved when the game is created.
    var selectedColor by remember { mutableStateOf<Color?>(Color.WHITE) }
    // npub, nprofile or hex; blank is an open challenge.
    val opponentHex = remember(opponentPubkey) { opponentPubkey.trim().ifBlank { null }?.let { decodePublicKeyAsHexOrNull(it) } }
    val opponentInvalid = opponentPubkey.isNotBlank() && opponentHex == null

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringRes(Res.string.new_chess_game),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = stringRes(Res.string.chess_play_as),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SideTile(Color.WHITE, Res.string.chess_color_white, selectedColor == Color.WHITE) { selectedColor = Color.WHITE }
                    SideTile(null, Res.string.chess_random_color, selectedColor == null) { selectedColor = null }
                    SideTile(Color.BLACK, Res.string.chess_color_black, selectedColor == Color.BLACK) { selectedColor = Color.BLACK }
                }

                OutlinedTextField(
                    value = opponentPubkey,
                    onValueChange = { opponentPubkey = it },
                    label = { Text(stringRes(Res.string.chess_opponent_label)) },
                    placeholder = { Text("npub1\u2026") },
                    supportingText = {
                        Text(stringRes(if (opponentInvalid) Res.string.chess_opponent_invalid else Res.string.chess_opponent_hint))
                    },
                    isError = opponentInvalid,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringRes(Res.string.cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        enabled = !opponentInvalid,
                        onClick = { onCreateGame(opponentHex, selectedColor ?: listOf(Color.WHITE, Color.BLACK).random()) },
                    ) {
                        Text(stringRes(Res.string.chess_create_game))
                    }
                }
            }
        }
    }
}

/** One choice of side: its badge (both, staggered, for random) over its name, outlined when chosen. */
@Composable
private fun RowScope.SideTile(
    color: Color?,
    label: StringResource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier =
            Modifier
                .weight(1f)
                .clip(shape)
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else ComposeColor.Transparent, shape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (color != null) {
            ChessSideBadge(color, 44.dp)
        } else {
            Box(Modifier.size(44.dp)) {
                ChessSideBadge(Color.WHITE, 26.dp, Modifier.align(Alignment.TopStart))
                ChessSideBadge(Color.BLACK, 26.dp, Modifier.align(Alignment.BottomEnd))
            }
        }
        Text(
            text = stringRes(label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The board screen's body: the opponent above the board, the user below it, then the result once
 * there is one, the moves, and the controls. On a wide window the moves and controls sit beside
 * the board instead.
 *
 * A player always sees the board from their own side, with their opponent at the top; only a
 * spectator can flip it. Everything comes from [game], a snapshot rebuilt from the game's events;
 * the only state kept here is how a spectator turned the board and which past move is shown.
 * [onMoveMade] must publish the move; the board redraws when the next snapshot arrives with it.
 * [player] draws one player's avatar and name, or the open seat when its pubkey is null.
 * Without [canPlay] (a read-only login) the board only shows the game.
 */
@Composable
fun LiveChessGameScreen(
    game: ChessGameSnapshot,
    onMoveMade: (from: String, to: String, offerDraw: Boolean) -> Unit,
    onResign: () -> Unit,
    player: @Composable RowScope.(pubkey: String?) -> Unit,
    modifier: Modifier = Modifier,
    canPlay: Boolean = true,
    onAcceptDraw: () -> Unit = {},
    onClaimAbandonment: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    var flippedByUser by rememberSaveable(game.gameId) { mutableStateOf(false) }
    var viewPly by remember(game.gameId) { mutableStateOf<Int?>(null) }
    var confirmResign by remember { mutableStateOf(false) }
    // Armed until the next move, which carries the offer.
    var offerDraw by rememberSaveable(game.gameId) { mutableStateOf(false) }

    // Celebrate an ending seen live, not one the screen opened on.
    val finishedWhenOpened = remember(game.gameId) { game.isFinished }
    var gameEndDismissed by remember(game.gameId) { mutableStateOf(false) }

    val replay = game.replay
    val lastPly = replay.last.ply
    val shownPly = viewPly?.coerceAtMost(lastPly) ?: lastPly
    val isLive = shownPly == lastPly
    val shown = replay.at(shownPly)
    val material = remember(shown) { ChessMaterial.of(shown.position) }
    val myColor = game.myColor

    // The user's side: the seat they hold, or the one they can take by moving. A read-only login takes none.
    val mySide = myColor?.takeIf { canPlay || !game.isJoinable }
    val flipped = if (mySide != null) mySide == Color.BLACK else flippedByUser
    val bottomColor = if (flipped) Color.BLACK else Color.WHITE
    val canResign = canPlay && myColor != null && !game.isOpenSeat && !game.isFinished
    val selectPly: (Int) -> Unit = { ply -> viewPly = ply.coerceIn(0, lastPly).takeIf { it != lastPly } }

    val strip: @Composable (Color, Modifier) -> Unit = { color, stripModifier ->
        ChessPlayerStrip(
            color = color,
            isToMove = !game.isFinished && isLive && shown.position.activeColor == color,
            captured = material.capturedBy(color),
            advantage = material.advantageOf(color),
            modifier = stripModifier,
        ) {
            player(if (color == Color.WHITE) game.whitePubkey else game.blackPubkey)
        }
    }

    val board: @Composable (Dp) -> Unit = { size ->
        InteractiveChessBoard(
            engine = game.engine,
            boardSize = size,
            flipped = flipped,
            playerColor = myColor,
            isSpectator = !canPlay || !game.isMyTurn || !isLive,
            positionVersion = game.moveCount,
            position = if (isLive) null else shown.position,
            lastMove = shown.lastMove,
            checkSquare = shown.checkSquare,
            onMoveMade = { from, to, _ ->
                onMoveMade(from, to, offerDraw)
                offerDraw = false
            },
        )
    }

    val toolbar: @Composable () -> Unit = {
        ChessGameToolbar(
            shownPly = shownPly,
            lastPly = lastPly,
            onSelectPly = selectPly,
            onFlip = if (mySide == null) ({ flippedByUser = !flippedByUser }) else null,
            onResign = if (canResign) ({ confirmResign = true }) else null,
            drawOfferArmed = offerDraw,
            // An offer rides on a move, so it is open while the user still has one to make and
            // none stands from either side.
            onToggleDrawOffer = if (canPlay && game.isSeatedInLiveGame && game.drawOfferedBy == null) ({ offerDraw = it }) else null,
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp && maxWidth > maxHeight
        if (wide) {
            val boardSize = min(maxHeight - 36.dp * 2 - 32.dp, maxWidth - 400.dp).coerceIn(200.dp, 760.dp)
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            ) {
                Column(Modifier.width(boardSize)) {
                    strip(bottomColor.opposite(), Modifier)
                    board(boardSize)
                    strip(bottomColor, Modifier)
                }
                Column(
                    modifier = Modifier.width(320.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GameStatusLine(game, shownPly, lastPly, canPlay, offerDraw, onBackToGame = { viewPly = null }, onAcceptDraw = onAcceptDraw, onClaimAbandonment = onClaimAbandonment)
                    ChessMoveTable(game.state.moveHistory, shownPly, selectPly, Modifier.weight(1f))
                    toolbar()
                }
            }
        } else {
            // Edge to edge on a phone: every pixel of width is board.
            val boardSize = min(maxWidth, maxHeight - 240.dp).coerceAtLeast(200.dp)
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                strip(bottomColor.opposite(), Modifier.width(boardSize).padding(horizontal = 12.dp))
                board(boardSize)
                strip(bottomColor, Modifier.width(boardSize).padding(horizontal = 12.dp))
                Column(Modifier.width(boardSize).padding(horizontal = 12.dp)) {
                    GameStatusLine(game, shownPly, lastPly, canPlay, offerDraw, onBackToGame = { viewPly = null }, onAcceptDraw = onAcceptDraw, onClaimAbandonment = onClaimAbandonment)
                    ChessMoveStrip(game.state.moveHistory, shownPly, selectPly)
                    toolbar()
                }
            }
        }

        val outcome = game.outcome
        if (outcome != null && !finishedWhenOpened && !gameEndDismissed) {
            ChessGameEndCard(
                outcome = outcome,
                myColor = game.myColor.takeIf { !game.isOpenSeat },
                // Keep the mated king in sight: over the board's upper half when it stands in the lower one.
                atTop = replay.last.checkSquare?.let { (it[1] - '0' <= 4) != flipped } ?: false,
                player = { color -> player(if (color == Color.WHITE) game.whitePubkey else game.blackPubkey) },
                onReview = { gameEndDismissed = true },
                onExit = onExit,
            )
        }
    }

    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            title = { Text(stringRes(Res.string.chess_resign_confirm_title)) },
            text = { Text(stringRes(Res.string.chess_resign_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmResign = false
                    onResign()
                }) { Text(stringRes(Res.string.chess_resign)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmResign = false }) { Text(stringRes(Res.string.cancel)) }
            },
        )
    }
}

/**
 * The one line the board and the dots cannot say: how the game ended; a draw offer to accept, or
 * the user's own one; a silent opponent's game to claim; or, while an earlier move is shown, the
 * way back to the game. Nothing otherwise.
 */
@Composable
private fun GameStatusLine(
    game: ChessGameSnapshot,
    shownPly: Int,
    lastPly: Int,
    canPlay: Boolean,
    drawOfferArmed: Boolean,
    onBackToGame: () -> Unit,
    onAcceptDraw: () -> Unit,
    onClaimAbandonment: () -> Unit,
) {
    if (shownPly != lastPly) {
        TextButton(onClick = onBackToGame) { Text(stringRes(Res.string.chess_back_to_game)) }
        return
    }

    game.outcome?.let {
        ChessResultBanner(it)
        return
    }

    val claimable by rememberClaimable(game)
    when {
        canPlay && game.canAcceptDraw ->
            StatusAction(stringRes(Res.string.chess_draw_offered_to_you), stringRes(Res.string.chess_accept_draw), onAcceptDraw)
        canPlay && claimable -> {
            val days = ((TimeUtils.now() - (game.state.lastMoveAt ?: TimeUtils.now())) / 86_400L).toInt()
            StatusAction(pluralStringRes(Res.plurals.chess_opponent_inactive_days, days, days), stringRes(Res.string.chess_claim_win), onClaimAbandonment)
        }
        drawOfferArmed -> StatusNote(stringRes(Res.string.chess_draw_offer_armed))
        game.drawOfferedBy != null && game.drawOfferedBy == game.myColor -> StatusNote(stringRes(Res.string.chess_draw_offer_sent))
    }
}

/** True once the viewer may claim the game from a silent opponent; flips by itself when that time comes. */
@Composable
private fun rememberClaimable(game: ChessGameSnapshot): State<Boolean> {
    val claimableAt = game.abandonmentClaimableAt
    return produceState(claimableAt != null && TimeUtils.now() >= claimableAt, claimableAt) {
        if (claimableAt == null) {
            value = false
            return@produceState
        }
        val wait = claimableAt - TimeUtils.now()
        if (wait > 0) delay(wait * 1000)
        value = true
    }
}

@Composable
private fun StatusAction(
    text: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun StatusNote(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
