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
package com.vitorpamplona.amethyst.commons.frontpage

/** One thing resolve changed. Dropped figures and unwrapped links are worth showing the reader. */
class ResolveChange(
    val kind: Kind,
    val detail: String,
) {
    /** [PERMALINK] an nevent citation, [ADDRESS] an naddr one (a stream, listing, calendar entry, long-form post or recipe). */
    enum class Kind { RESOLVED, DROPPED, UNWRAPPED, PERMALINK, ADDRESS, PROFILE }

    /** The page trying to do something the paper does not: the visible edge of an injection. */
    val noteworthy get() = kind == Kind.DROPPED || kind == Kind.UNWRAPPED
}

class Violation(
    val kind: Kind,
    val detail: String,
    val excerpt: String,
) {
    enum class Kind { QUOTE, IMAGE, LINK, MARKUP }
}

/** A page after resolve, checked: [html] is what renders; it ships only when [violations] is empty. */
class CheckedPage(
    val html: String,
    val changes: List<ResolveChange>,
    val violations: List<Violation>,
    val quotes: Int,
    /** The page's lead headline (its first h1, else h2), for notifications and history. */
    val headline: String? = null,
)

/**
 * Resolve and check, on a real HTML parser: art ids to shortlist URLs, citations to
 * permalinks, every other link unwrapped, then the QUOTE / IMAGE / LINK / MARKUP
 * boundary. The validator is not negotiable: a page with violations is never shown.
 */
fun interface PaperBoundary {
    fun resolveAndCheck(
        html: String,
        corpus: Corpus,
    ): CheckedPage
}

/** The platform's boundary (jsoup on Android and desktop), or null where there is none yet. */
expect fun paperBoundary(): PaperBoundary?
