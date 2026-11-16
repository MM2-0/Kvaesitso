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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.fitness

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The chart primitives of the My Fitness dashboard. Plain Canvas, no charting library: each chart
 * is a few dozen lines, and the screen needs exactly these five shapes. They take primitives, not
 * fitness types, so they stay reusable and render in commonMain.
 *
 * Every chart takes a [Modifier] the caller puts a content description on — a chart is an image to
 * a screen reader, and only the caller knows the sentence it stands for.
 */

/**
 * The colours the charts draw with. Activity identity uses the first three slots of the dataviz
 * reference categorical palette (validated colour-blind safe as a set, in both modes); anything past
 * three folds into [other]. The goal rings take three further slots so a ring never looks like an
 * activity. The heatmap is a single-hue ramp of the theme's primary.
 */
@Immutable
class FitnessChartColors(
    val activities: List<Color>,
    val other: Color,
    val rings: List<Color>,
    val heat: List<Color>,
    val track: Color,
    val grid: Color,
    val goalLine: Color,
)

@Composable
fun rememberFitnessChartColors(): FitnessChartColors {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return remember(scheme.primary, scheme.surface, scheme.onSurface, dark) {
        val empty = scheme.onSurface.copy(alpha = if (dark) 0.10f else 0.07f).compositeOver(scheme.surface)
        FitnessChartColors(
            activities =
                if (dark) {
                    listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70))
                } else {
                    listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A))
                },
            other = scheme.onSurface.copy(alpha = 0.35f).compositeOver(scheme.surface),
            rings =
                if (dark) {
                    listOf(Color(0xFF9085E9), Color(0xFFD55181), Color(0xFF2FA82F))
                } else {
                    listOf(Color(0xFF4A3AA7), Color(0xFFE87BA4), Color(0xFF008300))
                },
            heat =
                listOf(
                    empty,
                    scheme.primary.copy(alpha = 0.28f).compositeOver(scheme.surface),
                    scheme.primary.copy(alpha = 0.50f).compositeOver(scheme.surface),
                    scheme.primary.copy(alpha = 0.75f).compositeOver(scheme.surface),
                    scheme.primary,
                ),
            track = empty,
            grid = scheme.onSurface.copy(alpha = 0.12f),
            goalLine = scheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

/** One ring: how far along a goal is, 1f being met. Overshoot is clamped — the label carries it. */
@Immutable
data class RingSpec(
    val fraction: Float,
    val color: Color,
)

