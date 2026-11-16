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
package com.vitorpamplona.amethyst.commons.ui.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn

/** The side padding of a note's author + content area in [NoteComposeLayout]. */
val NoteHorizontalPadding = 12.dp

/**
 * Pre-computed pixel dimensions for [NoteComposeLayout], cached via [remember]
 * so dp-to-px conversion only runs once per density change.
 */
@Immutable
private data class NoteLayoutPx(
    /** Author picture column width: 55dp */
    val authorWidth: Int,
    /** Horizontal gap between author column and content column: 10dp */
    val authorContentGap: Int,
    /** Vertical spacer between header rows and note content: 4dp */
    val contentSpacer: Int,
    /** Vertical spacer between the full-width header (picture + rows) and note content: 8dp */
    val headerSpacer: Int,
    /** Left padding for the content area: 12dp */
    val padStart: Int,
    /** Right padding for the content area: 12dp */
    val padEnd: Int,
    /** Top padding for the content area: 10dp */
    val padTop: Int,
)

@Composable
@Preview
private fun NoteComposeLayoutPreview() {
    ThemeComparisonColumn {
        NoteComposeLayoutPreviewCard()
    }
}

@Composable
@Preview
private fun NoteComposeLayoutFullWidthPreview() {
    ThemeComparisonColumn {
        NoteComposeLayoutPreviewCard(fullWidth = true)
    }
}

