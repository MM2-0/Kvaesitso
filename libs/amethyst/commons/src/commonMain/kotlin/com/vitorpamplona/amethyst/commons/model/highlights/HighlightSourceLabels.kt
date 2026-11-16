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
package com.vitorpamplona.amethyst.commons.model.highlights

import com.vitorpamplona.quartz.utils.Rfc3986

/**
 * Readable names for the place a highlight was taken from, built only from what the event
 * already carries -- no network.
 *
 * A web highlight usually has no title tag, and the page's OpenGraph preview may be turned off,
 * still loading, or blocked by a bot check. The URL itself almost always names the page, though:
 * `medium.com/feenk/rewilding-software-engineering-083b86c81c43` is "Rewilding software
 * engineering" in the "feenk" publication. The same goes for the `d` tag of an addressable
 * event (`bitcoin-here-now`). These helpers recover those names, and refuse when a path segment
 * is an opaque id rather than words, so the caller can fall back to something else.
 */
object HighlightSourceLabels {
    private val PAGE_EXTENSIONS = listOf(".html", ".htm", ".shtml", ".php", ".aspx", ".asp", ".jsp", ".md")

    // Last path segments that name a view of the page rather than the page: the slug is one up.
    private val VIEW_SEGMENTS = setOf("index", "amp", "print", "view", "full", "comments", "")

    // First path segments that are a site's section scaffolding, not a publication worth naming.
    private val GENERIC_SECTIONS =
        setOf(
            "p",
            "post",
            "posts",
            "blog",
            "blogs",
            "article",
            "articles",
            "news",
            "story",
            "stories",
            "wiki",
            "pages",
            "page",
            "watch",
            "status",
            "entry",
            "entries",
            "read",
            "docs",
            "doc",
            "archive",
            "archives",
            "content",
            "index",
            "amp",
            "view",
            "item",
            "items",
            "questions",
            "question",
            "abs",
            "pdf",
            "html",
            "en",
            "www",
            "home",
            "public",
            "s",
            "a",
            "c",
            "t",
        )

    private val SECTION_SEGMENT = Regex("^@?[A-Za-z0-9][A-Za-z0-9._-]{1,39}$")
    private val LOCALE_SEGMENT = Regex("^[a-z]{2}([-_][a-zA-Z]{2})?$")
    private val HEX_TOKEN = Regex("^[0-9a-fA-F]+$")

    /**
     * A title recovered from [url]'s path, or null when the path does not spell one out.
     *
     * `https://medium.com/feenk/rewilding-software-engineering-083b86c81c43` → "Rewilding
     * software engineering". Query strings and fragments are ignored; a trailing `index.html`,
     * `amp` or numeric segment defers to the segment before it.
     */
    fun titleFromUrl(url: String): String? {
        val segments = pathSegments(url)
        if (segments.isEmpty()) return null

        val last = segments.last()
        titleFromSlug(last)?.let { return it }

        if (segments.size >= 2 && isViewSegment(last)) {
            return titleFromSlug(segments[segments.size - 2])
        }

        return null
    }

    /**
     * Turns a slug into words: `rewilding-software-engineering-083b86c81c43` → "Rewilding
     * software engineering", `Software_engineering` → "Software engineering".
     *
     * Returns null unless at least two tokens are actual words, so an id (`083b86c81c43`, a
     * UUID, a timestamp) or a lone section name (`blog`, `watch`) never poses as a title.
     */
    fun titleFromSlug(slug: String): String? {
        var text = percentDecode(slug).trim()
        PAGE_EXTENSIONS.firstOrNull { text.endsWith(it, ignoreCase = true) }?.let { text = text.dropLast(it.length) }

        val tokens = text.split('-', '_', '+', ' ', '~').filter { it.isNotEmpty() }.toMutableList()

        // Ids trail a slug (Medium's hash, a numeric post id) and dates lead it.
        while (tokens.isNotEmpty() && isIdToken(tokens.last())) tokens.removeAt(tokens.lastIndex)
        while (tokens.isNotEmpty() && tokens.first().all { it.isDigit() }) tokens.removeAt(0)

        if (tokens.size < 2) return null
        if (tokens.any { it.length > 30 }) return null
        // A UUID splits into hex groups that would otherwise pass as short "words".
        if (tokens.all { HEX_TOKEN.matches(it) && it.any { c -> c.isDigit() } }) return null

        val words = tokens.count(::isWord)
        if (words < 2 || words * 3 < tokens.size * 2) return null
        if (tokens.none { word -> word.count { it.isLetter() } >= 3 }) return null

        return tokens.joinToString(" ").replaceFirstChar { it.uppercaseChar() }
    }

