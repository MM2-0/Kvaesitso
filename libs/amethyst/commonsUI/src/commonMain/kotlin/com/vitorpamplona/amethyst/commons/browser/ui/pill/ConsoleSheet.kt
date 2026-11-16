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
package com.vitorpamplona.amethyst.commons.browser.ui.pill

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_pill_close
import com.vitorpamplona.amethyst.commons.resources.browser_pill_console
import com.vitorpamplona.amethyst.commons.resources.browser_pill_console_all
import com.vitorpamplona.amethyst.commons.resources.browser_pill_console_clear
import com.vitorpamplona.amethyst.commons.resources.browser_pill_console_copy
import com.vitorpamplona.amethyst.commons.resources.browser_pill_console_empty
import com.vitorpamplona.amethyst.commons.resources.browser_pill_console_errors
import com.vitorpamplona.amethyst.commons.resources.browser_pill_console_warnings
import com.vitorpamplona.amethyst.commons.ui.stringRes

/** Which console lines are shown. */
enum class ConsoleFilter { ALL, ERRORS, WARNINGS }

/**
 * The developer console as a bottom sheet: a grab handle, the title with All / Errors / Warnings filter
 * chips (with counts), Copy and Clear, then the log — one row per line with a level-coloured stripe, the
 * message in monospace and `file:line` muted. Long-press a row to copy it.
 */
@Composable
fun ConsoleSheet(
    lines: List<ConsoleLine>,
    onCopy: (List<ConsoleLine>) -> Unit,
    onClear: () -> Unit,
    onCopyLine: (ConsoleLine) -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 320.dp,
    initialFilter: ConsoleFilter = ConsoleFilter.ALL,
    onClose: (() -> Unit)? = null,
) {
    var filter by remember { mutableStateOf(initialFilter) }
    val errors = lines.count { it.level == ConsoleLine.Level.ERROR }
    val warnings = lines.count { it.level == ConsoleLine.Level.WARNING }
    val shown =
        when (filter) {
            ConsoleFilter.ALL -> lines
            ConsoleFilter.ERRORS -> lines.filter { it.level == ConsoleLine.Level.ERROR }
            ConsoleFilter.WARNINGS -> lines.filter { it.level == ConsoleLine.Level.WARNING }
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        border = PillDefaults.hairline(),
    ) {
        Column(Modifier.heightIn(max = maxHeight)) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            }
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringRes(Res.string.browser_pill_console), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { onCopy(shown) }, enabled = shown.isNotEmpty()) {
                    Icon(MaterialSymbols.ContentCopy, contentDescription = stringRes(Res.string.browser_pill_console_copy), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onClear, enabled = lines.isNotEmpty()) {
                    Icon(MaterialSymbols.Delete, contentDescription = stringRes(Res.string.browser_pill_console_clear), modifier = Modifier.size(20.dp))
                }
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.browser_pill_close), modifier = Modifier.size(20.dp))
                    }
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConsoleChip(stringRes(Res.string.browser_pill_console_all), lines.size, filter == ConsoleFilter.ALL) { filter = ConsoleFilter.ALL }
                ConsoleChip(stringRes(Res.string.browser_pill_console_errors), errors, filter == ConsoleFilter.ERRORS, MaterialTheme.colorScheme.error) { filter = ConsoleFilter.ERRORS }
                ConsoleChip(stringRes(Res.string.browser_pill_console_warnings), warnings, filter == ConsoleFilter.WARNINGS, warningColor()) { filter = ConsoleFilter.WARNINGS }
            }
            if (shown.isEmpty()) {
                Text(
                    stringRes(Res.string.browser_pill_console_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            } else {
                LazyColumn(Modifier.padding(top = 6.dp, bottom = 8.dp)) {
                    items(shown) { line -> ConsoleRowItem(line) { onCopyLine(line) } }
                }
            }
        }
    }
}

@Composable
private fun ConsoleChip(
    label: String,
    count: Int,
    selected: Boolean,
    dot: Color? = null,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text("$label  $count") },
        leadingIcon =
            dot?.let { color ->
                { Box(Modifier.size(8.dp).clip(CircleShape).background(color)) }
            },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer),
    )
}

@Composable
private fun warningColor(): Color = if (MaterialTheme.colorScheme.surface.luminanceBelowHalf()) Color(0xFFFFB74D) else Color(0xFFB45309)

private fun Color.luminanceBelowHalf(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConsoleRowItem(
    line: ConsoleLine,
    onLongPress: () -> Unit,
) {
    val color =
        when (line.level) {
            ConsoleLine.Level.ERROR -> MaterialTheme.colorScheme.error
            ConsoleLine.Level.WARNING -> warningColor()
            ConsoleLine.Level.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        }
    val background =
        when (line.level) {
            ConsoleLine.Level.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            ConsoleLine.Level.WARNING -> warningColor().copy(alpha = 0.10f)
            else -> Color.Transparent
        }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(background)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (line.level == ConsoleLine.Level.LOG) Color.Transparent else color))
        Spacer(Modifier.width(13.dp))
        Text(
            line.message,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f).padding(vertical = 5.dp),
        )
        if (line.source.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                line.source.substringAfterLast('/') + ":" + line.line,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 5.dp).width(96.dp),
            )
        }
    }
}
