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
package com.vitorpamplona.amethyst.commons.ui.layouts.listItem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.ui.layouts.listItem.ListTokens.ListItemContainerElevation
import kotlin.jvm.JvmInline
import kotlin.math.max

/**
 * This is a copy of Material3's ListItemLayout.kt file, with the only change being the padding change from 16.dp to 10.dp
 */

@Composable
fun SlimListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    // The container default stays `background` — what this layout has always painted — rather than
    // ListItemDefaults' `surface`, so existing callers are unchanged. It is a parameter now because
    // the row was painting its own opaque background even when the caller asked for another colour:
    // inside a container that already has a surface (a dialog) that reads as a black block.
    colors: ListItemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    tonalElevation: Dp = ListItemContainerElevation,
    shadowElevation: Dp = ListItemContainerElevation,
) {
    val decoratedHeadlineContent: @Composable () -> Unit = {
        ProvideTextStyleFromToken(
            colors.contentColor,
            MaterialTheme.typography.bodyLarge,
            headlineContent,
        )
    }
    val decoratedSupportingContent: @Composable (() -> Unit)? =
        supportingContent?.let {
            {
                Box(SupportingContentTopPadding) {
                    ProvideTextStyleFromToken(
                        colors.supportingContentColor,
                        MaterialTheme.typography.bodyMedium,
                        it,
                    )
                }
            }
        }
    val decoratedOverlineContent: @Composable (() -> Unit)? =
        overlineContent?.let {
            {
                ProvideTextStyleFromToken(
                    colors.overlineContentColor,
                    MaterialTheme.typography.labelSmall,
                    it,
                )
            }
        }
    val decoratedLeadingContent: @Composable (() -> Unit)? =
        leadingContent?.let {
            {
                Box(LeadingContentEndPadding) {
                    CompositionLocalProvider(
                        LocalContentColor provides colors.leadingContentColor,
                        content = it,
                    )
                }
            }
        }
    val decoratedTrailingContent: @Composable (() -> Unit)? =
        trailingContent?.let {
            {
                Box(TrailingContentStartPadding) {
                    ProvideTextStyleFromToken(
                        colors.trailingContentColor,
                        MaterialTheme.typography.labelSmall,
                        content = it,
                    )
                }
            }
        }

    Surface(
        modifier = Modifier.semantics(mergeDescendants = true) {}.then(modifier),
        shape = ListItemDefaults.shape,
        color = colors.containerColor,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    ) {
        SlimListItemLayout(
            headline = decoratedHeadlineContent,
            overline = decoratedOverlineContent,
            supporting = decoratedSupportingContent,
            leading = decoratedLeadingContent,
            trailing = decoratedTrailingContent,
        )
    }
}

@Composable
private fun SlimListItemLayout(
    leading: @Composable (() -> Unit)?,
    trailing: @Composable (() -> Unit)?,
    headline: @Composable () -> Unit,
    overline: @Composable (() -> Unit)?,
    supporting: @Composable (() -> Unit)?,
) {
    val measurePolicy = remember { ListItemMeasurePolicy() }
    Layout(
        contents =
            listOf(headline, overline ?: {}, supporting ?: {}, leading ?: {}, trailing ?: {}),
        measurePolicy = measurePolicy,
    )
}