/** Concentric goal rings, outermost first, with [center] drawn in the middle. */
@Composable
fun GoalRings(
    rings: List<RingSpec>,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    strokeWidth: Dp = 11.dp,
    gap: Dp = 3.dp,
    center: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val step = stroke + gap.toPx()
            rings.forEachIndexed { i, ring ->
                val inset = stroke / 2 + i * step
                val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                if (arcSize.width <= 0) return@forEachIndexed
                val topLeft = Offset(inset, inset)
                drawArc(ring.color.copy(alpha = 0.16f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                val sweep = 360f * ring.fraction.coerceIn(0f, 1f)
                if (sweep > 0f) {
                    drawArc(ring.color, -90f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
        center()
    }
}

/** A stacked bar: segments bottom-up, in a fixed colour order so a colour never changes height rank. */
@Immutable
data class StackedBar(
    val segments: List<Pair<Float, Color>>,
    val label: String,
) {
    val total: Float get() = segments.sumOf { it.first.toDouble() }.toFloat()
}

/**
 * Bars over weeks with a dashed goal line (keyed by [GoalLineLegend], not labelled in the plot,
 * where any position collides with some week's bar). Tapping a bar selects it, and a soft column behind it
 * marks it as the subject of the header above the chart. The bars themselves keep their colours —
 * dimming the rest muddies the activity colours, worst of all on a dark surface.
 *
 * Only every [labelEvery]th label is drawn — twelve date labels under twelve thin bars collide.
 */
@Composable
fun WeeklyBarChart(
    bars: List<StackedBar>,
    goal: Float?,
    selected: Int,
    onSelect: (Int) -> Unit,
    colors: FitnessChartColors,
    modifier: Modifier = Modifier,
    height: Dp = 148.dp,
    labelEvery: Int = 3,
) {
    val max = maxOf(bars.maxOfOrNull { it.total } ?: 0f, (goal ?: 0f) * 1.15f, 1f)

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(bars.size) {
                    detectTapGestures { offset ->
                        if (bars.isEmpty()) return@detectTapGestures
                        val slot = size.width.toFloat() / bars.size
                        onSelect((offset.x / slot).toInt().coerceIn(0, bars.size - 1))
                    }
                },
        ) {
            val slot = size.width / bars.size
            val barWidth = slot * 0.62f
            val gap = 2.dp.toPx()
            val radius = 4.dp.toPx()

            // Recessive baseline.
            drawLine(colors.grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())

            if (selected in bars.indices) {
                drawRoundRect(colors.track, Offset(selected * slot + slot * 0.06f, 0f), Size(slot * 0.88f, size.height), CornerRadius(6.dp.toPx()))
            }

            bars.forEachIndexed { i, bar ->
                val left = i * slot + (slot - barWidth) / 2
                var bottom = size.height
                val drawn = bar.segments.filter { it.first > 0f }
                drawn.forEachIndexed { s, (value, color) ->
                    val h = (value / max) * size.height
                    val top = bottom - h
                    // A 2dp surface gap between stacked fills, taken out of the upper segment.
                    val segmentBottom = if (s == 0) bottom else bottom - gap
                    if (segmentBottom - top > 0.5f) {
                        drawTopRounded(color, Rect(left, top, left + barWidth, segmentBottom), if (s == drawn.lastIndex) radius else 0f)
                    }
                    bottom = top
                }
                if (drawn.isEmpty()) {
                    // A zero week still gets a stub, so "nothing" reads as a measured zero, not a gap in the data.
                    drawTopRounded(colors.track, Rect(left, size.height - 3.dp.toPx(), left + barWidth, size.height), radius / 2)
                }
            }

            if (goal != null && goal > 0f) {
                val y = size.height - (goal / max) * size.height
                drawLine(
                    color = colors.goalLine,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
        }

        Row(Modifier.fillMaxWidth()) {
            bars.forEachIndexed { i, bar ->
                // Always label the last bar (this week); space the rest back from it.
                val show = (bars.lastIndex - i) % labelEvery == 0
                Text(
                    text = if (show) bar.label else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun DrawScope.drawTopRounded(
    color: Color,
    rect: Rect,
    radius: Float,
) {
    if (radius <= 0f) {
        drawRect(color, rect.topLeft, rect.size)
        return
    }
    val r = CornerRadius(radius.coerceAtMost(rect.width / 2), radius.coerceAtMost(rect.height))
    val path = Path().apply { addRoundRect(RoundRect(rect, topLeft = r, topRight = r, bottomRight = CornerRadius.Zero, bottomLeft = CornerRadius.Zero)) }
    drawPath(path, color)
}

/**
 * A GitHub-style calendar: one column per week, one row per weekday, cells shaded by [levels]
 * (0 = rest day, up to `colors.heat.lastIndex`; -1 = a day still to come, drawn as nothing).
 *
 * [rowLabels] has seven entries, blank for rows left unlabelled; [columnLabels] one per week,
 * null where no month starts.
 */
@Composable
fun CalendarHeatmap(
    levels: List<Int>,
    weeks: Int,
    rowLabels: List<String>,
    columnLabels: List<String?>,
    colors: FitnessChartColors,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val rowLabelWidth = rowLabels.maxOfOrNull { measurer.measure(it, labelStyle).size.width } ?: 0
    val headerHeight = measurer.measure("M", labelStyle).size.height
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gapDp = 3.dp
        val available = maxWidth - with(density) { (rowLabelWidth + 6).toDp() }
        val cell = (available - gapDp * (weeks - 1)) / weeks
        val totalHeight = with(density) { headerHeight.toDp() } + 4.dp + cell * 7 + gapDp * 6

        Canvas(Modifier.fillMaxWidth().height(totalHeight)) {
            val gap = gapDp.toPx()
            val c = cell.toPx()
            val originX = rowLabelWidth + 6.dp.toPx()
            val originY = headerHeight + 4.dp.toPx()
            val radius = CornerRadius(c * 0.22f)

            columnLabels.forEachIndexed { w, label ->
                label?.let {
                    val layout = measurer.measure(it, labelStyle)
                    // A month label that would overflow the right edge is shifted in, not clipped.
                    val x = (originX + w * (c + gap)).coerceAtMost(size.width - layout.size.width)
                    drawText(layout, topLeft = Offset(x, 0f))
                }
            }
            rowLabels.forEachIndexed { d, label ->
                if (label.isNotBlank()) {
                    val layout = measurer.measure(label, labelStyle)
                    drawText(layout, topLeft = Offset(0f, originY + d * (c + gap) + (c - layout.size.height) / 2))
                }
            }
            levels.forEachIndexed { i, level ->
                if (level < 0) return@forEachIndexed
                val w = i / 7
                val d = i % 7
                drawRoundRect(
                    color = colors.heat[level.coerceIn(0, colors.heat.lastIndex)],
                    topLeft = Offset(originX + w * (c + gap), originY + d * (c + gap)),
                    size = Size(c, c),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/** The "Less ▢▢▢▢ More" key under a heatmap. */
@Composable
fun HeatmapLegend(
    less: String,
    more: String,
    colors: FitnessChartColors,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(less, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        colors.heat.forEach { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(it)) }
        Text(more, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Plain single-series columns with a label under each — the weekday profile. [highlight] gets the
 * full colour; the rest a lighter step of the same hue, so the peak reads without a second hue.
 */
@Composable
fun ColumnChart(
    values: List<Float>,
    labels: List<String>,
    color: Color,
    highlight: Int?,
    colors: FitnessChartColors,
    modifier: Modifier = Modifier,
    height: Dp = 84.dp,
) {
    val max = maxOf(values.maxOrNull() ?: 0f, 1f)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val slot = size.width / values.size
            val barWidth = slot * 0.5f
            val radius = 4.dp.toPx()
            drawLine(colors.grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            values.forEachIndexed { i, v ->
                val left = i * slot + (slot - barWidth) / 2
                val h = (v / max) * size.height
                if (h > 0.5f) {
                    drawTopRounded(if (i == highlight) color else color.copy(alpha = 0.4f), Rect(left, size.height - h, left + barWidth, size.height), radius)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == highlight) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One labelled horizontal bar: `Morning ▇▇▇▇▇▇░░░ 12`. */
@Composable
fun LabeledBar(
    label: String,
    fraction: Float,
    value: String,
    color: Color,
    colors: FitnessChartColors,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 84.dp,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(labelWidth))
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(colors.track),
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(color),
                )
            }
        }
        Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
    }
}

/** A 100% stacked bar of shares, with a 2dp surface gap between segments. */
@Composable
fun ShareBar(
    shares: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    val total = shares.sumOf { it.first.toDouble() }.toFloat()
    Row(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (total <= 0f) return@Row
        shares.filter { it.first > 0f }.forEach { (value, color) ->
            Box(Modifier.weight(value / total).height(height).background(color))
        }
    }
}

/** What a day in the week strip shows. */
enum class DayMark { DONE, REST, TODAY, TODAY_DONE, FUTURE }

/** `M T W T F S S` with a filled dot for each day trained this week. */
@Composable
fun WeekStrip(
    labels: List<String>,
    marks: List<DayMark>,
    color: Color,
    colors: FitnessChartColors,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.zip(marks).forEach { (label, mark) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (mark == DayMark.TODAY || mark == DayMark.TODAY_DONE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val fill =
                    when (mark) {
                        DayMark.DONE, DayMark.TODAY_DONE -> color
                        DayMark.REST -> colors.track
                        DayMark.TODAY, DayMark.FUTURE -> Color.Transparent
                    }
                Box(Modifier.size(14.dp).clip(CircleShape).background(fill)) {
                    if (mark == DayMark.TODAY || mark == DayMark.FUTURE) {
                        Canvas(Modifier.size(14.dp)) {
                            drawCircle(
                                if (mark == DayMark.TODAY) color else colors.track,
                                radius = size.minDimension / 2 - 1.dp.toPx(),
                                style = Stroke(1.5.dp.toPx(), pathEffect = if (mark == DayMark.FUTURE) PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())) else null),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A colour chip and its name, for chart legends. */
@Composable
fun LegendChip(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** The legend entry for a dashed goal line. */
@Composable
fun GoalLineLegend(
    label: String,
    colors: FitnessChartColors,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.width(16.dp).height(10.dp)) {
            drawLine(
                colors.goalLine,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 2.dp.toPx())),
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Puts a sentence on a chart for screen readers, and makes the chart one node instead of many. */
fun Modifier.chartDescription(description: String): Modifier = this.semantics(mergeDescendants = true) { contentDescription = description }
