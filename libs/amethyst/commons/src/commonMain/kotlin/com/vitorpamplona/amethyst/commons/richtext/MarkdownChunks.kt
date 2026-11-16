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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.cache.ConcurrentLruCache

/** A heading of a Markdown document: its level (1 for `#`), its text and the anchor that links to it. */
@Immutable
data class MarkdownHeading(
    val level: Int,
    val text: String,
    val anchor: String,
)

/** A piece of a Markdown document the renderer draws on its own. */
@Immutable
sealed interface MarkdownChunk {
    /** Markdown prose. [anchor] is the anchor of the heading it opens with, if any. */
    @Immutable
    data class Prose(
        val markdown: String,
        val anchor: String?,
    ) : MarkdownChunk

    /** A top-level fenced code block, drawn by the app so it can be highlighted. */
    @Immutable
    data class Code(
        val language: String?,
        val code: String,
    ) : MarkdownChunk
}

/**
 * Splits Markdown into the pieces the app draws itself: top-level fenced code blocks, so they can be
 * syntax highlighted (the Markdown library only draws plain code), and, when asked, a piece per
 * heading, so a reader can scroll to a section. Reference-link definitions (`[Blossom]: https://…`)
 * are repeated at the end of every prose piece, since each piece is parsed on its own and they draw
 * nothing.
 *
 * Only what sits at the top level counts: a fence or heading indented into a list stays inside the
 * prose around it. The scan is line-based and linear.
 */
object MarkdownChunks {
    private val HIGHLIGHTED = setOf("json", "jsonc", "json5", "jsonl", "ndjson", "js", "javascript", "ts", "typescript", "yaml", "yml")

    /** True when the code block gets the JSON-ish highlighter: its language, or an unlabelled block that opens like JSON. */
    fun isHighlightable(
        language: String?,
        code: String,
    ): Boolean {
        val lang = language?.lowercase()?.ifBlank { null }
        if (lang != null) return lang in HIGHLIGHTED
        val start = code.trimStart()
        return start.startsWith('{') || start.startsWith('[')
    }

    /** The document's headings, outside code, with the anchors [split] gives their pieces. */
    fun headings(markdown: String): List<MarkdownHeading> {
        val result = ArrayList<MarkdownHeading>()
        val seen = HashMap<String, Int>()
        scan(markdown.lines()) { line ->
            if (line is Line.Heading) result.add(MarkdownHeading(line.level, line.text, uniqueAnchor(line.text, seen)))
        }
        return result
    }

    /**
     * The pieces of [markdown], or null when it needs no splitting: no highlightable code block
     * and, unless [atHeadings], no heading to stop at. Null too for a document with footnotes,
     * whose definitions would repeat in every piece.
     */
    fun split(
        markdown: String,
        atHeadings: Boolean,
    ): List<MarkdownChunk>? {
        // Most notes have no fence: without one and without anchors to place, nothing to split.
        if (!atHeadings && !markdown.contains("```") && !markdown.contains("~~~")) return null

        val lines = markdown.lines()
        val parsed = ArrayList<Line>(lines.size)
        scan(lines) { parsed.add(it) }

        if (parsed.any { it is Line.Text && FOOTNOTE_DEF.containsMatchIn(it.raw) }) return null

        val hasHeading = atHeadings && parsed.any { it is Line.Heading }
        val hasCode = parsed.any { it is Line.Fence && isHighlightable(it.language, it.code) }
        if (!hasHeading && !hasCode) return null

        val definitions =
            parsed
                .filter { it is Line.Text && REFERENCE_DEF.containsMatchIn(it.raw) }
                .joinToString("\n") { (it as Line.Text).raw }

        val chunks = ArrayList<MarkdownChunk>()
        val seen = HashMap<String, Int>()
        val prose = StringBuilder()
        var anchor: String? = null

        fun flush() {
            val text = prose.toString().trim('\n')
            if (text.isNotBlank() || anchor != null) {
                val withDefs = if (definitions.isNotEmpty() && text.isNotBlank()) text + "\n\n" + definitions else text
                chunks.add(MarkdownChunk.Prose(withDefs, anchor))
            }
            prose.clear()
            anchor = null
        }

        for (line in parsed) {
            when (line) {
                is Line.Fence -> {
                    flush()
                    chunks.add(MarkdownChunk.Code(line.language, line.code))
                }

                is Line.Heading -> {
                    if (atHeadings) {
                        flush()
                        anchor = uniqueAnchor(line.text, seen)
                    }
                    prose.append(line.raw).append('\n')
                }

                is Line.Text -> {
                    // Definitions are appended to every piece; leaving them in place too would be harmless,
                    // but a piece made only of definitions would draw as an empty block.
                    if (!REFERENCE_DEF.containsMatchIn(line.raw)) prose.append(line.raw).append('\n')
                }
            }
        }
        flush()
        return chunks
    }

    private class CachedSplit(
        val markdown: String,
        val atHeadings: Boolean,
        val chunks: List<MarkdownChunk>?,
    )

