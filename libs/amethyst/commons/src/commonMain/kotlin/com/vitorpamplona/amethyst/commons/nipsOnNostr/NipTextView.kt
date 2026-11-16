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
package com.vitorpamplona.amethyst.commons.nipsOnNostr

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.richtext.MarkdownChunks
import com.vitorpamplona.amethyst.commons.richtext.MarkdownHeading
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.kinds.KindNames
import com.vitorpamplona.quartz.utils.cache.ConcurrentLruCache

/** A kind a NIP defines: its number and, when the `k` tag carries one, the NIP's own name for it. */
@Immutable
data class NipDefinedKind(
    val kind: Int,
    val label: String?,
)

/**
 * What the screens draw for a NIP, worked out once per document: a kind-30817 NIP published on
 * Nostr ([of]) or an official one read from the nips repo ([ofMarkdown]).
 *
 * [body] is the Markdown to render: the content without the leading headings that repeat
 * [title] (NostrHub and the nips repo both open with them), with relative links to other NIPs
 * (`44.md`) pointed at the nips repo, and with plain `NIP-44` mentions turned into links. The app
 * opens both kinds of link in its own reader. [sections] are the body's headings, for a table of
 * contents. [preview] is the opening prose as plain text, for the feed card when the NIP has no
 * summary.
 */
@Immutable
class NipTextView(
    val number: String?,
    val title: String?,
    val summary: String?,
    val status: String?,
    val flags: List<String>,
    val kinds: List<NipDefinedKind>,
    val body: String,
    val preview: String,
    val sections: List<MarkdownHeading>,
) {
    /** True when a table of contents helps: a long body with a few sections. */
    val hasContents: Boolean get() = sections.count { it.level <= 3 } >= MIN_SECTIONS && body.length > MIN_BODY_FOR_CONTENTS

    companion object {
        private const val MIN_SECTIONS = 3
        private const val MIN_BODY_FOR_CONTENTS = 3000

        private class Cached(
            val event: NipTextEvent,
            val view: NipTextView,
        )

        private val cache = ConcurrentLruCache<String, Cached>(64)

        /**
         * The view of [event], built once and shared: the feed card, the thread header and the
         * thread body all ask for it, and building it takes several passes over the whole text.
         */
        fun of(event: NipTextEvent): NipTextView {
            cache.get(event.id)?.let { cached ->
                val old = cached.event
                if (old === event || (old.content == event.content && old.tags.contentDeepEquals(event.tags))) return cached.view
            }
            return build(event).also { cache.put(event.id, Cached(event, it)) }
        }

        private fun build(event: NipTextEvent): NipTextView {
            val title = event.title()?.trim()?.ifBlank { null }
            val stripped = stripLeadingTitle(event.content, title)
            val status = event.tagValue("status")?.trim()?.ifBlank { null }
            val body = linkNipMentions(linkRelativeNips(stripped.body), stripped.number)
            return NipTextView(
                number = stripped.number,
                title = title ?: stripped.firstHeading,
                // The tag, not NipTextEvent.summary(): that one guesses from the content by
                // skipping its first line, which drops the real opening of a NIP with no heading.
                summary = event.tagValue("summary")?.trim()?.ifBlank { null },
                status = status,
                flags = stripped.flags.filterNot { it.equals(status, ignoreCase = true) },
                kinds = definedKinds(event.tags),
                body = body,
                preview = plainPreview(stripped.body),
                sections = MarkdownChunks.headings(body),
            )
        }

        /** An official NIP, from its file in the nips repo. Its kinds come from the kind registry. */
        fun ofMarkdown(
            number: String,
            markdown: String,
        ): NipTextView {
            val stripped = stripLeadingTitle(markdown, null)
            val body = linkNipMentions(linkRelativeNips(stripped.body), number)
            return NipTextView(
                number = stripped.number ?: number,
                title = stripped.firstHeading,
                summary = null,
                status = null,
                flags = stripped.flags,
                kinds = officialKinds(number),
                body = body,
                preview = plainPreview(stripped.body),
                sections = MarkdownChunks.headings(body),
            )
        }
    }
}

// The registry inverted once: official NIP number to the kinds it defines, in kind order.
private val kindsByOfficialNip: Map<String, List<NipDefinedKind>> by lazy {
    val byNip = HashMap<String, MutableList<Int>>()
    KindNames.names.forEach { (kind, info) ->
        val nip = info.nip?.let { NipLinks.normalize(it) } ?: return@forEach
        byNip.getOrPut(nip) { ArrayList() }.add(kind)
    }
    byNip.mapValues { (_, kinds) -> kinds.sorted().map { NipDefinedKind(it, null) } }
}

/** The kinds the registry attributes to official NIP [number], in kind order. */
fun officialKinds(number: String): List<NipDefinedKind> = kindsByOfficialNip[number] ?: emptyList()

