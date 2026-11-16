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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

private val MenuShape = RoundedCornerShape(20.dp)
private val MenuSectionShape = RoundedCornerShape(14.dp)
private val MenuItemShape = RoundedCornerShape(10.dp)

/** Space between the menu's sides and its sections: the 8dp Material already leaves above and below them. */
private val MenuSidePadding = 8.dp

/** Space between sections. */
private val MenuSectionGap = 6.dp

/** Space between a section's edge and its rows. */
private val MenuSectionPadding = 4.dp

/**
 * How far a menu's first row sits below the menu's top edge: Material's 8dp and the section's
 * own padding. A submenu offsets by it to line its first row up with the row that opened it.
 */
val RoundedMenuFirstRowInset = 8.dp + MenuSectionPadding

/**
 * A dropdown in the look of the app's action dialogs ([M3ActionDialog]): a rounded container
 * holding rounded [RoundedMenuSection]s, instead of Material's 4dp menu with flat dividers.
 */
@Composable
fun RoundedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        properties = properties,
        shape = MenuShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = MenuSidePadding),
            verticalArrangement = Arrangement.spacedBy(MenuSectionGap),
        ) {
            content()
        }
    }
}

/** One group of related rows in a [RoundedDropdownMenu], as [M3ActionSection] is in the dialog. */
@Composable
fun RoundedMenuSection(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MenuSectionShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(MenuSectionPadding)) {
            content()
        }
    }
}

/** Rounds a menu row, so its hover and press highlight is a pill inside the section. */
val RoundedMenuItemModifier = Modifier.clip(MenuItemShape)

/**
 * The container of a [RoundedDropdownMenu], for a menu placed by its own Popup: a submenu,
 * which Material's DropdownMenu cannot place beside its row (its offset shifts both of the sides
 * it tries, so it never flips to the left at the window's edge). Sized and padded as Material
 * sizes its menus.
 */
@Composable
fun RoundedMenuSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MenuShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier =
                Modifier
                    // Material's own 112–280dp item bounds, plus this container's side and section padding.
                    .widthIn(min = 112.dp, max = 280.dp + (MenuSidePadding + MenuSectionPadding) * 2)
                    .width(IntrinsicSize.Max)
                    .padding(horizontal = MenuSidePadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(MenuSectionGap),
        ) {
            content()
        }
    }
}

/**
 * Places a submenu beside the row that opened it ([anchorBounds]): after it when it fits, else
 * before it, its first row level with that row ([firstRowInsetPx] above it) and kept inside the
 * window.
 */
class SubmenuPositionProvider(
    private val firstRowInsetPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val width = popupContentSize.width
        val after = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right else anchorBounds.left - width
        val before = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left - width else anchorBounds.right
        val x =
            when {
                after >= 0 && after + width <= windowSize.width -> after
                before >= 0 && before + width <= windowSize.width -> before
                else -> (windowSize.width - width).coerceAtLeast(0)
            }
        val y = (anchorBounds.top - firstRowInsetPx).coerceAtMost(windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
