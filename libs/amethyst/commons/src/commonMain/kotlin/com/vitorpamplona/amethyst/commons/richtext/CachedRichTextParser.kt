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

import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.quartz.utils.cache.ConcurrentLruCache

/**
 * The shared, cross-platform cache in front of [RichTextParser]. Both Amethyst
 * Android and Amethyst Desktop render the same parsed [RichTextViewerState] from
 * one place, so the same content quoted in multiple notes is only parsed once.
 */
object CachedRichTextParser {
    // Global across every feed. Sized to hold the active feed's visible + prefetched
    // working set plus a few other feeds' recent entries, so pre-parsed bodies survive
    // until the render reads them and feed switches don't thrash. Each entry is one
    // note's parsed segments — typically single-digit KB.
    // Keyed by a 32-bit hash for speed, but every hit re-checks the inputs:
    // String.hashCode() is trivially collidable ("Aa"/"BB"), and serving one
    // note's parsed segments (images, invoices, link cards) under another
    // note's author is not an acceptable failure mode.
    private class CachedParse(
        val content: String,
        val tagsHash: Int,
        val callbackUri: String?,
        val authorPubKey: String?,
        val state: RichTextViewerState,
    ) {
        fun matches(
            content: String,
            tagsHash: Int,
            callbackUri: String?,
            authorPubKey: String?,
        ) = this.tagsHash == tagsHash &&
            this.callbackUri == callbackUri &&
            this.authorPubKey == authorPubKey &&
            this.content == content
    }

    private class CachedMarkdown(
        val content: String,
        val isMarkdown: Boolean,
    )

    private val richTextCache = ConcurrentLruCache<Int, CachedParse>(500)
    private val isMarkdownCache = ConcurrentLruCache<Int, CachedMarkdown>(200)

    private fun hashCodeCache(
        content: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String?,
        authorPubKey: String?,
    ): Int {
        var result = content.hashCode()
        // Content-addressed (memoized contentHash), not identity — `lists` is an
        // Array<Array<String>>, so a fresh instance with the same tag content must
        // map to the same entry. This lets an off-thread pre-parse (the feed media
        // prefetcher) populate the exact entry the renderer later looks up, turning
        // the scroll-time parse into a cache hit. The parse is a pure function of
        // tag content, so equal content sharing one entry is correct. The hash is
        // cached on the instance so this stays O(1)-amortized per tag list.
        result = 31 * result + tags.contentHash()
        if (callbackUri != null) {
            result = 31 * result + callbackUri.hashCode()
        }
        if (authorPubKey != null) {
            result = 31 * result + authorPubKey.hashCode()
        }
        return result
    }

    fun trimToSize(maxItems: Int) {
        richTextCache.trimToSize(maxItems)
        // isMarkdownCache is sized at 40% of richTextCache; preserve the ratio
        isMarkdownCache.trimToSize(maxItems * 2 / 5)
    }

    fun cachedText(
        content: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String? = null,
        authorPubKey: String? = null,
    ): RichTextViewerState? =
        richTextCache
            .get(hashCodeCache(content, tags, callbackUri, authorPubKey))
            ?.takeIf { it.matches(content, tags.contentHash(), callbackUri, authorPubKey) }
            ?.state

    fun parseText(
        content: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String? = null,
        authorPubKey: String? = null,
    ): RichTextViewerState {
        val key = hashCodeCache(content, tags, callbackUri, authorPubKey)
        val tagsHash = tags.contentHash()
        val cached = richTextCache.get(key)
        return if (cached != null && cached.matches(content, tagsHash, callbackUri, authorPubKey)) {
            cached.state
        } else {
            val newState = RichTextParser().parseText(content, tags, callbackUri, authorPubKey)
            richTextCache.put(key, CachedParse(content, tagsHash, callbackUri, authorPubKey, newState))
            newState
        }
    }

    // Shared across every RichTextViewer instance so that the same content quoted in multiple
    // notes only pays for the scan once. The decision is purely a function of `content`.
    fun isMarkdown(content: String): Boolean {
        val key = content.hashCode()
        isMarkdownCache.get(key)?.takeIf { it.content == content }?.let { return it.isMarkdown }
        val result = computeIsMarkdown(content)
        isMarkdownCache.put(key, CachedMarkdown(content, result))
        return result
    }

