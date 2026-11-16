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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessOutcome
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessOutcome.Perspective
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chess_back_to_games
import com.vitorpamplona.amethyst.commons.resources.chess_by_abandonment
import com.vitorpamplona.amethyst.commons.resources.chess_by_agreement
import com.vitorpamplona.amethyst.commons.resources.chess_by_checkmate
import com.vitorpamplona.amethyst.commons.resources.chess_by_resignation
import com.vitorpamplona.amethyst.commons.resources.chess_by_stalemate
import com.vitorpamplona.amethyst.commons.resources.chess_by_timeout
import com.vitorpamplona.amethyst.commons.resources.chess_color_black
import com.vitorpamplona.amethyst.commons.resources.chess_color_white
import com.vitorpamplona.amethyst.commons.resources.chess_result_black_wins
import com.vitorpamplona.amethyst.commons.resources.chess_result_draw
import com.vitorpamplona.amethyst.commons.resources.chess_result_white_wins
import com.vitorpamplona.amethyst.commons.resources.chess_result_you_lost
import com.vitorpamplona.amethyst.commons.resources.chess_result_you_won
import com.vitorpamplona.amethyst.commons.resources.chess_review_game
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip64Chess.ChessPiece
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.GameTermination
import com.vitorpamplona.quartz.nip64Chess.PieceType
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.graphics.Color as ComposeColor

private val Gold = ComposeColor(0xFFFFC94D)
private val Win = ComposeColor(0xFF4CAF50)

/** The headline of a result: "You won", "You lost", "Draw", or who won for a spectator. */
@Composable
fun outcomeTitle(outcome: ChessOutcome): String =
    stringRes(
        when (outcome.perspective) {
            Perspective.WON -> Res.string.chess_result_you_won
            Perspective.LOST -> Res.string.chess_result_you_lost
            Perspective.DRAW -> Res.string.chess_result_draw
            Perspective.SPECTATOR -> if (outcome.winner == Color.WHITE) Res.string.chess_result_white_wins else Res.string.chess_result_black_wins
        },
    )

/** "by checkmate", "by resignation", … or null when the game did not say. */
@Composable
fun terminationText(termination: GameTermination?): String? =
    when (termination) {
        GameTermination.CHECKMATE -> stringRes(Res.string.chess_by_checkmate)
        GameTermination.RESIGNATION -> stringRes(Res.string.chess_by_resignation)
        GameTermination.STALEMATE -> stringRes(Res.string.chess_by_stalemate)
        GameTermination.DRAW_AGREEMENT -> stringRes(Res.string.chess_by_agreement)
        GameTermination.TIMEOUT -> stringRes(Res.string.chess_by_timeout)
        GameTermination.ABANDONMENT -> stringRes(Res.string.chess_by_abandonment)
        null -> null
    }

/** The colour that tells a result at a glance: green won, red lost, the theme's for a draw or someone else's game. */
@Composable
fun outcomeColor(outcome: ChessOutcome): ComposeColor =
    when (outcome.perspective) {
        Perspective.WON -> Win
        Perspective.LOST -> MaterialTheme.colorScheme.error
        Perspective.DRAW -> MaterialTheme.colorScheme.secondary
        Perspective.SPECTATOR -> MaterialTheme.colorScheme.primary
    }

/** A player's score in a finished game: 1, 0 or ½. */
fun ChessOutcome.scoreOf(color: Color): String =
    when (winner) {
        null -> "½"
        color -> "1"
        else -> "0"
    }

/** The winner's badge, or both badges for a draw. */
@Composable
fun OutcomeKings(
    outcome: ChessOutcome,
    size: Dp,
) {
    val winner = outcome.winner
    if (winner == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ChessSideBadge(Color.WHITE, size)
            ChessSideBadge(Color.BLACK, size)
        }
    } else {
        ChessSideBadge(winner, size)
    }
}

/**
 * A side, drawn as its king on a light square, so both kings read on any background. The
 * content description names the side.
 */
@Composable
fun ChessSideBadge(
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val name = stringRes(if (color == Color.WHITE) Res.string.chess_color_white else Res.string.chess_color_black)
    Box(
        modifier =
            modifier
                .size(size)
                .background(BoardColors.Light, RoundedCornerShape(size / 5))
                .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        ChessKing(color, Modifier.size(size * 0.86f))
    }
}

/** The king of [color]: how the chess screens draw a side. */
@Composable
fun ChessKing(
    color: Color,
    modifier: Modifier = Modifier,
) = Image(imageVector = ChessPiece(PieceType.KING, color).toImageVector(), contentDescription = null, modifier = modifier)

