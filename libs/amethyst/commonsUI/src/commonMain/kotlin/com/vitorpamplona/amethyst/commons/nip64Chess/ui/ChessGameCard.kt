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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessGameSnapshot
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessOutcome
import com.vitorpamplona.amethyst.commons.nip64Chess.jesterGameEvents
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chess_claim_win
import com.vitorpamplona.amethyst.commons.resources.chess_draw_offered
import com.vitorpamplona.amethyst.commons.resources.chess_join_as_black
import com.vitorpamplona.amethyst.commons.resources.chess_join_as_white
import com.vitorpamplona.amethyst.commons.resources.chess_open_seat
import com.vitorpamplona.amethyst.commons.resources.chess_your_turn
import com.vitorpamplona.amethyst.commons.resources.route_chess
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.types.KindCard
import com.vitorpamplona.amethyst.commons.ui.note.types.KindTone
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The game a Jester start note opened, as LocalCache has it now: rebuilt off the main thread
 * each time a move is linked to the note. Null until the start event is loaded.
 */
@Composable
fun observeChessGame(
    startNote: Note,
    viewer: HexKey,
): State<ChessGameSnapshot?> {
    val replies by startNote
        .flow()
        .replies.stateFlow
        .collectAsStateWithLifecycle()
    val metadata by startNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    return produceState<ChessGameSnapshot?>(null, startNote, replies, metadata) {
        value = withContext(Dispatchers.Default) { startNote.jesterGameEvents()?.let { ChessGameSnapshot.build(it, viewer) } }
    }
}

/**
 * A chess game in a feed: the position with its last move, who plays which colour, and whose turn
 * it is or how it ended. Opening it (the note's own click, or Join on a seat the user can take)
 * goes to the board.
 */
@Composable
fun ChessGameCard(
    startNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val game by observeChessGame(startNote, accountViewModel.account.userProfile().pubkeyHex)
    val snapshot = game ?: return

    val status = chessStatus(snapshot)

    val outcome = snapshot.outcome
    val toMove =
        snapshot.state.currentPosition.activeColor
            .takeIf { outcome == null && !snapshot.isOpenSeat }

    KindCard(
        icon = MaterialSymbols.ChessKnight,
        title = stringRes(Res.string.route_chess),
        subtitle = outcome?.let { terminationText(it.termination) },
        status = status?.first,
        statusTone = status?.second,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChessBoard(
                position = snapshot.state.currentPosition,
                boardSize = 128.dp,
                showCoordinates = false,
                flipped = snapshot.myColor == Color.BLACK,
                lastMove = snapshot.replay.last.lastMove,
                checkSquare = snapshot.replay.last.checkSquare,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardPlayer(Color.WHITE, snapshot.whitePubkey, outcome?.scoreOf(Color.WHITE), toMove == Color.WHITE, accountViewModel, nav)
                CardPlayer(Color.BLACK, snapshot.blackPubkey, outcome?.scoreOf(Color.BLACK), toMove == Color.BLACK, accountViewModel, nav)
                if (snapshot.isJoinable && accountViewModel.account.isWriteable()) {
                    Button(onClick = { nav.nav(Route.ChessGame(snapshot.gameId)) }) {
                        Text(stringRes(if (snapshot.myColor == Color.WHITE) Res.string.chess_join_as_white else Res.string.chess_join_as_black))
                    }
                }
            }
        }
    }
}

/** One side of the game: its score once the game is over, its badge, the player (or the open seat), and a dot on its turn. */
@Composable
internal fun CardPlayer(
    color: Color,
    pubkey: String?,
    score: String?,
    isToMove: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        score?.let {
            Text(
                text = it,
                modifier = Modifier.width(14.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (it == "1") FontWeight.Bold else FontWeight.Normal,
                color = if (it == "1") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ChessSideBadge(color, 20.dp)
        if (pubkey == null) {
            Text(
                text = stringRes(Res.string.chess_open_seat),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LoadUser(pubkey) { user ->
                if (user != null) {
                    ClickableUserPicture(baseUser = user, size = 20.dp, accountViewModel = accountViewModel, onClick = { nav.nav(Route.Profile(pubkey)) })
                    UsernameDisplay(baseUser = user, accountViewModel = accountViewModel)
                }
            }
        }
        if (isToMove) Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
    }
}

/** The chip a card needs: the result from the user's side, a draw offer to answer, a game to claim, or that it is their move. The board and the seats say the rest. */
@Composable
private fun chessStatus(game: ChessGameSnapshot): Pair<String, KindTone>? {
    val outcome = game.outcome
    return when {
        outcome != null -> outcomeChip(outcome)
        game.canAcceptDraw -> stringRes(Res.string.chess_draw_offered) to KindTone.Primary
        game.canClaimAbandonment(TimeUtils.now()) -> stringRes(Res.string.chess_claim_win) to KindTone.Warning
        game.isMyTurn && !game.isJoinable -> stringRes(Res.string.chess_your_turn) to KindTone.Primary
        else -> null
    }
}

/** A result as a card's chip: the user's win green, their loss red, anything else neutral. */
@Composable
internal fun outcomeChip(outcome: ChessOutcome): Pair<String, KindTone> =
    outcomeTitle(outcome) to
        when (outcome.perspective) {
            ChessOutcome.Perspective.WON -> KindTone.Positive
            ChessOutcome.Perspective.LOST -> KindTone.Negative
            else -> KindTone.Neutral
        }