@Composable
private fun NoteComposeLayoutPreviewCard(fullWidth: Boolean = false) {
    NoteComposeLayout(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
        fullWidth = fullWidth,
        showSecondRow = true,
        authorPicture = {
            Box(
                Modifier
                    .size(if (fullWidth) 35.dp else 55.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF9575CD)),
            )
        },
        firstRow = {
            Text(
                "Alice  @alice  2h  ...",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondRow = {
            Text(
                "alice@nostr.com",
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1,
            )
        },
        noteContent = {
            Text(
                "This is a sample note content that demonstrates the custom " +
                    "NoteComposeLayout with all slots populated. The layout handles " +
                    "author picture, relay badges, header rows, content, and reactions.",
            )
        },
        reactionsRow = {
            Text("  Reply 3     Boost 5     Like 12     Zap 1.2k", fontSize = 12.sp, color = Color.Gray)
        },
    )
}

@Composable
@Preview
private fun NoteComposeLayoutBoostedPreview() {
    ThemeComparisonColumn {
        NoteComposeLayout(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
            addPadding = false,
            showAuthorColumn = false,
            authorPicture = {},
            firstRow = {
                Text("Bob boosted  2h  ...")
            },
            secondRow = {},
            noteContent = {
                Text("Boosted note content without author column or padding.")
            },
            reactionsRow = {
                Text("  Reply     Boost     Like     Zap", fontSize = 12.sp, color = Color.Gray)
            },
        )
    }
}

/**
 * Custom single-pass layout for note feed items that replaces the nested
 * Row/Column structure with direct constraint calculation using known fixed
 * dimensions.
 *
 * ## Layout structure
 *
 * When [showAuthorColumn] is true (normal notes):
 * ```
 * ┌──────────────────────────────────────────────┐
 * │ padding (start=12, end=12, top=10)           │
 * │ ┌──────┐ 10dp ┌────────────────────────────┐ │
 * │ │author│  gap  │ firstRow                   │ │
 * │ │ 55dp │       │ secondRow (optional)       │ │
 * │ │      │       │ 4dp spacer (optional)      │ │
 * │ └──────┘       │ noteContent                │ │
 * │                └────────────────────────────┘ │
 * ├───────────────────────────────────────────────┤
 * │ reactionsRow (full width, no side padding)    │
 * └───────────────────────────────────────────────┘
 * ```
 *
 * When [showAuthorColumn] and [fullWidth] are true (the full-width note style, like the
 * Picture feed cards):
 * ```
 * ┌───────────────────────────────────────────────┐
 * │ padding (start=12, end=12, top=10)            │
 * │ ┌──────┐ 10dp ┌─────────────────────────────┐ │
 * │ │author│  gap  │ firstRow                    │ │
 * │ │      │       │ secondRow (optional)        │ │
 * │ └──────┘       └─────────────────────────────┘ │
 * │ 8dp spacer (optional)                         │
 * │ noteContent (full inner width)                │
 * ├───────────────────────────────────────────────┤
 * │ reactionsRow (full width, no side padding)    │
 * └───────────────────────────────────────────────┘
 * ```
 * The author picture and the header rows are centered on each other, and [authorGutter]
 * is not used: there is no column under the picture to hold it.
 *
 * When [showAuthorColumn] is false (boosted/quoted notes):
 * ```
 * ┌───────────────────────────────────────────────┐
 * │ firstRow (full width)                         │
 * │ noteContent                                   │
 * ├───────────────────────────────────────────────┤
 * │ reactionsRow (full width)                     │
 * └───────────────────────────────────────────────┘
 * ```
 *
 * ## Design choices
 *
 * **Multi-content Layout instead of layoutId**: Uses [Layout] with `contents: List`
 * so each slot's composables become direct measurables without Box/Column wrappers.
 * This eliminates 6 intermediate layout nodes per note compared to the layoutId
 * approach. Empty slot lambdas produce empty measurable lists, which are skipped
 * entirely (no [Constraints.fixed] zero-size measurement needed).
 *
 * **Padding in measure policy instead of Modifier.padding()**: The reactions row
 * spans the full parent width without side padding, while the author + content area
 * has 12dp horizontal padding. A single Modifier.padding() can't express different
 * padding for different children, so padding offsets are applied during placement.
 *
 * **placeRelative instead of place**: Ensures correct positioning in RTL layouts
 * by automatically mirroring x coordinates.
 *
 * **arrayOfNulls instead of map**: For multi-child slots (noteContent, reactionsRow),
 * uses pre-sized arrays with indexed iteration to avoid List allocation overhead in
 * the measure pass hot path.
 *
 * ## Slot ordering contract
 *
 * The `contents` list and `allMeasurables` indices are:
 * - 0: [authorPicture] - 0 or 1 measurables
 * - 1: [firstRow] - exactly 1 measurable
 * - 2: [secondRow] - 0 or 1 measurables
 * - 3: [noteContent] - 1+ measurables (stacked vertically)
 * - 4: [reactionsRow] - 0+ measurables (stacked vertically)
 * - 5: [authorGutter] - 0 or 1 measurables
 *
 * ## Performance
 *
 * Compared to the previous nested Row > Column > Column structure:
 * - Eliminates 3 layout node levels (Row, author Column, content Column)
 * - Eliminates Row's two-pass measurement (measure author first, then content)
 * - Pre-computes all pixel dimensions once via [remember], cached across recompositions
 * - Remaining per-frame allocations: ~40 bytes for `listOf(5 lambdas)` +
 *   ~24 bytes per `arrayOfNulls` for multi-child slots
 *
 * The caller should use `drawBehind { drawRect(color) }` instead of
 * `background(color)` on the [modifier] to avoid recomposition when the
 * background color state changes (e.g. "new item" highlight fade).
 *
 * @param modifier Applied to the Layout root. Typically includes combinedClickable
 *   for note navigation, drawBehind for background color, and fillMaxWidth.
 * @param addPadding Whether to add standard note padding (12dp sides, 10dp top)
 *   to the author + content area. False for boosted notes.
 * @param showAuthorColumn Whether to reserve 55dp for the author picture column
 *   with a 10dp gap. False for boosted/quoted notes.
 * @param fullWidth Whether the author picture heads the note beside the header rows and the
 *   content takes the full width below them, instead of keeping a column for the picture.
 *   Only matters when [showAuthorColumn] is true.
 * @param showSecondRow Whether to measure and place the second header row
 *   (NIP-05 status, location, PoW). Requires complete UI mode.
 * @param showContentSpacer Whether to add a spacer between header rows and
 *   note content (4dp, or 8dp under a [fullWidth] header). False for repost events.
 * @param authorPicture Slot for the author's profile picture (55x55dp area).
 *   Should emit nothing when [showAuthorColumn] is false to skip composition.
 * @param firstRow Slot for the primary header: author name, time, more options.
 *   Always present.
 * @param secondRow Slot for the secondary header: NIP-05, location, PoW, OTS.
 *   Should emit nothing when [showSecondRow] is false.
 * @param noteContent Slot for the event-specific content. May emit multiple
 *   children (content + zap splits + approval button) which are stacked vertically.
 * @param reactionsRow Slot for the reaction buttons row. May emit a ReactionsRow,
 *   a Spacer, or nothing depending on event type.
 * @param authorGutter Slot for the author column below the picture, sized to run down
 *   to the end of the note content (the expanded text's "Show Less"). Usually empty;
 *   when it emits, the main area grows to fit at least its minimum height.
 */
@Composable
fun NoteComposeLayout(
    modifier: Modifier = Modifier,
    addPadding: Boolean = true,
    showAuthorColumn: Boolean = true,
    fullWidth: Boolean = false,
    showSecondRow: Boolean = false,
    showContentSpacer: Boolean = true,
    authorPicture: @Composable () -> Unit,
    firstRow: @Composable () -> Unit,
    secondRow: @Composable () -> Unit,
    noteContent: @Composable () -> Unit,
    reactionsRow: @Composable () -> Unit,
    authorGutter: @Composable () -> Unit = {},
) {
    val density = LocalDensity.current
    val px =
        remember(density) {
            with(density) {
                NoteLayoutPx(
                    authorWidth = Size55dp.roundToPx(),
                    authorContentGap = 10.dp.roundToPx(),
                    contentSpacer = 4.dp.roundToPx(),
                    headerSpacer = 8.dp.roundToPx(),
                    padStart = NoteHorizontalPadding.roundToPx(),
                    padEnd = NoteHorizontalPadding.roundToPx(),
                    padTop = 10.dp.roundToPx(),
                )
            }
        }

    // Slot order must match the indices used in the measure policy below.
    Layout(
        contents =
            listOf(
                authorPicture, // 0
                firstRow, // 1
                secondRow, // 2
                noteContent, // 3
                reactionsRow, // 4
                authorGutter, // 5
            ),
        modifier = modifier,
    ) { allMeasurables, constraints ->
        val maxWidth = constraints.maxWidth

        // Padding is applied in the measure policy (not via Modifier.padding) because
        // the reactions row needs full width while the content area is inset.
        val padStart = if (addPadding) px.padStart else 0
        val padEnd = if (addPadding) px.padEnd else 0
        val padTop = if (addPadding) px.padTop else 0

        // Column widths are computed from known fixed dimensions, eliminating
        // the Row's two-pass measurement (measure author → compute remaining → measure content).
        val innerWidth = (maxWidth - padStart - padEnd).coerceAtLeast(0)
        val authorOnTop = showAuthorColumn && fullWidth
        val authorW = if (showAuthorColumn && !authorOnTop) px.authorWidth else 0
        val gap = if (showAuthorColumn && !authorOnTop) px.authorContentGap else 0
        val contentWidth = (innerWidth - authorW - gap).coerceAtLeast(0)

        // Constraints is a value class (inline Long) so these don't allocate.
        val authorConstraints = Constraints(maxWidth = px.authorWidth)
        val contentConstraints = Constraints(maxWidth = contentWidth)
        val fullWidthConstraints = Constraints(maxWidth = maxWidth)

        // Single-child slots: firstOrNull() returns null for empty slots (hidden),
        // skipping measurement entirely.
        val authorPlaceable = allMeasurables[0].firstOrNull()?.measure(authorConstraints)

        // With the author on top, the header rows sit beside the picture and the content below both.
        val headerX = if (authorOnTop && authorPlaceable != null) authorPlaceable.width + px.authorContentGap else 0
        val headerConstraints =
            if (authorOnTop) Constraints(maxWidth = (innerWidth - headerX).coerceAtLeast(0)) else contentConstraints

        val firstRowPlaceable = allMeasurables[1].firstOrNull()?.measure(headerConstraints)
        val secondRowPlaceable =
            if (showSecondRow) allMeasurables[2].firstOrNull()?.measure(headerConstraints) else null

        // Multi-child slots: measured into pre-sized arrays to avoid List allocation.
        val contentMeasurables = allMeasurables[3]
        val contentPlaceables = arrayOfNulls<Placeable>(contentMeasurables.size)
        var contentStackHeight = 0
        for (i in contentMeasurables.indices) {
            val placeable = contentMeasurables[i].measure(contentConstraints)
            contentPlaceables[i] = placeable
            contentStackHeight += placeable.height
        }

        val reactionsMeasurables = allMeasurables[4]
        val reactionsPlaceables = arrayOfNulls<Placeable>(reactionsMeasurables.size)
        var reactionsHeight = 0
        for (i in reactionsMeasurables.indices) {
            val placeable = reactionsMeasurables[i].measure(fullWidthConstraints)
            reactionsPlaceables[i] = placeable
            reactionsHeight += placeable.height
        }

        // Height calculation
        // Under a full-width header the body clears the picture, as on the Picture feed cards.
        val spacer =
            when {
                !showContentSpacer -> 0
                authorOnTop -> px.headerSpacer
                else -> px.contentSpacer
            }

        val headerRowsHeight = (firstRowPlaceable?.height ?: 0) + (secondRowPlaceable?.height ?: 0)
        val headerHeight = if (authorOnTop) maxOf(authorPlaceable?.height ?: 0, headerRowsHeight) else headerRowsHeight

        val contentColumnHeight = headerHeight + spacer + contentStackHeight

        val gutterMeasurable =
            if (showAuthorColumn && !authorOnTop && authorPlaceable != null) allMeasurables[5].firstOrNull() else null
        val gutterTop = (authorPlaceable?.height ?: 0) + px.authorContentGap

        val authorColumnHeight =
            if (showAuthorColumn && !authorOnTop && authorPlaceable != null) {
                if (gutterMeasurable != null) {
                    gutterTop + gutterMeasurable.minIntrinsicHeight(px.authorWidth)
                } else {
                    authorPlaceable.height
                }
            } else {
                0
            }

        val mainAreaHeight = maxOf(authorColumnHeight, contentColumnHeight)

        val gutterPlaceable =
            gutterMeasurable?.let {
                // A very long expanded note can be taller than Constraints can hold at this width.
                val height = (mainAreaHeight - gutterTop).coerceAtLeast(0)
                it.measure(Constraints.fitPrioritizingWidth(px.authorWidth, px.authorWidth, height, height))
            }
        val totalHeight = padTop + mainAreaHeight + reactionsHeight

        // placeRelative handles RTL by mirroring x coordinates automatically.
        layout(maxWidth, totalHeight.coerceAtLeast(constraints.minHeight)) {
            // Content column (to the right of author, inside padding area)
            val contentX = padStart + authorW + gap
            var y = padTop

            if (authorOnTop) {
                // Author picture and header rows centered on each other, content below both.
                authorPlaceable?.let { it.placeRelative(padStart, padTop + (headerHeight - it.height) / 2) }
                y += (headerHeight - headerRowsHeight) / 2

                firstRowPlaceable?.placeRelative(padStart + headerX, y)
                y += firstRowPlaceable?.height ?: 0

                if (showSecondRow) {
                    secondRowPlaceable?.placeRelative(padStart + headerX, y)
                }

                y = padTop + headerHeight
            } else {
                // Author column (inside padding area)
                if (showAuthorColumn) {
                    authorPlaceable?.placeRelative(padStart, padTop)
                    gutterPlaceable?.placeRelative(padStart, padTop + gutterTop)
                }

                firstRowPlaceable?.placeRelative(contentX, y)
                y += firstRowPlaceable?.height ?: 0

                if (showSecondRow) {
                    secondRowPlaceable?.placeRelative(contentX, y)
                    y += secondRowPlaceable?.height ?: 0
                }
            }

            y += spacer

            for (placeable in contentPlaceables) {
                placeable?.placeRelative(contentX, y)
                y += placeable?.height ?: 0
            }

            // Reactions row (full width, no side padding, below main area)
            var reactionsY = padTop + mainAreaHeight
            for (placeable in reactionsPlaceables) {
                placeable?.placeRelative(0, reactionsY)
                reactionsY += placeable?.height ?: 0
            }
        }
    }
}