/** One line on a finished game, for under the board: the winner's king, the result and how it came. */
@Composable
fun ChessResultBanner(
    outcome: ChessOutcome,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutcomeKings(outcome, 24.dp)
        Text(
            text = outcomeTitle(outcome),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = outcomeColor(outcome),
        )
        terminationText(outcome.termination)?.let {
            Text(text = it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The moment a game ends in front of the user: a medal (the user's king crowned in gold for a
 * win, tipped over for a loss, both kings for a draw), the result and how it came, the score by
 * player ([player] draws one), and the two ways on, reviewing the game or going back to the
 * others. It is compact and sits at the bottom, or at the top when [atTop] (the caller's choice,
 * to keep a mated king in sight); a win rains confetti.
 */
@Composable
fun BoxScope.ChessGameEndCard(
    outcome: ChessOutcome,
    myColor: Color?,
    atTop: Boolean,
    player: @Composable RowScope.(Color) -> Unit,
    onReview: () -> Unit,
    onExit: () -> Unit,
) {
    // A tap anywhere off the card closes it, like reviewing.
    Box(
        Modifier
            .matchParentSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onReview),
    )

    if (outcome.perspective == Perspective.WON) Confetti(Modifier.matchParentSize())

    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    val tip = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (outcome.perspective == Perspective.LOST) tip.animateTo(-90f, tween(durationMillis = 700, delayMillis = 250, easing = FastOutSlowInEasing))
    }

    Surface(
        modifier =
            Modifier
                .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(12.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 16.dp,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Medal(outcome, myColor, tip.value, Modifier.scale(pop.value))
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = outcomeTitle(outcome),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = outcomeColor(outcome),
                    )
                    terminationText(outcome.termination)?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            ScoreLine(outcome, Color.WHITE, player)
            ScoreLine(outcome, Color.BLACK, player)
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onReview, modifier = Modifier.weight(1f)) { Text(stringRes(Res.string.chess_review_game)) }
                Button(onClick = onExit, modifier = Modifier.weight(1f)) { Text(stringRes(Res.string.chess_back_to_games)) }
            }
        }
    }
}

@Composable
private fun ScoreLine(
    outcome: ChessOutcome,
    color: Color,
    player: @Composable RowScope.(Color) -> Unit,
) {
    val won = outcome.winner == color
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChessSideBadge(color, 22.dp)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { player(color) }
        Text(
            text = outcome.scoreOf(color),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = if (won) FontWeight.Bold else FontWeight.Normal,
            color = if (won) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A disc on a glow: for a win the user's king in gold light, for a loss the user's king tipped
 * over by [tipDegrees], for a draw both kings, for a spectator the winner's king.
 */
@Composable
private fun Medal(
    outcome: ChessOutcome,
    myColor: Color?,
    tipDegrees: Float,
    modifier: Modifier = Modifier,
) {
    val glow =
        when (outcome.perspective) {
            Perspective.WON -> Gold
            Perspective.LOST -> MaterialTheme.colorScheme.error
            Perspective.DRAW -> MaterialTheme.colorScheme.secondary
            Perspective.SPECTATOR -> MaterialTheme.colorScheme.primary
        }
    Box(
        modifier =
            modifier
                .size(76.dp)
                .background(Brush.radialGradient(listOf(glow.copy(alpha = 0.6f), glow.copy(alpha = 0.15f), ComposeColor.Transparent)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(58.dp)
                .background(BoardColors.Light, CircleShape)
                .border(2.dp, glow, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when (outcome.perspective) {
                Perspective.DRAW ->
                    Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                        ChessKing(Color.WHITE, Modifier.size(32.dp))
                        ChessKing(Color.BLACK, Modifier.size(32.dp))
                    }
                Perspective.LOST -> ChessKing(myColor ?: Color.WHITE, Modifier.size(40.dp).rotate(tipDegrees))
                Perspective.WON -> ChessKing(myColor ?: outcome.winner ?: Color.WHITE, Modifier.size(42.dp))
                Perspective.SPECTATOR -> ChessKing(outcome.winner ?: Color.WHITE, Modifier.size(42.dp))
            }
        }
    }
}

private class Flake(
    val x: Float,
    val speed: Float,
    val sway: Float,
    val phase: Float,
    val spin: Float,
    val size: Float,
    val color: ComposeColor,
)

/** A short burst of falling confetti, played once. */
@Composable
private fun Confetti(modifier: Modifier) {
    val colors = listOf(Gold, Win, MaterialTheme.colorScheme.primary, ComposeColor(0xFFFF6E9C), ComposeColor(0xFF4FC3F7))
    val flakes =
        remember {
            val random = Random(7)
            List(90) {
                Flake(
                    x = random.nextFloat(),
                    speed = 0.7f + random.nextFloat() * 0.6f,
                    sway = 0.02f + random.nextFloat() * 0.05f,
                    phase = random.nextFloat() * 2f * PI.toFloat(),
                    spin = 180f + random.nextFloat() * 540f,
                    size = 6f + random.nextFloat() * 8f,
                    color = colors[random.nextInt(colors.size)],
                )
            }
        }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 2800, easing = LinearEasing)) }

    Canvas(modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val fade = if (t > 0.7f) (1f - t) / 0.3f else 1f
        flakes.forEach { flake ->
            val y = (-0.1f + t * flake.speed * 1.2f) * size.height
            val x = (flake.x + sin(t * 6f + flake.phase) * flake.sway) * size.width
            rotate(degrees = flake.spin * t, pivot = Offset(x, y)) {
                drawRect(
                    color = flake.color.copy(alpha = fade),
                    topLeft = Offset(x - flake.size / 2, y - flake.size / 4),
                    size = Size(flake.size, flake.size / 2),
                )
            }
        }
    }
}
