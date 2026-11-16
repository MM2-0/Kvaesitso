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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.ui.richtext.InLineIconRenderer
import com.vitorpamplona.amethyst.commons.util.customEmojiUrlOrNull
import com.vitorpamplona.amethyst.commons.viewmodels.RoomReaction
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import kotlinx.collections.immutable.persistentListOf

/**
 * Floating-emoji overlay drawn under a speaker's avatar. Each
 * incoming `kind-7` becomes its own independent chip — two
 * reactions arriving in quick succession produce two concurrent
 * rises, not one shared chip that restarts. The previous
 * implementation grouped by content and used `youngestSec` as a
 * `remember` key, which meant a second `🔥` from the same speaker
 * interrupted the first chip's animation and showed `×2` instead
 * of two distinct floating emojis.
 *
 * Reactions live for [REACTION_WINDOW_SEC] seconds in the
 * aggregator; each chip animates over [REACTION_RISE_MS] and then
 * stays invisible (alpha=0) until the aggregator evicts it. We cap
 * the visible-chip count at [MAX_VISIBLE_CHIPS] so a flurry of
 * reactions doesn't bleed the Row into the next column.
 */
@Composable
fun SpeakerReactionOverlay(
    reactions: List<RoomReaction>,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = reactions.isNotEmpty(),
        enter = fadeIn() + scaleIn(initialScale = 0.6f),
        exit = fadeOut() + scaleOut(targetScale = 0.6f),
        modifier = modifier,
    ) {
        // Most-recent first; capped so the layered Box doesn't grow
        // an unbounded set of overlapping chips. Older reactions drop
        // off as new ones arrive (the aggregator still tracks them
        // for eviction but they're not drawn once past the cap).
        val visible =
            remember(reactions) {
                reactions
                    .sortedByDescending { it.createdAtSec }
                    .take(MAX_VISIBLE_CHIPS)
            }
        // Box, not Row — every chip stacks at the same right-anchored
        // spot so the X position stays fixed throughout the chip's
        // lifecycle. The previous Row layout slid older chips
        // leftward each time a new reaction arrived (sortedByDescending
        // put the newest at index 0). With overlapping chips, each
        // animates independently in place; the newest is drawn on
        // top by virtue of being last in the iteration.
        Box(contentAlignment = androidx.compose.ui.Alignment.BottomEnd) {
            // Iterate in reverse (oldest first) so the newest reaction
            // ends up last in the Box and therefore on top of the
            // paint stack.
            visible.asReversed().forEach { reaction ->
                // Per-event-id key — independent animation lifecycle
                // per reaction. Same-emoji bursts no longer collide
                // on a shared chip.
                key(reaction.eventId) {
                    ReactionChip(
                        content = reaction.content,
                        youngestSec = reaction.createdAtSec,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionChip(
    content: String,
    youngestSec: Long,
) {
    // Manual frame-clock animation. Earlier passes using
    // `Animatable.animateTo(...)` and `animateFloatAsState(...)` never
    // produced visible motion in this site — five iterations of
    // tuning didn't help. Driving the progress State directly from
    // `withFrameNanos` is the lowest-level Compose animation pattern
    // there is: each frame, we read the elapsed time, compute a 0→1
    // fraction, and write it into a MutableFloatState. The
    // `graphicsLayer` lambda below reads that State, so its block
    // re-invokes per frame without going through the standard
    // animation framework. Keyed on [youngestSec] so a fresh
    // reaction in the same emoji burst restarts the animation
    // cleanly.
    var progress by remember(youngestSec) { mutableFloatStateOf(0f) }
    LaunchedEffect(youngestSec) {
        val startNanos = withFrameNanos { it }
        while (true) {
            val nowNanos = withFrameNanos { it }
            val elapsedMs = (nowNanos - startNanos) / 1_000_000f
            val raw = (elapsedMs / REACTION_RISE_MS).coerceIn(0f, 1f)
            // FastOutSlowInEasing — quick start, slow tail. Pulled
            // by hand so we don't import the animation-core easing
            // for one call site.
            progress = FastOutSlowInEasing.transform(raw)
            if (raw >= 1f) break
        }
    }

    // The chip drifts up, grows, and fades out — three concurrent
    // tracks driven by the same `progress` value, all set inside
    // the graphicsLayer lambda so the read tracks at draw time
    // (per frame, no full recomposition needed):
    //   * translationY: 0 → -DRIFT_MAX_DP. Capped within the cell's
    //     headroom (otherwise the chip clips the avatar above on a
    //     75 dp grid cell).
    //   * scale: 1 → SCALE_MAX. Mirrors a "rising bubble" — the
    //     emoji looks closer/larger as it lifts.
    //   * alpha: 1 → 1 → 0. Solid for the first ALPHA_HOLD_FRAC of
    //     progress; fades over the remainder so the chip dissipates
    //     well before the 10 s aggregator-eviction tick.
    // Fixed-size Box so each chip occupies the same footprint
    // regardless of which emoji is rendered — "🔥" and "👍" have
    // different intrinsic glyph widths, and without a fixed box the
    // Row's right-anchored layout slid the visible content left/right
    // by a few dp on every new reaction. Centring the Text inside a
    // [CHIP_SIZE]-square Box pins the emoji's centre to a stable
    // point. No Surface / background — transparent over the avatar.
    Box(
        modifier =
            Modifier
                .size(CHIP_SIZE)
                .graphicsLayer {
                    val p = progress
                    translationY = -DRIFT_MAX_DP.dp.toPx() * p
                    scaleX = 1f + (SCALE_MAX - 1f) * p
                    scaleY = scaleX
                    alpha =
                        (
                            1f -
                                (
                                    (p - ALPHA_HOLD_FRAC).coerceAtLeast(0f) /
                                        (1f - ALPHA_HOLD_FRAC)
                                )
                        ).coerceIn(0f, 1f)
                },
        contentAlignment = Alignment.Center,
    ) {
        RenderReactionContent(content)
    }
}

/**
 * Render a single reaction's content the same way
 * `ReactionsRow.RenderReactionType` does, so a kind-7 with a NIP-30
 * custom emoji shows the image (not the `:shortcode:url` string)
 * and special "+" / "-" tokens fall back to the standard heart / 👎
 * conventions.
 *
 *   - `":<shortcode>:<url>"` → image rendered inline via
 *     [InLineIconRenderer] using [CustomEmoji.ImageUrlType].
 *   - `"+"` → ❤️ (the legacy NIP-25 "like" symbol; the heart icon
 *     vector lives in `ReactionsRow.kt` as a private composable so
 *     here we render the unicode heart at the same size).
 *   - `"-"` → 👎.
 *   - Anything else → rendered verbatim as Text (the common case —
 *     a single unicode emoji).
 */
@Composable
private fun RenderReactionContent(content: String) {
    // A bare `:shortcode:` (its event had no emoji tag) has no image: it falls to the text branch.
    val renderable =
        remember(content) {
            content.customEmojiUrlOrNull()?.let { persistentListOf(CustomEmoji.ImageUrlType(it)) }
        }
    if (renderable != null) {
        InLineIconRenderer(
            wordsInOrder = renderable,
            style = SpanStyle(color = Color.Unspecified),
            fontSize = EMOJI_FONT_SIZE,
            maxLines = 1,
        )
        return
    }
    val display =
        when (content) {
            "+" -> "❤️"
            "-" -> "👎"
            else -> content
        }
    Text(
        text = display,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = EMOJI_FONT_SIZE),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

// 3 s pop-and-drift. Earlier 1.5 s, 4 s, and 6 s passes (when the
// animation was broken upstream) didn't read well; 3 s with the
// working frame-clock animation lands as a brisk rise — fast enough
// to feel like a reaction, slow enough to track visually — and
// still finishes inside the 10 s aggregator-eviction window.
private const val REACTION_RISE_MS = 3000L

// Final rise distance has to clear the avatar without crashing into
// the cell above. 28 dp lands the chip near the top edge of a 75 dp
// avatar — readable, no clip.
private const val DRIFT_MAX_DP = 28f

// 1.6× peak scale gives a clearly visible "growing as it rises"
// effect. Applied via graphicsLayer so it's drawing-only and
// doesn't reflow neighbours each frame.
private const val SCALE_MAX = 1.6f

// Alpha holds at 1 for the first 50 % of progress, then ramps to 0
// over the remaining 50 %. Keeps the reaction readable in its prime
// without abrupt removal.
private const val ALPHA_HOLD_FRAC = 0.5f

// Base font for the emoji glyph. labelSmall (default Material caption
// ~11 sp) was too small to read at avatar-grid scale; 22 sp gets the
// emoji to roughly the same visual size as a stage-grid avatar badge.
private val EMOJI_FONT_SIZE = 22.sp

// Fixed chip footprint. Slightly larger than the emoji glyph so the
// glyph (which varies in intrinsic width across emojis) is centred
// in a consistent box. With this, the Row's right-anchored layout
// produces a perfectly stable left edge per chip count, no matter
// which emoji.
private val CHIP_SIZE = 30.dp

// Cap on concurrently-rendered chips per sender. The aggregator
// keeps reactions for 10 s; without a cap, a burst of 8+ reactions
// would stretch the Row past the cell and into the next column even
// after the older chips have faded to alpha=0 (still occupy layout).
// 3 reads as "the user is reacting a lot" without overflowing.
private const val MAX_VISIBLE_CHIPS = 3
