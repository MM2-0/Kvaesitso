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

/**
 * What to render for a NIP-84 highlight: the passage to show, and the range inside it that
 * the user actually marked.
 *
 * When the event carries a `context` tag the surrounding passage is shown with the quote
 * marked inside it, trimmed to a bounded window on each side so a quote pulled from the
 * middle of a long article doesn't drag whole paragraphs into the feed. When it doesn't —
 * or the quote can't be located in the context — [text] is the quote alone and [marked] is
 * null, meaning "mark all of it".
 */
data class HighlightQuote(
    val text: String,
    val marked: IntRange?,
) {
    companion object {
        /**
         * @param highlight the `content` of the highlight event — the marked passage.
         * @param context the optional surrounding text the highlight was taken from.
         * @param prefix the W3C TextQuoteSelector prefix, used to pick the right occurrence
         *   when the quote appears more than once in the context.
         */
        fun of(
            highlight: String,
            context: String?,
            prefix: String? = null,
        ): HighlightQuote {
            if (highlight.isEmpty()) return HighlightQuote(context.orEmpty(), null)
            if (context.isNullOrBlank()) return HighlightQuote(highlight, null)

            val at = locate(context, highlight, prefix)
            return if (at != null) {
                window(context, at, at + highlight.length)
            } else {
                // Context that doesn't actually contain the quote is worse than no context:
                // it would mark nothing and silently show text the user never highlighted.
                HighlightQuote(highlight, null)
            }
        }

        /**
         * Most surrounding context to keep on each side of the marked quote, in characters.
         * A highlight taken from the middle of a long article can carry the whole article in
         * its `context` tag; without a cap the feed card would render several paragraphs around
         * a one-sentence highlight. Just enough to frame the quote, no more.
         */
        private const val MAX_CONTEXT_CHARS_PER_SIDE = 160

        private const val ELLIPSIS = "…"

        /**
         * Trims the context down to [MAX_CONTEXT_CHARS_PER_SIDE] on each side of the quote,
         * snapping the cut to a whole-word boundary and marking it with an ellipsis. The quote
         * itself ([start] until [endExclusive]) is always kept in full, and the returned
         * [marked] range is re-based onto the trimmed text.
         */
        private fun window(
            context: String,
            start: Int,
            endExclusive: Int,
        ): HighlightQuote {
            val lead = trimLead(context.substring(0, start))
            val trail = trimTrail(context.substring(endExclusive))
            val quote = context.substring(start, endExclusive)

            val prefix = if (lead.trimmed) "$ELLIPSIS " else ""
            val suffix = if (trail.trimmed) " $ELLIPSIS" else ""

            // Drop any blank lines sitting at the very edges of the context — with the far text
            // trimmed (or even when it isn't) they would otherwise render as empty space above or
            // below the quote. Only the outer edges are touched; the whitespace framing the quote
            // itself is preserved.
            val raw = prefix + lead.text + quote + trail.text + suffix
            val leadingBlank = raw.length - raw.trimStart().length
            val text = raw.trim()

            val markStart = (prefix.length + lead.text.length - leadingBlank).coerceAtLeast(0)
            val markEnd = (markStart + quote.length).coerceAtMost(text.length)
            return HighlightQuote(text, markStart until markEnd)
        }

        private class Side(
            val text: String,
            val trimmed: Boolean,
        )

        /** Keeps the tail of the leading context, starting at a whole word. */
        private fun trimLead(text: String): Side {
            if (text.length <= MAX_CONTEXT_CHARS_PER_SIDE) return Side(text, false)

            var i = text.length - MAX_CONTEXT_CHARS_PER_SIDE
            // Skip the partial word the budget landed inside, then the whitespace after it, so
            // the kept text begins at the start of a whole word rather than mid-word.
            while (i < text.length && !text[i].isWhitespace()) i++
            while (i < text.length && text[i].isWhitespace()) i++
            val cut = if (i >= text.length) text.length - MAX_CONTEXT_CHARS_PER_SIDE else i
            return Side(text.substring(cut), true)
        }

        /** Keeps the head of the trailing context, ending at a whole word. */
        private fun trimTrail(text: String): Side {
            if (text.length <= MAX_CONTEXT_CHARS_PER_SIDE) return Side(text, false)

            var i = MAX_CONTEXT_CHARS_PER_SIDE
            // Retreat over the partial word the budget landed inside, then the whitespace before
            // it, so the kept text ends at the end of a whole word rather than mid-word.
            while (i > 0 && !text[i - 1].isWhitespace()) i--
            while (i > 0 && text[i - 1].isWhitespace()) i--
            val cut = if (i <= 0) MAX_CONTEXT_CHARS_PER_SIDE else i
            return Side(text.substring(0, cut), true)
        }

        /**
         * Finds [highlight] inside [context], preferring the occurrence whose preceding text
         * ends with [prefix]. Highlighters emit that prefix precisely so a repeated quote can
         * be pinned to the right spot.
         */
        private fun locate(
            context: String,
            highlight: String,
            prefix: String?,
        ): Int? {
            // The common case — no prefix to disambiguate with — needs only the first match, so
            // don't scan the whole context enumerating every occurrence.
            if (prefix.isNullOrBlank()) return context.indexOf(highlight).takeIf { it >= 0 }

            val occurrences = occurrencesOf(context, highlight)
            if (occurrences.isEmpty()) return null
            if (occurrences.size == 1) return occurrences.first()

            val tail = prefix.trimEnd()
            return occurrences.firstOrNull { context.substring(0, it).trimEnd().endsWith(tail) }
                ?: occurrences.first()
        }

        private fun occurrencesOf(
            context: String,
            highlight: String,
        ): List<Int> {
            val found = mutableListOf<Int>()
            var from = context.indexOf(highlight)
            while (from >= 0) {
                found.add(from)
                from = context.indexOf(highlight, from + 1)
            }
            return found
        }
    }
}
