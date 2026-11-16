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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessReplay
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chess_color_black
import com.vitorpamplona.amethyst.commons.resources.chess_color_white
import com.vitorpamplona.amethyst.commons.resources.chess_game_invalid
import com.vitorpamplona.amethyst.commons.resources.chess_game_parse_failed
import com.vitorpamplona.amethyst.commons.resources.chess_game_pgn_content
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip64Chess.ChessGame
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.GameResult
import com.vitorpamplona.quartz.nip64Chess.PGNParser

/**
 * Complete chess game viewer with board, metadata, and navigation
 * Shared composable for Android and Desktop platforms
 *
 * Parses PGN content and displays:
 * - Game metadata (event, players, result)
 * - Interactive chess board
 * - Move navigation controls
 *
 * @param pgnContent PGN format string
 * @param modifier Modifier for the viewer
 * @param playerContent Optional composable to render player names with avatar + display name.
 *   When provided, PGN player names (which may be hex pubkeys) are rendered using this composable.
 */
@Composable
fun ChessGameViewer(
    pgnContent: String,
    modifier: Modifier = Modifier,
    playerContent: (@Composable (String) -> Unit)? = null,
) {
    val gameResult =
        remember(pgnContent) {
            PGNParser.parse(pgnContent)
        }

    gameResult.fold(
        onSuccess = { game ->
            ChessGameDisplay(game, modifier, playerContent)
        },
        onFailure = { error ->
            ChessGameError(
                errorMessage = error.message ?: stringRes(Res.string.chess_game_parse_failed),
                pgnContent = pgnContent,
                modifier = modifier,
            )
        },
    )
}

/**
 * A NIP-64 game record: the players beside the board with their scores, the board (on the final
 * position, the move that led to it highlighted), the moves, and the same controls as the live
 * board to step through them or turn it around.
 */
@Composable
private fun ChessGameDisplay(
    game: ChessGame,
    modifier: Modifier = Modifier,
    playerContent: (@Composable (String) -> Unit)? = null,
) {
    // Replayed by the engine rather than taken from the parser's positions, which lose castling;
    // the replay also knows each move's squares and the king in check. It stops at a move that
    // does not replay, and so does the list.
    val replay = remember(game) { ChessReplay.of(game.moves.map { it.san }) }
    val sans = remember(replay) { game.moves.take(replay.last.ply).map { it.san } }
    var ply by remember(game) { mutableStateOf(sans.size) }
    var flipped by remember(game) { mutableStateOf(false) }
    val shown = replay.at(ply)
    val bottom = if (flipped) Color.BLACK else Color.WHITE

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val heading = listOfNotNull(game.event, game.site, game.date).filter { it.isNotBlank() && '?' !in it }
        if (heading.isNotEmpty()) {
            Text(
                text = heading.joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RecordPlayer(game, bottom.opposite(), playerContent)
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ChessBoard(position = shown.position, boardSize = minOf(maxWidth, 480.dp), flipped = flipped, lastMove = shown.lastMove, checkSquare = shown.checkSquare)
        }
        RecordPlayer(game, bottom, playerContent)
        ChessMoveStrip(sans, ply, { ply = it.coerceIn(0, sans.size) })
        ChessGameToolbar(
            shownPly = ply,
            lastPly = sans.size,
            onSelectPly = { ply = it.coerceIn(0, sans.size) },
            onFlip = { flipped = !flipped },
            onResign = null,
        )
    }
}

/** One side of a record: its king, the player, and its score once the record has a result. */
@Composable
private fun RecordPlayer(
    game: ChessGame,
    color: Color,
    playerContent: (@Composable (String) -> Unit)?,
) {
    val name = if (color == Color.WHITE) game.white else game.black
    // A Nostr record names players by key; a classic PGN by name.
    val pubkey = remember(name) { name?.let { decodePublicKeyAsHexOrNull(it) } }
    val score =
        when (game.result) {
            GameResult.WHITE_WINS -> if (color == Color.WHITE) "1" else "0"
            GameResult.BLACK_WINS -> if (color == Color.BLACK) "1" else "0"
            GameResult.DRAW -> "½"
            GameResult.IN_PROGRESS -> null
        }
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChessSideBadge(color, 20.dp)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            when {
                name == null || name == "?" -> Text(stringRes(if (color == Color.WHITE) Res.string.chess_color_white else Res.string.chess_color_black), style = MaterialTheme.typography.bodyMedium)
                pubkey != null && playerContent != null -> playerContent(pubkey)
                else -> Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        score?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (it == "1") FontWeight.Bold else FontWeight.Normal,
                color = if (it == "1") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Displays error state when PGN parsing fails
 */
@Composable
private fun ChessGameError(
    errorMessage: String,
    pgnContent: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Error title
            Text(
                text = stringRes(Res.string.chess_game_invalid),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )

            // Error message
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            // PGN content label
            Text(
                text = stringRes(Res.string.chess_game_pgn_content),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 4.dp),
            )

            // Raw PGN content
            Text(
                text = pgnContent,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