private fun NipTextEvent.tagValue(name: String): String? {
    for (tag in tags) {
        if (tag.size > 1 && tag[0] == name) return tag[1]
    }
    return null
}

/** The `k` tags, once per kind, keeping the first non-blank name any of them gives it. */
internal fun definedKinds(tags: Array<Array<String>>): List<NipDefinedKind> {
    val byKind = LinkedHashMap<Int, String?>()
    for (tag in tags) {
        if (tag.size < 2 || tag[0] != "k") continue
        val kind = tag[1].trim().toIntOrNull() ?: continue
        val label = tag.getOrNull(2)?.trim()?.ifBlank { null }
        if (byKind[kind] == null) byKind[kind] = label
    }
    return byKind.map { (kind, label) -> NipDefinedKind(kind, label) }
}

internal class StrippedNip(
    val number: String?,
    val firstHeading: String?,
    val flags: List<String>,
    val body: String,
)

// Real NIP numbers are two hex characters; a heading like `NIP-AMB` is a name, so it stays a heading.
private val NIP_NUMBER = Regex("^NIP[- ]?([0-9A-Fa-f]{2})$", RegexOption.IGNORE_CASE)
private val ATX_HEADING = Regex("^#{1,6}\\s+(.*?)\\s*#*\\s*$")
private val SETEXT_UNDERLINE = Regex("^\\s*(=+|-+)\\s*$")

/**
 * Drops the headings a NIP opens with when they repeat what the header already shows: a
 * `NIP-XX` heading (its number goes to [StrippedNip.number]) and a heading equal to [title].
 * Both ATX (`# Title`) and setext (`Title` over `=====`) headings count. Any other opening
 * heading stays, since it is part of the text. A status line right under them
 * (`` `draft` `optional` ``) moves to [StrippedNip.flags].
 */
internal fun stripLeadingTitle(
    content: String,
    title: String?,
): StrippedNip {
    val lines = content.lines()
    var i = 0
    var number: String? = null
    var firstHeading: String? = null

    fun skipBlank() {
        while (i < lines.size && lines[i].isBlank()) i++
    }

    // Two passes at most: `NIP-XX` then the title, as the nips repo writes them.
    repeat(2) {
        skipBlank()
        if (i >= lines.size) return@repeat
        val line = lines[i]
        val atx = ATX_HEADING.find(line.trim())
        val (text, consumed) =
            when {
                atx != null -> atx.groupValues[1].trim() to 1
                i + 1 < lines.size && line.isNotBlank() && SETEXT_UNDERLINE.matches(lines[i + 1]) -> line.trim() to 2
                else -> return@repeat
            }

        val nip = NIP_NUMBER.find(text)
        when {
            nip != null && number == null -> {
                number = nip.groupValues[1].uppercase()
                i += consumed
            }

            title != null && text.equals(title, ignoreCase = true) -> {
                i += consumed
            }

            title == null && firstHeading == null && number != null -> {
                // No title tag: the heading after `NIP-XX` is the title, so the header shows it.
                firstHeading = text
                i += consumed
            }

            else -> {
                return@repeat
            }
        }
    }

    skipBlank()

    // The nips repo's status line, `draft` `optional`, right under the headings.
    // Only single words count, so an opening line of inline code (`kind:1`) stays in the text.
    var flags = emptyList<String>()
    if (i < lines.size && STATUS_LINE.matches(lines[i].trim())) {
        val words = STATUS_FLAG.findAll(lines[i]).map { it.groupValues[1].trim() }.toList()
        if (words.all { FLAG_WORD.matches(it) }) {
            flags = words
            i++
            skipBlank()
        }
    }

    return StrippedNip(number, firstHeading, flags, lines.drop(i).joinToString("\n").trimEnd())
}

// `](44.md)`, `](44.md#section)` and the reference form `[NIP-44]: 44.md`.
private val RELATIVE_NIP_LINK = Regex("(\\]\\(|^\\s{0,3}\\[[^\\]]+\\]:\\s*)(\\.?/?)([0-9A-Fa-f]{2}\\.md(?:#[^)\\s]*)?)", RegexOption.MULTILINE)

/** Points relative links to other NIPs (`[NIP-44](44.md)`) at the nips repo, where they resolve. */
internal fun linkRelativeNips(markdown: String): String = RELATIVE_NIP_LINK.replace(markdown) { m -> m.groupValues[1] + NipLinks.REPO_BLOB + m.groupValues[3] }

private val NIP_MENTION = Regex("NIP-([0-9A-Fa-f]{2})(?![\\w-])", RegexOption.IGNORE_CASE)