    private fun computeIsMarkdown(content: String): Boolean {
        val len = content.length
        if (len == 0) return false

        var isNewLine = true
        var nonSpaceCharCountOnLine = 0
        var lastNonSpaceChar = ' '
        // True while every non-whitespace char on the current line has
        // been the same '=' or '-' as the first. Required for the
        // setext-heading check below — without it, an ordinary sentence
        // ending in `-` would be promoted to a heading underline.
        var lineIsHomogeneousSetextChar = false

        // End of the URL being skipped. A URL is not markdown: `_`, `*`, `|` and `~~` are
        // ordinary path characters (Amazon's CDN serves `71zvSJXs2KL._SL1499_.jpg`), and
        // reading them as markers sends a bare image link to the markdown renderer, which
        // italicises part of it and breaks the link.
        var urlEnd = 0

        for (i in 0 until len) {
            val c = content[i]
            val cCode = c.code

            // O(1) trigger-char gate. Allocated once at object init.
            if (i >= urlEnd && cCode < 128 && IS_MARKDOWN_TRIGGER[cCode]) {
                if (c == ':') {
                    // `scheme://`: skip the rest of the URL. The scheme before the colon holds
                    // no trigger chars that could fire mid-word.
                    val end = urlEndAt(content, i)
                    if (end > 0) urlEnd = end
                }
                if (c == '`' || c == '|') return true
                if (c == '~' && i + 1 < len && content[i + 1] == '~') return true

                if (isNewLine) {
                    if (c == '#') {
                        var j = i + 1
                        while (j < len && content[j] == '#') j++
                        if (j < len && content[j] == ' ' && (j - i) <= 6) return true
                    }
                    if (c == '>') {
                        if (i + 1 < len && content[i + 1] == ' ') return true
                    }
                    if ((c == '-' || c == '*' || c == '+') && i + 1 < len && content[i + 1] == ' ') return true
                }

                if (c == '*') {
                    // CommonMark allows intraword `*` emphasis
                    // (`foo*bar*baz` → `foo<em>bar</em>baz`), so no
                    // word-boundary carve-out here. We still need a
                    // flanking check though: a `*` followed by
                    // whitespace can't open emphasis, and a closing
                    // `*` can't be preceded by whitespace. Without
                    // these, `5 * 3 = 15` and `5 * 3 * 7` would
                    // false-fire.
                    if (i + 1 < len && content[i + 1] == '*') return true
                    val nextChar = if (i + 1 < len) content[i + 1] else ' '
                    if (!nextChar.isMdSpaceOrNewline()) {
                        var j = i + 1
                        while (j < len && content[j] != '\n') {
                            if (content[j] == ':') {
                                // A `*` inside a URL closes nothing; one ending it can (see urlEndsIn).
                                val end = urlEndAt(content, j)
                                if (end > 0) {
                                    if (urlEndsIn(content, j, end, '*')) return true
                                    j = end
                                    continue
                                }
                            }
                            if (content[j] == '*' && !content[j - 1].isMdSpaceOrNewline()) return true
                            j++
                        }
                    }
                }
                if (c == '_') {
                    // CommonMark §6.2 forbids `_` from opening or
                    // closing emphasis intraword — the rule that
                    // makes `snake_case` and `foo_bar_baz` render
                    // literally. Same rule keeps cashuB/cashuA
                    // base64url payloads from false-firing, since
                    // every `_` inside such a token is surrounded
                    // by word chars.
                    //
                    // Practical heuristic: skip when the char before
                    // this `_` is a word char (letter, digit, or
                    // another `_` — the latter folds runs of `_` so
                    // we only evaluate the run's first position).
                    if (i == 0 || !content[i - 1].isMdWordChar()) {
                        if (i + 1 < len && content[i + 1] == '_') {
                            // `__` (or longer) at a non-word
                            // boundary. Walk to the end of the run
                            // and confirm it's followed by
                            // non-whitespace (left-flanking).
                            var runEnd = i + 1
                            while (runEnd < len && content[runEnd] == '_') runEnd++
                            if (runEnd < len && !content[runEnd].isMdSpaceOrNewline()) return true
                        } else {
                            // Single `_` at a non-word boundary.
                            // Find a matching `_` on the same line
                            // that itself is a valid closer (not
                            // followed by a word char).
                            var j = i + 1
                            while (j < len && content[j] != '\n') {
                                if (content[j] == ':') {
                                    // As for `*`: only a `_` ending a URL can close.
                                    val end = urlEndAt(content, j)
                                    if (end > 0) {
                                        if (urlEndsIn(content, j, end, '_')) return true
                                        j = end
                                        continue
                                    }
                                }
                                if (content[j] == '_') {
                                    val nextIsWord = j + 1 < len && content[j + 1].isMdWordChar()
                                    if (!nextIsWord) return true
                                }
                                j++
                            }
                        }
                    }
                }
                if (c == '[') {
                    var j = i + 1
                    while (j < len && content[j] != ']') {
                        if (content[j] == '\n') break
                        j++
                    }
                    if (j + 1 < len && content[j] == ']' && content[j + 1] == '(') {
                        // A markdown link's URL portion can't span a
                        // newline — bail if we hit '\n' before ')'.
                        var k = j + 2
                        while (k < len && content[k] != ')') {
                            if (content[k] == '\n') break
                            k++
                        }
                        if (k < len && content[k] == ')') return true
                    }
                }
            }

            // Structural line tracking — drives isNewLine for the next
            // iteration and powers the ordered-list + setext-heading
            // checks that need to know "how much non-space text has
            // appeared on the current line".
            if (isNewLine) {
                // A blank line is still "at line start": skipping '\n'/'\r'
                // here keeps isNewLine true so a heading/blockquote/list
                // marker on the next line (the standard `\n\n#` spacing) is
                // still recognized as line-leading. Without the newline
                // exclusion, the second '\n' of a blank line flipped
                // isNewLine to false and ATX headings after a blank line
                // went undetected.
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                    isNewLine = false
                    nonSpaceCharCountOnLine = 1
                    lastNonSpaceChar = c
                    lineIsHomogeneousSetextChar = (c == '=' || c == '-')

                    // Ordered list: digit+ followed by `. ` at line start.
                    if (cCode in 48..57) {
                        var j = i + 1
                        while (j < len && content[j].code in 48..57) j++
                        if (j + 1 < len && content[j] == '.' && content[j + 1] == ' ') return true
                    }
                }
            } else {
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                    nonSpaceCharCountOnLine++
                    if (c != lastNonSpaceChar) lineIsHomogeneousSetextChar = false
                    lastNonSpaceChar = c
                }

                if (c == '\n' || c == '\r') {
                    // Setext heading: a line of 3+ '=' or '-' (and
                    // nothing else) under non-empty text. Without the
                    // homogeneity check, an ordinary sentence ending
                    // in `-` would be promoted to a heading underline.
                    if (lineIsHomogeneousSetextChar &&
                        nonSpaceCharCountOnLine >= 3 &&
                        (lastNonSpaceChar == '=' || lastNonSpaceChar == '-')
                    ) {
                        return true
                    }
                    isNewLine = true
                    nonSpaceCharCountOnLine = 0
                    lineIsHomogeneousSetextChar = false
                }
            }
        }

        // Trailing-line setext check for content not terminated by '\n'.
        if (lineIsHomogeneousSetextChar &&
            nonSpaceCharCountOnLine >= 3 &&
            (lastNonSpaceChar == '=' || lastNonSpaceChar == '-')
        ) {
            return true
        }

        return false
    }

    /**
     * When the `:` at [colon] starts the `://` of a URL, the index just past the URL; else -1.
     * The URL ends at whitespace (Unicode spaces too), `<`, `>`, `"`, or a non-ASCII character
     * that is not a letter or digit, such as the CJK comma in `…/a、**重点**`.
     */
    private fun urlEndAt(
        content: String,
        colon: Int,
    ): Int {
        if (colon + 2 >= content.length || content[colon + 1] != '/' || content[colon + 2] != '/') return -1
        var j = colon + 3
        while (j < content.length && !content[j].endsUrl()) j++
        return j
    }

    private fun Char.endsUrl() = isWhitespace() || this == '<' || this == '>' || this == '"' || (code >= 128 && !isLetterOrDigit())

    /**
     * Whether the URL that runs to [end] ends in [marker], before any trailing punctuation. GFM
     * leaves such a run out of a bare URL, so it can close emphasis opened before the URL
     * (`_see https://x.com/a_`).
     */
    private fun urlEndsIn(
        content: String,
        colon: Int,
        end: Int,
        marker: Char,
    ): Boolean {
        var k = end - 1
        while (k > colon && content[k] in TRAILING_URL_PUNCTUATION) k--
        return content[k] == marker
    }

    private const val TRAILING_URL_PUNCTUATION = ".,!?:;)"

    // CommonMark "word" character for the intraword-emphasis rule:
    // ASCII letters, ASCII digits, and `_` itself. Used to fold runs of
    // `_` (so we only evaluate the start of a run) and to detect
    // `snake_case`-style intraword underscores.
    private fun Char.isMdWordChar(): Boolean = this.isLetterOrDigit() || this == '_'

    private fun Char.isMdSpaceOrNewline(): Boolean = this == ' ' || this == '\t' || this == '\n' || this == '\r'

    // Allocated once at object init; every isMarkdown call does an
    // O(1) lookup against this table instead of branching through ten
    // `contains(...)` calls.
    private val IS_MARKDOWN_TRIGGER =
        BooleanArray(128).apply {
            this['#'.code] = true
            this['*'.code] = true
            this['_'.code] = true
            this['['.code] = true
            this['`'.code] = true
            this['>'.code] = true
            this['-'.code] = true
            this['+'.code] = true
            this['~'.code] = true
            this['|'.code] = true
            this[':'.code] = true
        }
}

object CachedUrlParser {
    private val parsedUrlsCache = ConcurrentLruCache<Int, List<String>>(10)

    fun cachedParseValidUrls(content: String): List<String>? = parsedUrlsCache.get(content.hashCode())

    fun parseValidUrls(content: String): List<String> {
        if (content.isEmpty()) return emptyList()

        val key = content.hashCode()
        val cached = parsedUrlsCache.get(key)
        return if (cached != null) {
            cached
        } else {
            val urlSet = UrlParser().parseValidUrls(content)
            val newUrls = urlSet.withScheme.filter { it.startsWith("http") } + urlSet.withoutScheme.map { "http://$it" } + urlSet.bech32s.map { "http://$it" }
            parsedUrlsCache.put(key, newUrls)
            newUrls
        }
    }
}
