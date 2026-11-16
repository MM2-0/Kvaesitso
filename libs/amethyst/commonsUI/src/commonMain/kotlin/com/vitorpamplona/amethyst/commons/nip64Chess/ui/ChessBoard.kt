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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessMoveSquares
import com.vitorpamplona.quartz.nip64Chess.ChessPiece
import com.vitorpamplona.quartz.nip64Chess.ChessPosition

/**
 * Renders an 8x8 chess board with pieces
 * Shared composable for Android and Desktop platforms
 *
 * @param position The chess position to display
 * @param modifier Modifier for the board
 * @param boardSize Total size of the board in dp
 * @param showCoordinates Whether to show file labels (a-h) on bottom rank
 */
@Composable
fun ChessBoard(
    position: ChessPosition,
    modifier: Modifier = Modifier,
    boardSize: Dp = 320.dp,
    showCoordinates: Boolean = true,
    flipped: Boolean = false,
    lastMove: ChessMoveSquares? = null,
    checkSquare: String? = null,
) {
    val squareSize = boardSize / 8

    Column(modifier = modifier.size(boardSize)) {
        for (row in 0..7) {
            Row {
                for (column in 0..7) {
                    val file = if (flipped) 7 - column else column
                    val rank = if (flipped) row else 7 - row
                    val square = "${'a' + file}${rank + 1}"

                    ChessSquare(
                        piece = position.pieceAt(file, rank),
                        // a1 is dark: each player has a light square on their right.
                        isLight = (rank + file) % 2 != 0,
                        size = squareSize,
                        isLastMove = lastMove != null && (square == lastMove.from || square == lastMove.to),
                        isCheck = square == checkSquare,
                        coordinate = if (showCoordinates && row == 7) ('a' + file).toString() else null,
                        rankLabel = if (showCoordinates && column == 0) (rank + 1).toString() else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChessSquare(
    piece: com.vitorpamplona.quartz.nip64Chess.ChessPiece?,
    isLight: Boolean,
    size: Dp,
    isLastMove: Boolean,
    isCheck: Boolean,
    coordinate: String?,
    rankLabel: String? = null,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .background(if (isLight) BoardColors.Light else BoardColors.Dark),
        contentAlignment = Alignment.Center,
    ) {
        if (isLastMove) Box(Modifier.fillMaxSize().background(BoardColors.LastMove))
        if (isCheck) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(BoardColors.Check, BoardColors.Check.copy(alpha = 0.6f), Color.Transparent))))
        }

        // Display piece using CBurnett ImageVector icons
        piece?.let {
            Image(
                imageVector = it.toImageVector(),
                contentDescription = "${it.color} ${it.type}",
                modifier = Modifier.fillMaxSize().padding(2.dp),
            )
        }

        // Show rank label (1-8) on left file
        rankLabel?.let {
            Text(
                text = it,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isLight) Color(0xFFB58863) else Color(0xFFF0D9B5),
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp),
            )
        }

        // Show file coordinate (a-h) on bottom rank
        coordinate?.let {
            Text(
                text = it,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isLight) Color(0xFFB58863) else Color(0xFFF0D9B5),
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp),
            )
        }
    }
}