/**
 * Turns plain `NIP-44` mentions into links, so they open in the reader like linked ones do. Text
 * already inside a link (`[NIP-44](…)`), inline code and fenced code is left alone, and so is a
 * mention of [self], the NIP being read.
 */
internal fun linkNipMentions(
    markdown: String,
    self: String?,
): String {
    if (!markdown.contains("NIP-", ignoreCase = true)) return markdown
    val out = StringBuilder(markdown.length + 64)
    var inFence = false
    markdown.lines().forEachIndexed { index, line ->
        if (index > 0) out.append('\n')
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inFence = !inFence
            out.append(line)
            return@forEachIndexed
        }
        if (inFence || REFERENCE_LINE.containsMatchIn(line)) {
            out.append(line)
        } else {
            out.append(linkMentionsInLine(line, self))
        }
    }
    return out.toString()
}

/** A mention glued to a word, path or anchor (`xNIP-44`, `/NIP-44`, `#NIP-44`) is not one. */
private fun isWordJoined(previous: Char?) = previous != null && (previous.isLetterOrDigit() || previous == '/' || previous == '#' || previous == '-' || previous == '_')

private val REFERENCE_LINE = Regex("^ {0,3}\\[[^\\]]+\\]:")

/** Links the mentions in one line, skipping code spans and the text and targets of existing links. */
private fun linkMentionsInLine(
    line: String,
    self: String?,
): String {
    val out = StringBuilder(line.length + 32)
    var i = 0
    var bracketDepth = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '`' -> {
                val close = line.indexOf('`', i + 1)
                val end = if (close < 0) line.length else close + 1
                out.append(line, i, end)
                i = end
            }

            c == '[' -> {
                bracketDepth++
                out.append(c)
                i++
            }

            c == ']' -> {
                if (bracketDepth > 0) bracketDepth--
                out.append(c)
                i++
                // Skip a link target right after the brackets: `](…)`.
                if (i < line.length && line[i] == '(') {
                    val close = line.indexOf(')', i)
                    val end = if (close < 0) line.length else close + 1
                    out.append(line, i, end)
                    i = end
                }
            }

            c == '<' -> {
                // An autolink or HTML: copy it whole.
                val close = line.indexOf('>', i)
                val end = if (close < 0) i + 1 else close + 1
                out.append(line, i, end)
                i = end
            }

            bracketDepth == 0 && (c == 'N' || c == 'n') && !isWordJoined(line.getOrNull(i - 1)) -> {
                val match = NIP_MENTION.matchAt(line, i)
                val number = match?.groupValues?.get(1)?.let { NipLinks.normalize(it) }
                if (match != null && number != null && number != self) {
                    out
                        .append('[')
                        .append(match.value)
                        .append("](")
                        .append(NipLinks.repoUrl(number))
                        .append(')')
                    i += match.value.length
                } else {
                    out.append(c)
                    i++
                }
            }

            else -> {
                out.append(c)
                i++
            }
        }
    }
    return out.toString()
}

private val STATUS_LINE = Regex("^(`[^`]+`\\s*)+$")
private val STATUS_FLAG = Regex("`([^`]+)`")
private val FLAG_WORD = Regex("^[A-Za-z][A-Za-z-]{0,23}$")
private val INLINE_LINK = Regex("!?\\[([^\\]]*)\\](\\([^)]*\\)|\\[[^\\]]*\\])")
private val EMPHASIS = Regex("(\\*\\*|__|\\*|_|~~)(\\S(?:.*?\\S)?)\\1")

/**
 * The first paragraph of prose, as plain text: no headings, no `draft` `optional` status line,
 * no code, lists or tables, and inline Markdown reduced to its text.
 */
internal fun plainPreview(markdown: String): String {
    val paragraph = StringBuilder()
    var inFence = false
    for (raw in markdown.lines()) {
        val line = raw.trim()
        if (line.startsWith("```") || line.startsWith("~~~")) {
            inFence = !inFence
            if (paragraph.isNotEmpty()) break
            continue
        }
        if (inFence) continue
        if (line.isEmpty()) {
            if (paragraph.isNotEmpty()) break
            continue
        }
        val isBlock =
            line.startsWith("#") ||
                line.startsWith("|") ||
                line.startsWith(">") ||
                line.startsWith("[") && line.contains("]:") ||
                SETEXT_UNDERLINE.matches(line) ||
                STATUS_LINE.matches(line)
        if (isBlock) {
            if (paragraph.isNotEmpty()) break
            continue
        }
        if (paragraph.isNotEmpty()) paragraph.append(' ')
        paragraph.append(line.removePrefix("- ").removePrefix("* "))
    }

    return paragraph
        .toString()
        .replace(INLINE_LINK) { it.groupValues[1] }
        .replace(EMPHASIS) { it.groupValues[2] }
        .replace("`", "")
}
