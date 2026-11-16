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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wotNudge

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_activate
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_dismiss
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_eyebrow
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_first_action
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_hero_body
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_in_circle
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_not_now
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_on_top
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_rings_you_follow
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_turn_on
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.showCount
import kotlinx.coroutines.delay

/*
 * Prototypes of the circle card that say less: a number, a picture and one button. Each takes
 * the same [CircleNudge] as [CircleRingsCard], so Home can swap one in without other changes.
 */

/** What a circle card draws, and what it calls back. */
class CircleNudge(
    val self: NudgeFace,
    val faces: List<NudgeFace>,
    val followCount: Int,
    /** The people the follows trust; zero until their lists load. */
    val extendedCount: Int,
    val providers: List<CircleProvider>,
    val selected: CircleProvider,
    val activation: CircleActivation,
    val onSelect: (CircleProvider) -> Unit,
    val onActivate: () -> Unit,
    val onNotNow: () -> Unit,
    val onDismiss: () -> Unit,
) {
    val working get() = activation is CircleActivation.Working
}

/** Prototype A: the rings, the number, one sentence on what changes, and Activate. */
@Composable
fun CircleHeroCard(
    nudge: CircleNudge,
    modifier: Modifier = Modifier,
) {
    NudgeSurface(modifier, nudge.onDismiss, background = rememberCircleGlow()) {
        Eyebrow()
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            CircleRings(nudge.self, nudge.faces.take(6), Modifier.size(120.dp), ringsSize = 120.dp)
            CircleCount(nudge.followCount, nudge.extendedCount, 44.sp)
        }
        Spacer(Modifier.height(12.dp))
        // Once activated the pitch has done its job: the panel below takes its place.
        if (nudge.activation != CircleActivation.Activated) {
            Text(
                stringRes(Res.string.wot_nudge_hero_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(14.dp))
        }
        CircleAction(nudge, stringRes(Res.string.wot_nudge_activate))
    }
}

/** Prototype B: one row. Pulsing rings, the number, "Turn on". */
@Composable
fun CircleBannerCard(
    nudge: CircleNudge,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.background(rememberCircleGlow()).padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleRings(nudge.self, nudge.faces.take(4), Modifier.size(72.dp), ringsSize = 72.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                CircleCount(nudge.followCount, nudge.extendedCount, 26.sp)
                if (nudge.activation is CircleActivation.Failed) CircleError(nudge.activation)
            }
            when (nudge.activation) {
                CircleActivation.Activated -> CircularProgressIndicator(Modifier.padding(horizontal = 12.dp).size(18.dp), strokeWidth = 2.dp)
                is CircleActivation.Working -> CircularProgressIndicator(Modifier.padding(horizontal = 12.dp).size(18.dp), strokeWidth = 2.dp)
                else -> Button(onClick = nudge.onActivate) { Text(stringRes(Res.string.wot_nudge_turn_on), fontWeight = FontWeight.SemiBold, maxLines = 1) }
            }
            IconButton(
                onClick = if (nudge.activation == CircleActivation.Activated) nudge.onDismiss else nudge.onNotNow,
                enabled = !nudge.working,
            ) {
                Icon(
                    MaterialSymbols.Close,
                    contentDescription = stringRes(Res.string.wot_nudge_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Prototype C: centered and big. The rings, the number under them, where it moves up as icons. */
@Composable
fun CircleSpotlightCard(
    nudge: CircleNudge,
    modifier: Modifier = Modifier,
) {
    NudgeSurface(modifier, nudge.onDismiss, background = rememberCircleGlow()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CircleRings(nudge.self, nudge.faces.take(8), Modifier.size(168.dp), ringsSize = 168.dp)
            Spacer(Modifier.height(8.dp))
            CircleCount(nudge.followCount, nudge.extendedCount, 48.sp, centered = true)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PlaceUp(MaterialSymbols.Forum)
                PlaceUp(MaterialSymbols.Notifications)
                PlaceUp(MaterialSymbols.Mail)
            }
        }
        Spacer(Modifier.height(16.dp))
        CircleAction(nudge, stringRes(Res.string.wot_nudge_first_action))
    }
}

/**
 * Prototype D: show, don't tell. A mock list sorts itself: your circle slides above strangers,
 * every few seconds. Four words of copy.
 */
@Composable
fun CircleSortedCard(
    nudge: CircleNudge,
    modifier: Modifier = Modifier,
) {
    NudgeSurface(modifier, nudge.onDismiss, background = rememberCircleGlow()) {
        Text(
            stringRes(Res.string.wot_nudge_on_top),
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 32.dp),
        )
        Spacer(Modifier.height(12.dp))
        SortingList(nudge.faces)
        Spacer(Modifier.height(14.dp))
        CircleAction(nudge, stringRes(Res.string.wot_nudge_turn_on))
    }
}

private class MockRow(
    val face: NudgeFace?,
    val mixed: Int,
    val sorted: Int,
    val first: Float,
    val second: Float,
)

@Composable
private fun SortingList(faces: List<NudgeFace>) {
    val rows =
        remember(faces) {
            listOf(
                MockRow(faces.getOrNull(0), mixed = 1, sorted = 0, first = 0.35f, second = 0.8f),
                MockRow(faces.getOrNull(1), mixed = 3, sorted = 1, first = 0.45f, second = 0.6f),
                MockRow(null, mixed = 0, sorted = 2, first = 0.3f, second = 0.7f),
                MockRow(null, mixed = 2, sorted = 3, first = 0.4f, second = 0.55f),
            )
        }
    // Sorted first, so a still frame shows the payoff; then mixed for a moment, and back.
    var sorted by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            sorted = false
            delay(1_400)
            sorted = true
        }
    }
    val rowHeight = 44.dp
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
        Box(Modifier.fillMaxWidth().height(rowHeight * rows.size)) {
            rows.forEach { row ->
                val y = animateDpAsState(rowHeight * (if (sorted) row.sorted else row.mixed), tween(700, easing = FastOutSlowInEasing), label = "row")
                MockRowView(row, Modifier.height(rowHeight).offset { IntOffset(0, y.value.roundToPx()) })
            }
        }
    }
}