private class ListItemMeasurePolicy : MultiContentMeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<List<Measurable>>,
        constraints: Constraints,
    ): MeasureResult {
        val (
            headlineMeasurable,
            overlineMeasurable,
            supportingMeasurable,
            leadingMeasurable,
            trailingMeasurable,
        ) =
            measurables
        var currentTotalWidth = 0

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val startPadding = ListItemStartPadding
        val endPadding = ListItemEndPadding
        val horizontalPadding = (startPadding + endPadding).roundToPx()

        // ListItem layout has a cycle in its dependencies which we use
        // intrinsic measurements to break:
        // 1. Intrinsic leading/trailing width
        // 2. Intrinsic supporting height
        // 3. Intrinsic vertical padding
        // 4. Actual leading/trailing measurement
        // 5. Actual supporting measurement
        // 6. Actual vertical padding
        val intrinsicLeadingWidth =
            leadingMeasurable.firstOrNull()?.minIntrinsicWidth(constraints.maxHeight) ?: 0
        val intrinsicTrailingWidth =
            trailingMeasurable.firstOrNull()?.minIntrinsicWidth(constraints.maxHeight) ?: 0
        val intrinsicSupportingWidthConstraint =
            looseConstraints.maxWidth.subtractConstraintSafely(
                intrinsicLeadingWidth + intrinsicTrailingWidth + horizontalPadding,
            )
        val intrinsicSupportingHeight =
            supportingMeasurable
                .firstOrNull()
                ?.minIntrinsicHeight(intrinsicSupportingWidthConstraint) ?: 0
        val intrinsicIsSupportingMultiline =
            isSupportingMultilineHeuristic(intrinsicSupportingHeight)
        val intrinsicListItemType =
            ListItemType(
                hasOverline = overlineMeasurable.firstOrNull() != null,
                hasSupporting = supportingMeasurable.firstOrNull() != null,
                isSupportingMultiline = intrinsicIsSupportingMultiline,
            )
        val intrinsicVerticalPadding = (verticalPadding(intrinsicListItemType) * 2).roundToPx()

        val paddedLooseConstraints =
            looseConstraints.offset(
                horizontal = -horizontalPadding,
                vertical = -intrinsicVerticalPadding,
            )

        val leadingPlaceable = leadingMeasurable.firstOrNull()?.measure(paddedLooseConstraints)
        currentTotalWidth += leadingPlaceable.widthOrZero

        val trailingPlaceable =
            trailingMeasurable
                .firstOrNull()
                ?.measure(paddedLooseConstraints.offset(horizontal = -currentTotalWidth))
        currentTotalWidth += trailingPlaceable.widthOrZero

        var currentTotalHeight = 0

        val headlinePlaceable =
            headlineMeasurable
                .firstOrNull()
                ?.measure(paddedLooseConstraints.offset(horizontal = -currentTotalWidth))
        currentTotalHeight += headlinePlaceable.heightOrZero

        val supportingPlaceable =
            supportingMeasurable
                .firstOrNull()
                ?.measure(
                    paddedLooseConstraints.offset(
                        horizontal = -currentTotalWidth,
                        vertical = -currentTotalHeight,
                    ),
                )
        currentTotalHeight += supportingPlaceable.heightOrZero
        val isSupportingMultiline =
            supportingPlaceable != null &&
                (supportingPlaceable[FirstBaseline] != supportingPlaceable[LastBaseline])

        val overlinePlaceable =
            overlineMeasurable
                .firstOrNull()
                ?.measure(
                    paddedLooseConstraints.offset(
                        horizontal = -currentTotalWidth,
                        vertical = -currentTotalHeight,
                    ),
                )

        val listItemType =
            ListItemType(
                hasOverline = overlinePlaceable != null,
                hasSupporting = supportingPlaceable != null,
                isSupportingMultiline = isSupportingMultiline,
            )
        val topPadding = verticalPadding(listItemType)
        val verticalPadding = topPadding * 2

        val width =
            calculateWidth(
                leadingWidth = leadingPlaceable.widthOrZero,
                trailingWidth = trailingPlaceable.widthOrZero,
                headlineWidth = headlinePlaceable.widthOrZero,
                overlineWidth = overlinePlaceable.widthOrZero,
                supportingWidth = supportingPlaceable.widthOrZero,
                horizontalPadding = horizontalPadding,
                constraints = constraints,
            )
        val height =
            calculateHeight(
                leadingHeight = leadingPlaceable.heightOrZero,
                trailingHeight = trailingPlaceable.heightOrZero,
                headlineHeight = headlinePlaceable.heightOrZero,
                overlineHeight = overlinePlaceable.heightOrZero,
                supportingHeight = supportingPlaceable.heightOrZero,
                listItemType = listItemType,
                verticalPadding = verticalPadding.roundToPx(),
                constraints = constraints,
            )

        return place(
            width = width,
            height = height,
            leadingPlaceable = leadingPlaceable,
            trailingPlaceable = trailingPlaceable,
            headlinePlaceable = headlinePlaceable,
            overlinePlaceable = overlinePlaceable,
            supportingPlaceable = supportingPlaceable,
            isThreeLine = listItemType == ListItemType.ThreeLine,
            startPadding = startPadding.roundToPx(),
            endPadding = endPadding.roundToPx(),
            topPadding = topPadding.roundToPx(),
        )
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<List<IntrinsicMeasurable>>,
        width: Int,
    ): Int = calculateIntrinsicHeight(measurables, width, IntrinsicMeasurable::maxIntrinsicHeight)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<List<IntrinsicMeasurable>>,
        height: Int,
    ): Int = calculateIntrinsicWidth(measurables, height, IntrinsicMeasurable::maxIntrinsicWidth)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<List<IntrinsicMeasurable>>,
        width: Int,
    ): Int = calculateIntrinsicHeight(measurables, width, IntrinsicMeasurable::minIntrinsicHeight)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<List<IntrinsicMeasurable>>,
        height: Int,
    ): Int = calculateIntrinsicWidth(measurables, height, IntrinsicMeasurable::minIntrinsicWidth)

    private fun IntrinsicMeasureScope.calculateIntrinsicWidth(
        measurables: List<List<IntrinsicMeasurable>>,
        height: Int,
        intrinsicMeasure: IntrinsicMeasurable.(height: Int) -> Int,
    ): Int {
        val (
            headlineMeasurable,
            overlineMeasurable,
            supportingMeasurable,
            leadingMeasurable,
            trailingMeasurable,
        ) =
            measurables
        return calculateWidth(
            leadingWidth = leadingMeasurable.firstOrNull()?.intrinsicMeasure(height) ?: 0,
            trailingWidth = trailingMeasurable.firstOrNull()?.intrinsicMeasure(height) ?: 0,
            headlineWidth = headlineMeasurable.firstOrNull()?.intrinsicMeasure(height) ?: 0,
            overlineWidth = overlineMeasurable.firstOrNull()?.intrinsicMeasure(height) ?: 0,
            supportingWidth = supportingMeasurable.firstOrNull()?.intrinsicMeasure(height) ?: 0,
            horizontalPadding = (ListItemStartPadding + ListItemEndPadding).roundToPx(),
            constraints = Constraints(),
        )
    }

    private fun IntrinsicMeasureScope.calculateIntrinsicHeight(
        measurables: List<List<IntrinsicMeasurable>>,
        width: Int,
        intrinsicMeasure: IntrinsicMeasurable.(width: Int) -> Int,
    ): Int {
        val (
            headlineMeasurable,
            overlineMeasurable,
            supportingMeasurable,
            leadingMeasurable,
            trailingMeasurable,
        ) =
            measurables

        var remainingWidth =
            width.subtractConstraintSafely((ListItemStartPadding + ListItemEndPadding).roundToPx())
        val leadingHeight =
            leadingMeasurable.firstOrNull()?.let {
                val height = it.intrinsicMeasure(remainingWidth)
                remainingWidth =
                    remainingWidth.subtractConstraintSafely(
                        it.maxIntrinsicWidth(Constraints.Infinity),
                    )
                height
            } ?: 0
        val trailingHeight =
            trailingMeasurable.firstOrNull()?.let {
                val height = it.intrinsicMeasure(remainingWidth)
                remainingWidth =
                    remainingWidth.subtractConstraintSafely(
                        it.maxIntrinsicWidth(Constraints.Infinity),
                    )
                height
            } ?: 0
        val overlineHeight = overlineMeasurable.firstOrNull()?.intrinsicMeasure(remainingWidth) ?: 0
        val headlineHeight = headlineMeasurable.firstOrNull()?.intrinsicMeasure(remainingWidth) ?: 0
        val supportingHeight =
            supportingMeasurable.firstOrNull()?.intrinsicMeasure(remainingWidth) ?: 0
        val isSupportingMultiline = isSupportingMultilineHeuristic(supportingHeight)
        val listItemType =
            ListItemType(
                hasOverline = overlineHeight > 0,
                hasSupporting = supportingHeight > 0,
                isSupportingMultiline = isSupportingMultiline,
            )

        return calculateHeight(
            leadingHeight = leadingHeight,
            trailingHeight = trailingHeight,
            headlineHeight = headlineHeight,
            overlineHeight = overlineHeight,
            supportingHeight = supportingHeight,
            listItemType = listItemType,
            verticalPadding = (verticalPadding(listItemType) * 2).roundToPx(),
            constraints = Constraints(),
        )
    }
}

