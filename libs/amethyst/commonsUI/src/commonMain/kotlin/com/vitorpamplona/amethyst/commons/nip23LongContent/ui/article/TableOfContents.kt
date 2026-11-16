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
package com.vitorpamplona.amethyst.commons.nip23LongContent.ui.article

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.+)")
private val TRAILING_HASHES_REGEX = Regex("#+$")

data class TocEntry(
    val level: Int,
    val text: String,
    val index: Int,
)

/**
 * Extracts table of contents entries from markdown content.
 * Parses ATX headings (# H1, ## H2, etc.), skipping code blocks.
 */
fun extractTableOfContents(markdown: String): List<TocEntry> {
    val entries = mutableListOf<TocEntry>()
    var inCodeBlock = false
    var headingIndex = 0

    markdown.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            return@forEach
        }
        if (inCodeBlock) return@forEach

        val match = HEADING_REGEX.find(trimmed)
        if (match != null) {
            val level = match.groupValues[1].length
            val text =
                match.groupValues[2]
                    .trim()
                    .replace(TRAILING_HASHES_REGEX, "")
                    .trim()
            if (text.isNotEmpty() && level <= 3) {
                entries.add(TocEntry(level = level, text = text, index = headingIndex))
            }
            headingIndex++
        }
    }
    return entries
}

@Composable
fun TableOfContents(
    entries: List<TocEntry>,
    activeEntryIndex: Int?,
    onEntryClick: (TocEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier =
            modifier
                .width(240.dp)
                .verticalScroll(scrollState)
                .padding(vertical = 16.dp),
    ) {
        entries.forEach { entry ->
            val isActive = entry.index == activeEntryIndex
            val accentColor = MaterialTheme.colorScheme.primary

            Text(
                text = entry.text,
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color =
                            if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .clickable { onEntryClick(entry) }
                        .padding(
                            start = ((entry.level - 1) * 16).dp,
                            top = 4.dp,
                            bottom = 4.dp,
                            end = 8.dp,
                        ).then(
                            if (isActive) {
                                Modifier.drawBehind {
                                    drawLine(
                                        color = accentColor,
                                        start = Offset(0f, 0f),
                                        end = Offset(0f, size.height),
                                        strokeWidth = 3.dp.toPx(),
                                    )
                                }
                            } else {
                                Modifier
                            },
                        ),
            )
        }
    }
}