@Composable
private fun MockRowView(
    row: MockRow,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val inCircle = row.face != null
    Row(
        modifier.fillMaxWidth().graphicsLayer { alpha = if (inCircle) 1f else 0.45f }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).let { if (inCircle) it.border(1.5.dp, scheme.primary, CircleShape) else it }) {
            if (row.face != null) {
                row.face(28.dp)
            } else {
                Box(Modifier.size(28.dp).background(scheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(MaterialSymbols.Person, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Bar(row.first, if (inCircle) scheme.primary.copy(alpha = 0.55f) else scheme.onSurface.copy(alpha = 0.25f))
            Bar(row.second, scheme.onSurface.copy(alpha = 0.12f))
        }
        if (inCircle) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                Icon(MaterialSymbols.ArrowUpward, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun Bar(
    fraction: Float,
    color: Color,
) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(7.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}

@Composable
private fun Eyebrow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(MaterialSymbols.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, filled = true, modifier = Modifier.size(16.dp))
        Text(stringRes(Res.string.wot_nudge_eyebrow), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

/** "~13k in your circle", or just the follows until the people they trust are counted. */
@Composable
private fun CircleCount(
    followCount: Int,
    extendedCount: Int,
    size: TextUnit,
    centered: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val known = extendedCount > 0
    Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            if (known) "~" + showCount(followCount + extendedCount) else showCount(followCount),
            style =
                TextStyle(
                    brush = Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)),
                    fontSize = size,
                    lineHeight = size * 1.05f,
                    fontWeight = FontWeight.ExtraBold,
                ),
            maxLines = 1,
        )
        Text(
            stringRes(if (known) Res.string.wot_nudge_in_circle else Res.string.wot_nudge_rings_you_follow),
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 1,
        )
    }
}

/** A place's icon with the up arrow on it. */
@Composable
private fun PlaceUp(symbol: MaterialSymbol) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(scheme.surface.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        Icon(symbol, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(scheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MaterialSymbols.ArrowUpward, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(10.dp))
        }
    }
}

/** The button and the footer, or the "mapping" line once activated. */
@Composable
private fun CircleAction(
    nudge: CircleNudge,
    label: String,
) {
    if (nudge.activation == CircleActivation.Activated) {
        CircleActivatedPanel(nudge.selected.name, nudge.onDismiss)
        return
    }
    if (nudge.activation is CircleActivation.Failed) CircleError(nudge.activation)
    CircleActivateButton(nudge.activation, label, nudge.onActivate, Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleProviderLine(nudge.providers, nudge.selected, nudge.onSelect, enabled = !nudge.working, modifier = Modifier.weight(1f))
        TextButton(onClick = nudge.onNotNow, enabled = !nudge.working) { Text(stringRes(Res.string.wot_nudge_not_now)) }
    }
}

@Composable
private fun CircleError(failed: CircleActivation.Failed) {
    Text(
        failed.message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
