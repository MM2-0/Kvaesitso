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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessEngine
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessMoveSquares
import com.vitorpamplona.quartz.nip64Chess.ChessPiece
import com.vitorpamplona.quartz.nip64Chess.ChessPosition
import com.vitorpamplona.quartz.nip64Chess.PieceType
import kotlin.math.roundToInt
import com.vitorpamplona.quartz.nip64Chess.Color as ChessColor

/** Lichess's board palette, so the board reads the way most players already know it. */
internal object BoardColors {
    val Light = Color(0xFFF0D9B5)
    val Dark = Color(0xFFB58863)
    val LastMove = Color(0x699BC700)
    val Selected = Color(0x8014551E)
    val Target = Color(0x8014551E)
    val Check = Color(0xFFFF0000)
}

/**
 * A chess board the user moves on: tap a piece then its target, or drag it there.
 *
 * It draws [position] (by default the live one in [engine]), the [lastMove]'s two squares and a
 * king in check, and asks [engine] for legal moves. It never moves a piece itself: [onMoveMade]
 * gets `from` and `to` (with a promotion letter, like `e8q`) and must publish the move; the board
 * redraws when the next position arrives.
 *
 * @param flipped Black's view, rank 1 at the top.
 * @param playerColor The only colour the user may move; null lets either side move.
 * @param isSpectator No input at all: not the user's turn, a spectator, or a past position.
 */
@Composable
fun InteractiveChessBoard(
    engine: ChessEngine,
    modifier: Modifier = Modifier,
    boardSize: Dp = 400.dp,
    flipped: Boolean = false,
    playerColor: ChessColor? = null,
    isSpectator: Boolean = false,
    positionVersion: Int = 0,
    position: ChessPosition? = null,
    lastMove: ChessMoveSquares? = null,
    checkSquare: String? = null,
    onMoveMade: (from: String, to: String, san: String) -> Unit = { _, _, _ -> },
) {
    val livePosition = remember(engine, positionVersion) { engine.getPosition() }
    val shown = position ?: livePosition
    val sideToMove = livePosition.activeColor
    val canInteract = position == null && !isSpectator && (playerColor == null || sideToMove == playerColor)

    var selected by remember { mutableStateOf<String?>(null) }
    var targets by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dragFrom by remember { mutableStateOf<String?>(null) }
    var dragAt by remember { mutableStateOf<Offset?>(null) }
    var pendingPromotion by remember { mutableStateOf<PendingPromotion?>(null) }

    // A new position (the opponent moved, or the user's move landed) drops whatever was half-done.
    LaunchedEffect(positionVersion, position, canInteract) {
        selected = null
        targets = emptySet()
        dragFrom = null
        dragAt = null
        pendingPromotion = null
    }

    val squareSize = boardSize / 8
    val density = LocalDensity.current.density
    val currentOnMoveMade by rememberUpdatedState(onMoveMade)

    fun clearSelection() {
        selected = null
        targets = emptySet()
    }

    fun select(square: String) {
        selected = square
        targets = engine.getLegalMovesFrom(square).toSet()
    }

    fun ownPieceAt(square: String): Boolean {
        val piece = livePosition[square] ?: return false
        return piece.color == (playerColor ?: sideToMove)
    }

    fun play(
        from: String,
        to: String,
    ) {
        val piece = livePosition[from]
        val toRank = to[1] - '1'
        clearSelection()
        if (piece?.type == PieceType.PAWN && (toRank == 7 || toRank == 0)) {
            pendingPromotion = PendingPromotion(from, to, piece.color)
        } else {
            currentOnMoveMade(from, to, "")
        }
    }

    fun squareAt(offset: Offset): String? {
        val side = squareSize.value * density
        val column = (offset.x / side).toInt()
        val row = (offset.y / side).toInt()
        if (column !in 0..7 || row !in 0..7) return null
        val file = if (flipped) 7 - column else column
        val rank = if (flipped) row else 7 - row
        return fileRankToSquare(file, rank)
    }

    Box(
        modifier =
            modifier
                .size(boardSize)
                .pointerInput(canInteract, flipped, livePosition, boardSize) {
                    if (!canInteract) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val pressed = squareAt(down.position) ?: return@awaitEachGesture
                        if (pendingPromotion != null) return@awaitEachGesture

                        val wasSelected = selected
                        if (wasSelected != null && pressed in targets) {
                            play(wasSelected, pressed)
                            return@awaitEachGesture
                        }
                        if (!ownPieceAt(pressed)) {
                            clearSelection()
                            return@awaitEachGesture
                        }
                        if (wasSelected != pressed) select(pressed)

                        // Follow the finger; it becomes a drag once it leaves the touch slop.
                        var dragging = false
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            last = change.position
                            if (!dragging && (last - down.position).getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                                dragFrom = pressed
                            }
                            if (dragging) {
                                dragAt = last
                                change.consume()
                            }
                            if (!change.pressed) break
                        }

                        if (dragging) {
                            val target = squareAt(last)
                            dragFrom = null
                            dragAt = null
                            if (target != null && target in targets) play(pressed, target)
                        } else if (wasSelected == pressed) {
                            // A second tap on the selected piece puts it down.
                            clearSelection()
                        }
                    }
                },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0..7) {
                Row {
                    for (column in 0..7) {
                        val file = if (flipped) 7 - column else column
                        val rank = if (flipped) row else 7 - row
                        val square = fileRankToSquare(file, rank)
                        BoardSquare(
                            piece = shown.pieceAt(file, rank).takeIf { square != dragFrom },
                            isLight = (rank + file) % 2 != 0,
                            size = squareSize,
                            isLastMove = lastMove != null && (square == lastMove.from || square == lastMove.to),
                            isSelected = square == selected,
                            isTarget = square in targets,
                            isCheck = square == checkSquare,
                            rankLabel = if (column == 0) (rank + 1).toString() else null,
                            fileLabel = if (row == 7) ('a' + file).toString() else null,
                        )
                    }
                }
            }
        }

        // The dragged piece rides under the finger, centred on it.
        val from = dragFrom
        val at = dragAt
        if (from != null && at != null) {
            livePosition[from]?.let { piece ->
                Image(
                    imageVector = piece.toImageVector(),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(squareSize)
                            .offset { IntOffset((at.x - squareSize.toPx() / 2).roundToInt(), (at.y - squareSize.toPx() / 2).roundToInt()) },
                )
            }
        }

        pendingPromotion?.let { promo ->
            PromotionPicker(
                color = promo.color,
                squareSize = squareSize,
                onPieceSelected = { pieceType ->
                    val suffix =
                        when (pieceType) {
                            PieceType.ROOK -> "r"
                            PieceType.BISHOP -> "b"
                            PieceType.KNIGHT -> "n"
                            else -> "q"
                        }
                    pendingPromotion = null
                    currentOnMoveMade(promo.from, promo.to + suffix, "")
                },
                onDismiss = { pendingPromotion = null },
            )
        }
    }
}

