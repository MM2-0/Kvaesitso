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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chess_flip_board
import com.vitorpamplona.amethyst.commons.resources.chess_move_navigator_final
import com.vitorpamplona.amethyst.commons.resources.chess_move_navigator_next
import com.vitorpamplona.amethyst.commons.resources.chess_move_navigator_previous
import com.vitorpamplona.amethyst.commons.resources.chess_move_navigator_start
import com.vitorpamplona.amethyst.commons.resources.chess_offer_draw
import com.vitorpamplona.amethyst.commons.resources.chess_resign
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip64Chess.ChessPiece
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.PieceType
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * A player's line beside the board: who they are ([player]), what they have taken and by how much
 * they are ahead, and a dot while it is their move.
 */
@Composable
fun ChessPlayerStrip(
    color: Color,
    isToMove: Boolean,
    captured: List<PieceType>,
    advantage: Int,
    modifier: Modifier = Modifier,
    player: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        player()
        CapturedPieces(captured, color.opposite())
        if (advantage > 0) {
            Text(
                text = "+$advantage",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        if (isToMove) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

/** The pieces of [color] that were taken, small and overlapping, the way scoresheets draw them. */
@Composable
private fun CapturedPieces(
    pieces: List<PieceType>,
    color: Color,
) {
    if (pieces.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        pieces.forEach { type ->
            Image(
                imageVector = ChessPiece(type, color).toImageVector(),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The moves so far as a scoresheet: one row per move number, White's and Black's side by side. */
@Composable
fun ChessMoveTable(
    history: List<String>,
    shownPly: Int,
    onSelectPly: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = (history.size + 1) / 2
    val state = rememberLazyListState()
    LaunchedEffect(shownPly) {
        if (rows > 0) state.animateScrollToItem(((shownPly - 1) / 2).coerceIn(0, rows - 1))
    }
    LazyColumn(state = state, modifier = modifier) {
        items(rows) { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${row + 1}.",
                    modifier = Modifier.width(40.dp).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoveCell(history, row * 2 + 1, shownPly, onSelectPly, Modifier.weight(1f))
                MoveCell(history, row * 2 + 2, shownPly, onSelectPly, Modifier.weight(1f))
            }
        }
    }
}

/** The moves so far on one scrolling line, for narrow screens: `1. e4 e5 2. Nf3 …`. */
@Composable
fun ChessMoveStrip(
    history: List<String>,
    shownPly: Int,
    onSelectPly: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    LaunchedEffect(shownPly, history.size) {
        if (history.isNotEmpty()) state.animateScrollToItem((shownPly - 1).coerceIn(0, history.lastIndex))
    }
    LazyRow(state = state, modifier = modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        itemsIndexed(history) { index, _ ->
            val ply = index + 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ply % 2 == 1) {
                    Text(
                        text = "${(ply + 1) / 2}.",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MoveCell(history, ply, shownPly, onSelectPly)
            }
        }
    }
}

@Composable
private fun MoveCell(
    history: List<String>,
    ply: Int,
    shownPly: Int,
    onSelectPly: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val san = history.getOrNull(ply - 1) ?: return Spacer(modifier)
    val isShown = ply == shownPly
    Text(
        text = san,
        modifier =
            modifier
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .background(
                    if (isShown) MaterialTheme.colorScheme.primaryContainer else ComposeColor.Transparent,
                    RoundedCornerShape(4.dp),
                ).clickable { onSelectPly(ply) }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (isShown) FontWeight.Bold else FontWeight.Normal,
        color = if (isShown) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * Stepping through the game and the board's own actions: first, back, forward and last; flip, for
 * a spectator ([onFlip] null otherwise); and resign, for a player ([onResign] null otherwise).
 */
@Composable
fun ChessGameToolbar(
    shownPly: Int,
    lastPly: Int,
    onSelectPly: (Int) -> Unit,
    onFlip: (() -> Unit)?,
    onResign: (() -> Unit)?,
    modifier: Modifier = Modifier,
    drawOfferArmed: Boolean = false,
    onToggleDrawOffer: ((Boolean) -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSelectPly(0) }, enabled = shownPly > 0) {
            Icon(MaterialSymbols.SkipPrevious, stringRes(Res.string.chess_move_navigator_start))
        }
        IconButton(onClick = { onSelectPly(shownPly - 1) }, enabled = shownPly > 0) {
            Icon(MaterialSymbols.AutoMirrored.KeyboardArrowLeft, stringRes(Res.string.chess_move_navigator_previous))
        }
        IconButton(onClick = { onSelectPly(shownPly + 1) }, enabled = shownPly < lastPly) {
            Icon(MaterialSymbols.AutoMirrored.KeyboardArrowRight, stringRes(Res.string.chess_move_navigator_next))
        }
        IconButton(onClick = { onSelectPly(lastPly) }, enabled = shownPly < lastPly) {
            Icon(MaterialSymbols.SkipNext, stringRes(Res.string.chess_move_navigator_final))
        }
        Spacer(Modifier.weight(1f))
        if (onFlip != null) {
            IconButton(onClick = onFlip) {
                Icon(MaterialSymbols.SwapVert, stringRes(Res.string.chess_flip_board))
            }
        }
        if (onToggleDrawOffer != null) {
            // ½ is how chess writes a draw; the toggle arms an offer that rides on the next move.
            val label = stringRes(Res.string.chess_offer_draw)
            FilledTonalIconToggleButton(
                checked = drawOfferArmed,
                onCheckedChange = onToggleDrawOffer,
                modifier = Modifier.semantics { contentDescription = label },
            ) {
                Text("½", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (onResign != null) {
            IconButton(onClick = onResign) {
                Icon(MaterialSymbols.Flag, stringRes(Res.string.chess_resign))
            }
        }
    }
}
