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
package com.vitorpamplona.amethyst.commons.ui.markdown

private val ListMarker = Regex("""^([-*+]|\d{1,9}[.)])(\s|$)""")
private val TableDelimiterRow = Regex("""^\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)+\|?\s*$""")
private val MarkdownUrl = Regex("""https?://[^\s()<>\[\]]+""")

/**
 * The URLs written on the top-level lines of [markdown]: lines that are not inside a list, a
 * block quote, a code block, a table or an HTML block. Media at those URLs sits directly in the post's column, so it may
 * run edge to edge with the post's own media; anything nested keeps the indent it is drawn in.
 *
 * A line scan, not a parse: a list item or quote holds every line up to the next blank line
 * (CommonMark's lazy continuation), and an indented line after one stays inside it.
 */
fun markdownTopLevelUrls(markdown: String): Set<String> {
    val urls = mutableSetOf<String>()
    var inFence = false
    var inNestedBlock = false
    // The URLs of the previous line, taken back when the next line turns out to be a table's delimiter row.
    var previousLineUrls = emptyList<String>()

    markdown.lineSequence().forEach { line ->
        val trimmed = line.trimStart()

        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inFence = !inFence
            return@forEach
        }
        if (inFence) return@forEach

        if (trimmed.isEmpty()) {
            inNestedBlock = false
            previousLineUrls = emptyList()
            return@forEach
        }

        // A table runs from its header line, just above the delimiter row, to the next blank line.
        if (TableDelimiterRow.matches(trimmed)) {
            urls.removeAll(previousLineUrls.toSet())
            inNestedBlock = true
            return@forEach
        }

        val indented = line.length - trimmed.length >= 2
        if (trimmed.startsWith(">") || trimmed.startsWith("|") || trimmed.startsWith("<") || ListMarker.containsMatchIn(trimmed)) {
            inNestedBlock = true
            return@forEach
        }
        if (inNestedBlock || indented) {
            previousLineUrls = emptyList()
            return@forEach
        }

        previousLineUrls = MarkdownUrl.findAll(trimmed).map { it.value.trimEnd('.', ',', ';', ':', '!', '?') }.toList()
        urls.addAll(previousLineUrls)
    }

    return urls
}