/** One square: its colour, the highlights on it, its piece and, on the edges, its coordinate. */
@Composable
private fun BoardSquare(
    piece: ChessPiece?,
    isLight: Boolean,
    size: Dp,
    isLastMove: Boolean,
    isSelected: Boolean,
    isTarget: Boolean,
    isCheck: Boolean,
    rankLabel: String?,
    fileLabel: String?,
) {
    Box(
        modifier = Modifier.size(size).background(if (isLight) BoardColors.Light else BoardColors.Dark),
        contentAlignment = Alignment.Center,
    ) {
        if (isLastMove) Box(Modifier.fillMaxSize().background(BoardColors.LastMove))
        if (isSelected) Box(Modifier.fillMaxSize().background(BoardColors.Selected))
        if (isCheck) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(BoardColors.Check, BoardColors.Check.copy(alpha = 0.6f), Color.Transparent)),
                ),
            )
        }

        piece?.let {
            Image(
                imageVector = it.toImageVector(),
                contentDescription = "${it.color} ${it.type}",
                modifier = Modifier.fillMaxSize().padding(2.dp),
            )
        }

        if (isTarget) {
            if (piece == null) {
                Box(Modifier.size(size * 0.3f).background(BoardColors.Target, CircleShape))
            } else {
                Box(Modifier.fillMaxSize().border(size * 0.08f, BoardColors.Target, CircleShape))
            }
        }

        val labelColor = if (isLight) BoardColors.Dark else BoardColors.Light
        rankLabel?.let {
            Text(it, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor, modifier = Modifier.align(Alignment.TopStart).padding(2.dp))
        }
        fileLabel?.let {
            Text(it, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor, modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp))
        }
    }
}

/**
 * Convert file/rank to algebraic notation (e.g., 0,0 -> "a1")
 */
private fun fileRankToSquare(
    file: Int,
    rank: Int,
): String = "${('a' + file)}${rank + 1}"

/**
 * Extension function to convert ChessPiece to CBurnett ImageVector
 */
internal fun ChessPiece.toImageVector(): ImageVector {
    val white = color == ChessColor.WHITE
    return when (type) {
        PieceType.KING -> if (white) ChessPieceVectors.WhiteKing else ChessPieceVectors.BlackKing
        PieceType.QUEEN -> if (white) ChessPieceVectors.WhiteQueen else ChessPieceVectors.BlackQueen
        PieceType.ROOK -> if (white) ChessPieceVectors.WhiteRook else ChessPieceVectors.BlackRook
        PieceType.BISHOP -> if (white) ChessPieceVectors.WhiteBishop else ChessPieceVectors.BlackBishop
        PieceType.KNIGHT -> if (white) ChessPieceVectors.WhiteKnight else ChessPieceVectors.BlackKnight
        PieceType.PAWN -> if (white) ChessPieceVectors.WhitePawn else ChessPieceVectors.BlackPawn
    }
}

/**
 * Data class for pending pawn promotion
 */
private data class PendingPromotion(
    val from: String,
    val to: String,
    val color: ChessColor,
)

/**
 * Promotion piece picker overlay
 */
@Composable
private fun PromotionPicker(
    color: ChessColor,
    squareSize: Dp,
    onPieceSelected: (PieceType) -> Unit,
    onDismiss: () -> Unit,
) {
    val isWhite = color == ChessColor.WHITE
    val promotionPieces =
        listOf(
            PieceType.QUEEN to if (isWhite) ChessPieceVectors.WhiteQueen else ChessPieceVectors.BlackQueen,
            PieceType.ROOK to if (isWhite) ChessPieceVectors.WhiteRook else ChessPieceVectors.BlackRook,
            PieceType.BISHOP to if (isWhite) ChessPieceVectors.WhiteBishop else ChessPieceVectors.BlackBishop,
            PieceType.KNIGHT to if (isWhite) ChessPieceVectors.WhiteKnight else ChessPieceVectors.BlackKnight,
        )

    // Semi-transparent overlay
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.clickable { /* prevent dismiss */ },
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                promotionPieces.forEach { (pieceType, icon) ->
                    Box(
                        modifier =
                            Modifier
                                .size(squareSize)
                                .background(Color(0xFFF0D9B5), RoundedCornerShape(4.dp))
                                .clickable { onPieceSelected(pieceType) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            imageVector = icon,
                            contentDescription = pieceType.name,
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}