private fun calculateWidth(
    leadingWidth: Int,
    trailingWidth: Int,
    headlineWidth: Int,
    overlineWidth: Int,
    supportingWidth: Int,
    horizontalPadding: Int,
    constraints: Constraints,
): Int {
    if (constraints.hasBoundedWidth) {
        return constraints.maxWidth
    }
    // Fallback behavior if width constraints are infinite
    val mainContentWidth = maxOf(headlineWidth, overlineWidth, supportingWidth)
    return horizontalPadding + leadingWidth + mainContentWidth + trailingWidth
}

private fun IntrinsicMeasureScope.calculateHeight(
    leadingHeight: Int,
    trailingHeight: Int,
    headlineHeight: Int,
    overlineHeight: Int,
    supportingHeight: Int,
    listItemType: ListItemType,
    verticalPadding: Int,
    constraints: Constraints,
): Int {
    val defaultMinHeight =
        when (listItemType) {
            ListItemType.OneLine -> ListTokens.ListItemOneLineContainerHeight
            ListItemType.TwoLine -> ListTokens.ListItemTwoLineContainerHeight
            else -> ListTokens.ListItemThreeLineContainerHeight
        }
    val minHeight = max(constraints.minHeight, defaultMinHeight.roundToPx())

    val mainContentHeight = headlineHeight + overlineHeight + supportingHeight

    return max(minHeight, verticalPadding + maxOf(leadingHeight, mainContentHeight, trailingHeight))
        .coerceAtMost(constraints.maxHeight)
}

