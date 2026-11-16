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
package com.vitorpamplona.amethyst.commons.nip23LongContent.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.markdown_toolbar_blockquote
import com.vitorpamplona.amethyst.commons.resources.markdown_toolbar_bold
import com.vitorpamplona.amethyst.commons.resources.markdown_toolbar_bullet_list
import com.vitorpamplona.amethyst.commons.resources.markdown_toolbar_horizontal_rule
import com.vitorpamplona.amethyst.commons.resources.markdown_toolbar_image
import com.vitorpamplona.amethyst.commons.resources.markdown_toolbar_inline_code
import com.vitorpamplona.amethyst.commons.resources.markdown_toolbar_italic
import com.vitorpamplona.amethyst.commons.resources.markdown_toolbar_link
import com.vitorpamplona.amethyst.commons.resources.markdown_toolbar_numbered_list
import com.vitorpamplona.amethyst.commons.resources.markdown_toolbar_strikethrough
import com.vitorpamplona.amethyst.commons.resources.markdown_toolbar_task_list
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * Markdown toolbar with Material icons, grouped formatting buttons, and active state.
 * Uses [MarkdownEditorState] for selection-aware toggle behavior.
 *
 * Buttons use `focusProperties { canFocus = false }` to prevent stealing focus
 * from the editor TextField, preserving the user's text selection.
 */
@Composable
fun MarkdownToolbar(
    state: MarkdownEditorState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // --- Headings ---
        ToolbarButton(label = "H1", active = state.headingLevel == 1) { state.setHeading(if (state.headingLevel == 1) null else 1) }
        ToolbarButton(label = "H2", active = state.headingLevel == 2) { state.setHeading(if (state.headingLevel == 2) null else 2) }
        ToolbarButton(label = "H3", active = state.headingLevel == 3) { state.setHeading(if (state.headingLevel == 3) null else 3) }

        Separator()

        // --- Inline formatting ---
        ToolbarIconButton(MaterialSymbols.FormatBold, stringRes(Res.string.markdown_toolbar_bold), state.isBold) { state.toggleBold() }
        ToolbarIconButton(MaterialSymbols.FormatItalic, stringRes(Res.string.markdown_toolbar_italic), state.isItalic) { state.toggleItalic() }
        ToolbarIconButton(MaterialSymbols.FormatStrikethrough, stringRes(Res.string.markdown_toolbar_strikethrough), state.isStrikethrough) { state.toggleStrikethrough() }
        ToolbarIconButton(MaterialSymbols.Code, stringRes(Res.string.markdown_toolbar_inline_code), state.isInlineCode) { state.toggleInlineCode() }

        Separator()

        // --- Lists ---
        ToolbarIconButton(MaterialSymbols.AutoMirrored.FormatListBulleted, stringRes(Res.string.markdown_toolbar_bullet_list), state.isUnorderedList) { state.toggleUnorderedList() }
        ToolbarIconButton(MaterialSymbols.FormatListNumbered, stringRes(Res.string.markdown_toolbar_numbered_list), state.isOrderedList) { state.toggleOrderedList() }
        ToolbarIconButton(MaterialSymbols.Checklist, stringRes(Res.string.markdown_toolbar_task_list), state.isTaskList) { state.toggleTaskList() }

        Separator()

        // --- Block elements ---
        ToolbarIconButton(MaterialSymbols.FormatQuote, stringRes(Res.string.markdown_toolbar_blockquote), state.isBlockquote) { state.toggleBlockquote() }
        ToolbarButton(label = "```", active = false) { state.toggleCodeBlock() }
        ToolbarIconButton(MaterialSymbols.HorizontalRule, stringRes(Res.string.markdown_toolbar_horizontal_rule), false) { state.insertHorizontalRule() }

        Separator()

        // --- Insert ---
        ToolbarIconButton(MaterialSymbols.Link, stringRes(Res.string.markdown_toolbar_link), false) { state.insertLink() }
        ToolbarIconButton(MaterialSymbols.Image, stringRes(Res.string.markdown_toolbar_image), false) { state.insertImage() }
    }
}

@Composable
private fun Separator() {
    VerticalDivider(
        modifier = Modifier.height(24.dp).padding(horizontal = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun ToolbarIconButton(
    icon: MaterialSymbol,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        if (active) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = CircleShape,
        modifier =
            Modifier
                .size(32.dp)
                .focusProperties { canFocus = false },
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        if (active) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = CircleShape,
        modifier =
            Modifier
                .size(32.dp)
                .focusProperties { canFocus = false },
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = contentColor,
        )
    }
}
