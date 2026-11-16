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
package com.vitorpamplona.amethyst.commons.richtext

/**
 * True when [segment] renders as a full-width block (a link-preview card, an image, a video,
 * a PDF) instead of an inline word. Media always draws as a block; a [LinkSegment] only does
 * when previews are on, otherwise it is an inline clickable URL.
 */
fun rendersAsBlock(
    segment: Segment,
    canPreview: Boolean,
): Boolean =
    when (segment) {
        is ImageSegment, is VideoSegment, is PdfSegment, is Base64Segment -> true
        is LinkSegment -> canPreview
        else -> false
    }

/** A paragraph that renders as an empty line: no words, or only blank text. */
fun ParagraphState.isBlankLine(): Boolean = words.all { it is RegularTextSegment && it.segmentText.isBlank() }

/**
 * Drops the one blank line that directly follows a paragraph ending in a block (see
 * [rendersAsBlock]), and the one that directly precedes a paragraph starting with one. A block
 * keeps its own even margin above and below, so the `\n\n` an author types to set a URL apart
 * from its sentences would otherwise add an empty text line on that side only: a picture in the
 * middle of a post sat three times farther from the paragraph above it than from the one below.
 * Standalone image paragraphs get the same treatment after them in [GalleryParser].
 *
 * Only one blank line goes on each side: a second one the author typed still shows.
 *
 * Returns [paragraphs] itself when nothing is dropped.
 */
fun dropBlankLinesAroundBlocks(
    paragraphs: List<ParagraphState>,
    canPreview: Boolean,
): List<ParagraphState> {
    var result: ArrayList<ParagraphState>? = null

    for (i in paragraphs.indices) {
        val paragraph = paragraphs[i]
        val drop =
            paragraph.isBlankLine() &&
                (endsWithBlock(paragraphs.getOrNull(i - 1), canPreview) || startsWithBlock(paragraphs.getOrNull(i + 1), canPreview))

        if (drop) {
            if (result == null) result = ArrayList<ParagraphState>(paragraphs.size).apply { addAll(paragraphs.subList(0, i)) }
        } else {
            result?.add(paragraph)
        }
    }

    return result ?: paragraphs
}

private fun endsWithBlock(
    paragraph: ParagraphState?,
    canPreview: Boolean,
): Boolean = paragraph != null && !paragraph.isBlankLine() && rendersAsBlock(paragraph.words.last(), canPreview)

private fun startsWithBlock(
    paragraph: ParagraphState?,
    canPreview: Boolean,
): Boolean = paragraph != null && !paragraph.isBlankLine() && rendersAsBlock(paragraph.words.first(), canPreview)