    /**
     * The site a URL belongs to, for a byline: the host without `www.`, plus the first path
     * segment when it names a publication or an account (`medium.com/feenk`,
     * `medium.com/@tudor`) rather than a section (`/blog`, `/p`, `/2024`, `/en`).
     */
    fun siteFromUrl(url: String): String? {
        val host =
            try {
                Rfc3986.host(url)
            } catch (_: Exception) {
                null
            }?.removePrefix("www.")?.ifBlank { null } ?: return null

        val segments = pathSegments(url)
        // Only a segment with a page under it is a section; a single segment is the page itself.
        if (segments.size < 2) return host

        // Segments come back raw; decode the one shown (`%40user` is `@user`).
        val first = percentDecode(segments.first())
        val isPublication =
            SECTION_SEGMENT.matches(first) &&
                !first.all { it.isDigit() } &&
                !LOCALE_SEGMENT.matches(first) &&
                first.lowercase() !in GENERIC_SECTIONS

        return if (isPublication) "$host/$first" else host
    }

    /**
     * Drops the site name a document `<title>` repeats at its end: `Title | Medium` → `Title`
     * when [siteName] is "Medium". Leaves [title] alone when the suffix is not the site's name.
     */
    fun cleanTitle(
        title: String,
        siteName: String?,
    ): String {
        val trimmed = title.trim()
        if (siteName.isNullOrBlank()) return trimmed

        for (separator in listOf(" | ", " - ", " — ", " – ", " · ", " :: ")) {
            val suffix = separator + siteName.trim()
            if (trimmed.length > suffix.length && trimmed.endsWith(suffix, ignoreCase = true)) {
                return trimmed.dropLast(suffix.length).trim()
            }
        }
        return trimmed
    }

    private fun isViewSegment(segment: String): Boolean {
        val lower = segment.lowercase()
        val base = PAGE_EXTENSIONS.firstOrNull { lower.endsWith(it) }?.let { lower.dropLast(it.length) } ?: lower
        return base in VIEW_SEGMENTS || base.all { it.isDigit() }
    }

    // Numbers, and long tokens that mix digits and letters (`083b86c81c43`), are ids.
    private fun isIdToken(token: String): Boolean =
        token.all { it.isDigit() } ||
            (token.length >= 8 && token.any { it.isDigit() } && token.any { it.isLetter() })

    // Mostly letters: `web3` is a word, `1a2b` is not.
    private fun isWord(token: String): Boolean = token.count { it.isLetter() } * 4 >= token.length * 3

    /**
     * The raw (still percent-encoded) non-empty path segments of [url], without its query or
     * fragment. Left encoded so a segment is decoded exactly once: decoding here and again in
     * [titleFromSlug] would turn a literal `%2541` into `A`.
     */
    private fun pathSegments(url: String): List<String> {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return emptyList()

        val afterAuthority = url.indexOf('/', schemeEnd + 3)
        if (afterAuthority < 0) return emptyList()

        val end = url.indexOfAny(charArrayOf('?', '#'), afterAuthority).let { if (it < 0) url.length else it }

        return url
            .substring(afterAuthority, end)
            .split('/')
            .filter { it.isNotEmpty() }
    }

    /** Decodes `%XX` escapes as UTF-8, or returns [text] unchanged when they are malformed. */
    private fun percentDecode(text: String): String {
        if ('%' !in text) return text

        val out = StringBuilder(text.length)
        val pending = ArrayList<Byte>()
        var i = 0
        while (i < text.length) {
            if (text[i] == '%') {
                if (i + 2 > text.lastIndex) return text
                // toIntOrNull(16) also takes a sign (`%-1`, `%+5`), which is not an escape.
                val high = hexValue(text[i + 1])
                val low = hexValue(text[i + 2])
                if (high < 0 || low < 0) return text
                pending.add(((high shl 4) or low).toByte())
                i += 3
            } else {
                if (pending.isNotEmpty()) {
                    out.append(pending.toByteArray().decodeToString())
                    pending.clear()
                }
                out.append(text[i])
                i++
            }
        }
        if (pending.isNotEmpty()) out.append(pending.toByteArray().decodeToString())
        return out.toString()
    }

    private fun hexValue(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> -1
        }
}