private fun MeasureScope.place(
    width: Int,
    height: Int,
    leadingPlaceable: Placeable?,
    trailingPlaceable: Placeable?,
    headlinePlaceable: Placeable?,
    overlinePlaceable: Placeable?,
    supportingPlaceable: Placeable?,
    isThreeLine: Boolean,
    startPadding: Int,
    endPadding: Int,
    topPadding: Int,
): MeasureResult =
    layout(width, height) {
        leadingPlaceable?.let {
            it.placeRelative(
                x = startPadding,
                y = if (isThreeLine) topPadding else CenterVertically.align(it.height, height),
            )
        }

        val mainContentX = startPadding + leadingPlaceable.widthOrZero
        val mainContentY =
            if (isThreeLine) {
                topPadding
            } else {
                val totalHeight =
                    headlinePlaceable.heightOrZero +
                        overlinePlaceable.heightOrZero +
                        supportingPlaceable.heightOrZero
                CenterVertically.align(totalHeight, height)
            }
        var currentY = mainContentY

        overlinePlaceable?.placeRelative(mainContentX, currentY)
        currentY += overlinePlaceable.heightOrZero

        headlinePlaceable?.placeRelative(mainContentX, currentY)
        currentY += headlinePlaceable.heightOrZero

        supportingPlaceable?.placeRelative(mainContentX, currentY)

        trailingPlaceable?.let {
            it.placeRelative(
                x = width - endPadding - it.width,
                y = if (isThreeLine) topPadding else CenterVertically.align(it.height, height),
            )
        }
    }

internal val Placeable?.widthOrZero: Int
    get() = this?.width ?: 0

internal val Placeable?.heightOrZero: Int
    get() = this?.height ?: 0

/**
 * Subtracts one value from another, where both values represent constraints used in layout.
 *
 * Notably:
 * - if [this] is [Constraints.Infinity], the result stays [Constraints.Infinity]
 * - the result is coerced to be non-negative
 */
internal fun Int.subtractConstraintSafely(other: Int): Int {
    if (this == Constraints.Infinity) {
        return this
    }
    return (this - other).coerceAtLeast(0)
}

// In the actual layout phase, we can query supporting baselines,
// but for an intrinsic measurement pass, we have to estimate.
private fun Density.isSupportingMultilineHeuristic(estimatedSupportingHeight: Int): Boolean = estimatedSupportingHeight > 30.sp.roundToPx()

private fun verticalPadding(listItemType: ListItemType): Dp =
    when (listItemType) {
        ListItemType.ThreeLine -> ListItemThreeLineVerticalPadding
        else -> ListItemVerticalPadding
    }

@JvmInline
private value class ListItemType private constructor(
    private val lines: Int,
) : Comparable<ListItemType> {
    override operator fun compareTo(other: ListItemType) = lines.compareTo(other.lines)

    companion object {
        /** One line list item */
        val OneLine = ListItemType(1)

        /** Two line list item */
        val TwoLine = ListItemType(2)

        /** Three line list item */
        val ThreeLine = ListItemType(3)

        operator fun invoke(
            hasOverline: Boolean,
            hasSupporting: Boolean,
            isSupportingMultiline: Boolean,
        ): ListItemType =
            when {
                (hasOverline && hasSupporting) || isSupportingMultiline -> ThreeLine
                hasOverline || hasSupporting -> TwoLine
                else -> OneLine
            }
    }
}

@Composable
internal fun ProvideTextStyleFromToken(
    contentColor: Color,
    textStyle: TextStyle,
    content: @Composable () -> Unit,
) {
    val mergedStyle = LocalTextStyle.current.merge(textStyle)
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalTextStyle provides mergedStyle,
        content = content,
    )
}

object ListTokens {
    val ListItemContainerElevation = 0.0.dp
    val ListItemOneLineContainerHeight = 56.0.dp
    val ListItemThreeLineContainerHeight = 88.0.dp
    val ListItemTwoLineContainerHeight = 72.0.dp
}

// Container related defaults
// TODO: Make sure these values stay up to date until replaced with tokens.
internal val ListItemVerticalPadding = 8.dp

internal val ListItemThreeLineVerticalPadding = 12.dp

internal val ListItemStartPadding = 10.dp

internal val ListItemEndPadding = 10.dp

// Icon related defaults.
// TODO: Make sure these values stay up to date until replaced with tokens.
internal val LeadingContentEndPadding = Modifier.padding(end = 10.dp)

// Icon related defaults.
// TODO: Make sure these values stay up to date until replaced with tokens.
internal val SupportingContentTopPadding = Modifier.padding(top = 2.dp)

// Trailing related defaults.
// TODO: Make sure these values stay up to date until replaced with tokens.
internal val TrailingContentStartPadding = Modifier.padding(start = 10.dp)
