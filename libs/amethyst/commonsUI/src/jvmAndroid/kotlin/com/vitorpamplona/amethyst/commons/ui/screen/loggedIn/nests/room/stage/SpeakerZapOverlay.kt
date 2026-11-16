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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.stage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.stage.SpeakerReactionOverlay
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.util.showAmountInteger
import com.vitorpamplona.amethyst.commons.viewmodels.REACTION_WINDOW_SEC
import com.vitorpamplona.amethyst.commons.viewmodels.RoomZap
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay

/**
 * Floating zap-chip overlay drawn over a participant's avatar — the
 * zap counterpart to [SpeakerReactionOverlay]. The aggregator groups
 * zaps by sender, so this renders the zaps a single participant has
 * *sent*: consecutive zaps from that sender stack into a row, each
 * chip keyed by its event id. The chip life-cycle (`fadeIn + scaleIn`
 * on arrival, upward drift + `fadeOut` over [REACTION_WINDOW_SEC])
 * matches reactions so both streams visually feel like the same
 * animation.
 *
 * Renders an "⚡ Nsats" pill in [BitcoinOrange] so zaps are distinct
 * from emoji reactions at a glance.
 */
@Composable
fun SpeakerZapOverlay(
    zaps: List<RoomZap>,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = zaps.isNotEmpty(),
        enter = fadeIn() + scaleIn(initialScale = 0.6f),
        exit = fadeOut() + scaleOut(targetScale = 0.6f),
        modifier = modifier,
    ) {
        // Newest zap leads the row, matching SpeakerReactionOverlay's
        // most-recent-first ordering so the two overlays read the same.
        val ordered = zaps.sortedByDescending { it.createdAtSec }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ordered.take(MAX_VISIBLE_ZAPS).forEach { zap ->
                ZapChip(zap = zap)
            }
        }
    }
}

@Composable
private fun ZapChip(zap: RoomZap) {
    // Mirror of [SpeakerReactionOverlay.ReactionChip]: tick a [progress]
    // state from 0f → 1f over the eviction window so the chip can drift
    // + fade in lockstep with how close it is to being evicted. Re-key
    // on the event id so the animation restarts whenever a fresh zap
    // replaces the value at this slot.
    var progress by remember(zap.eventId) { mutableFloatStateOf(0f) }
    LaunchedEffect(zap.eventId) {
        val ageMs = (TimeUtils.now() - zap.createdAtSec).coerceAtLeast(0L) * 1000L
        val remaining = (ZAP_WINDOW_MS - ageMs).coerceAtLeast(0L)
        progress = (ageMs.toFloat() / ZAP_WINDOW_MS).coerceIn(0f, 1f)
        if (remaining <= 0L) return@LaunchedEffect
        val steps = (remaining / 100L).coerceAtLeast(1L)
        repeat(steps.toInt()) {
            delay(100L)
            progress =
                ((TimeUtils.now() - zap.createdAtSec).toFloat() * 1000f / ZAP_WINDOW_MS)
                    .coerceIn(0f, 1f)
        }
        progress = 1f
    }

    val driftDp = (-16f * progress).dp
    val animatedAlpha by animateFloatAsState(
        targetValue = (1f - ((progress - 0.5f).coerceAtLeast(0f) * 2f)).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "zap-chip-alpha",
    )

    Surface(
        modifier =
            Modifier
                .offset(y = driftDp)
                .alpha(animatedAlpha),
        shape = MaterialTheme.shapes.small,
        color = BitcoinOrange.copy(alpha = 0.95f),
        // BitcoinOrange is theme-independent, so pin the content color
        // to white rather than colorScheme.onPrimary (which tracks the
        // theme's primary, not this fixed orange).
        contentColor = Color.White,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        val amountText =
            zap.amountSats?.let { showAmountInteger(it.toBigDecimal()) } ?: ""
        val label = if (amountText.isNotEmpty()) "⚡ $amountText" else "⚡"
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private const val ZAP_WINDOW_MS = REACTION_WINDOW_SEC * 1000L

// Cap the row at a small number so a burst of zaps from the same
// sender doesn't overflow the avatar's top-center anchor — the
// eviction sweep clears them within the window anyway.
private const val MAX_VISIBLE_ZAPS = 3