    private val splitCache = ConcurrentLruCache<Int, CachedSplit>(200)

    /**
     * [split], remembered across compositions: a feed card that scrolls back into view re-renders
     * its Markdown without scanning it again.
     */
    fun cachedSplit(
        markdown: String,
        atHeadings: Boolean,
    ): List<MarkdownChunk>? {
        val key = 31 * markdown.hashCode() + atHeadings.hashCode()
        splitCache.get(key)?.takeIf { it.markdown == markdown && it.atHeadings == atHeadings }?.let { return it.chunks }
        val chunks = split(markdown, atHeadings)
        splitCache.put(key, CachedSplit(markdown, atHeadings, chunks))
        return chunks
    }

    /** GitHub's anchor for a heading: lower case, punctuation dropped, spaces to hyphens. */
    fun anchorOf(text: String): String {
        val plain = INLINE_LINK.replace(text) { it.groupValues[1] }
        val sb = StringBuilder(plain.length)
        for (c in plain.lowercase()) {
            when {
                c.isLetterOrDigit() || c == '-' || c == '_' -> sb.append(c)
                c == ' ' -> sb.append('-')
            }
        }
        return sb.toString()
    }

    private fun uniqueAnchor(
        text: String,
        seen: MutableMap<String, Int>,
    ): String {
        val base = anchorOf(text)
        val count = seen[base]
        seen[base] = (count ?: 0) + 1
        return if (count == null) base else "$base-$count"
    }

    private sealed interface Line {
        class Text(
            val raw: String,
        ) : Line

        class Heading(
            val raw: String,
            val level: Int,
            val text: String,
        ) : Line

        class Fence(
            val language: String?,
            val code: String,
        ) : Line
    }

    private val ATX = Regex("^(#{1,6})(?:[ \\t]+(.*?))?[ \\t]*#*[ \\t]*$")
    private val SETEXT = Regex("^ {0,3}(=+|-+)[ \\t]*$")
    private val FENCE_OPEN = Regex("^( {0,3})(`{3,}|~{3,})[ \\t]*([^`\\s]*)[^`]*$")
    private val BLOCK_START = Regex("^\\s*([-*+>|]|\\d+[.)])(\\s|$)")
    private val REFERENCE_DEF = Regex("^ {0,3}\\[[^\\]^][^\\]]*\\]:\\s*\\S")
    private val FOOTNOTE_DEF = Regex("^ {0,3}\\[\\^[^\\]]+\\]:")
    private val INLINE_LINK = Regex("!?\\[([^\\]]*)\\](\\([^)]*\\)|\\[[^\\]]*\\])")

    /**
     * Classifies each line. A fence opened at column 0 becomes one [Line.Fence] holding its code;
     * one indented by 1 to 3 spaces (usually inside a list) stays text, but its lines are still
     * skipped for headings.
     */
    private inline fun scan(
        lines: List<String>,
        emit: (Line) -> Unit,
    ) {
        var i = 0
        var previousBlank = true
        while (i < lines.size) {
            val raw = lines[i]
            val fence = FENCE_OPEN.matchEntire(raw)
            if (fence != null) {
                val indent = fence.groupValues[1].length
                val marker = fence.groupValues[2]
                val close = findFenceClose(lines, i + 1, marker)
                if (indent == 0) {
                    val code = lines.subList(i + 1, close).joinToString("\n")
                    emit(Line.Fence(fence.groupValues[3].ifBlank { null }, code))
                } else {
                    for (j in i..minOf(close, lines.size - 1)) emit(Line.Text(lines[j]))
                }
                i = close + 1
                previousBlank = false
                continue
            }

            val atx = if (raw.startsWith('#')) ATX.matchEntire(raw) else null
            if (atx != null) {
                emit(Line.Heading(raw, atx.groupValues[1].length, plainHeading(atx.groupValues[2])))
                i++
                previousBlank = false
                continue
            }

            // Setext: one line of paragraph text right under a blank line, then `===` or `---`.
            val next = lines.getOrNull(i + 1)
            if (previousBlank && raw.isNotBlank() && !raw.startsWith(' ') && next != null && SETEXT.matches(next) && !BLOCK_START.containsMatchIn(raw)) {
                val level = if (next.trim().startsWith('=')) 1 else 2
                emit(Line.Heading(raw + "\n" + next, level, plainHeading(raw)))
                i += 2
                previousBlank = false
                continue
            }

            emit(Line.Text(raw))
            previousBlank = raw.isBlank()
            i++
        }
    }

    /** The index of the line closing the fence opened by [marker], or the end of the document. */
    private fun findFenceClose(
        lines: List<String>,
        from: Int,
        marker: String,
    ): Int {
        val char = marker[0]
        for (j in from until lines.size) {
            val t = lines[j].trim()
            if (t.length >= marker.length && t.all { it == char }) return j
        }
        return lines.size
    }

    private fun plainHeading(text: String): String =
        INLINE_LINK
            .replace(text) { it.groupValues[1] }
            .replace("`", "")
            .replace("**", "")
            .trim()
}
