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
package com.vitorpamplona.amethyst.commons.service.spellcheck

import com.vitorpamplona.quartz.utils.urldetector.detection.UrlDetector

/** A word worth checking: [start] inclusive, [end] exclusive, in the text it was cut from. */
data class WordRange(
    val start: Int,
    val end: Int,
)

/**
 * Cuts a note or message into the words a spelling engine should judge, and leaves out the parts
 * of a Nostr post that are not prose: links, `nostr:` references and bare bech32 keys, hashtags,
 * `@` mentions, `:emoji:` shortcodes, e-mail addresses, Lightning invoices, and code.
 *
 * Within the prose it also leaves out what a dictionary would only get wrong: words with digits
 * ("h4x0r", "2nd"), all-caps acronyms, camelCase and snake_case identifiers, and single letters.
 */
object SpellCheckTokenizer {
    private val skipPatterns =
        listOf(
            // Fenced code blocks, closed or still being typed, then inline code.
            Regex("```[\\s\\S]*?(?:```|$)"),
            Regex("`[^`\\n]+`"),
            // nostr: references and bare bech32 entities.
            Regex(
                "(?:nostr:)?(?:npub|nsec|note|nevent|nprofile|naddr|nrelay|nembed|ncryptsec)1[02-9ac-hj-np-z]+",
                RegexOption.IGNORE_CASE,
            ),
            // Lightning invoices, LNURLs and Cashu tokens.
            Regex("(?:lightning:)?(?:lnbc|lntb|lnbcrt|lnurl)[0-9a-z]+", RegexOption.IGNORE_CASE),
            Regex("cashu[AB][A-Za-z0-9_\\-=]+"),
            // Hashtags, @mentions and :shortcode: emoji. Letters are matched broadly so a hashtag
            // in any script is skipped whole.
            Regex("#[^\\s#.,;:!?()\\[\\]{}\"']+"),
            Regex("@[^\\s@,;:!?()\\[\\]{}\"']+"),
            Regex(":[A-Za-z0-9_+\\-]+:"),
        )

    fun words(text: String): List<WordRange> {
        if (text.isEmpty()) return emptyList()

        val skipped = BooleanArray(text.length)
        skipUrls(text, skipped)
        skipEmails(text, skipped)
        skipPatterns.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                for (i in match.range) skipped[i] = true
            }
        }

        val result = ArrayList<WordRange>()
        var i = 0
        while (i < text.length) {
            if (!text[i].isLetter()) {
                i++
                continue
            }

            val start = i
            var end = i
            var hasDigit = false
            var hasLower = false
            var hasUpper = false
            var camel = false
            var touchesSkipped = false
            var j = i
            while (j < text.length) {
                val c = text[j]
                when {
                    c.isLetter() -> {
                        if (c.isUpperCase()) {
                            if (hasLower) camel = true
                            hasUpper = true
                        } else if (c.isLowerCase()) {
                            hasLower = true
                        }
                    }
                    c.isDigit() -> hasDigit = true
                    // Combining marks are part of the word: Indic vowel signs are spacing marks
                    // (हिन्दी would otherwise split into fragments an engine could flag).
                    isCombiningMark(c) -> {}
                    // An apostrophe only belongs to the word when a letter follows: "don't" is one
                    // word, the closing quote in 'like this' is not part of "this".
                    isApostrophe(c) && j + 1 < text.length && text[j + 1].isLetter() -> {}
                    else -> break
                }
                if (skipped[j]) touchesSkipped = true
                j++
                end = j
            }
            i = end

            if (touchesSkipped) continue
            if (hasDigit || camel) continue
            // Single letters, and runs no language spells as one word (a pasted key or blob): an
            // engine would only flag them, and Hunspell takes ~0.2 s on a 20,000-letter one.
            if (end - start < 2 || end - start > MAX_WORD_LENGTH) continue
            if (hasUpper && !hasLower) continue
            // Identifiers: snake_case, file.ext, and words glued to a link or a code span.
            val before = if (start > 0) text[start - 1] else ' '
            val after = if (end < text.length) text[end] else ' '
            if (before == '_' || after == '_') continue
            // "2nd", "4k": the scan starts at the first letter, after the digits.
            if (before.isDigit()) continue
            if (after == '.' && end + 1 < text.length && text[end + 1].isLetter()) continue
            if (before == '.' && start >= 2 && text[start - 2].isLetter()) continue
            if ((start > 0 && skipped[start - 1]) || (end < text.length && skipped[end])) continue

            result.add(WordRange(start, end))
        }
        return result
    }

    private fun isCombiningMark(c: Char) =
        c.category == CharCategory.NON_SPACING_MARK ||
            c.category == CharCategory.COMBINING_SPACING_MARK ||
            c.category == CharCategory.ENCLOSING_MARK ||
            // Zero-width non-joiner and joiner shape letters inside Indic and Persian words.
            c == '\u200C' ||
            c == '\u200D'

    private fun isApostrophe(c: Char) = c == '\'' || c == '’'

    // "Pneumonoultramicroscopicsilicovolcanoconiosis", the longest word in major dictionaries, is 45.
    private const val MAX_WORD_LENGTH = 48

    /**
     * Marks `local@domain.tld` addresses. Scans out from each `@` instead of using a regex: a
     * pattern that starts with the local part's character class retries from every letter of a
     * long unbroken run, which took 1.6 s on a 20,000-letter paste.
     */
    private fun skipEmails(
        text: String,
        skipped: BooleanArray,
    ) {
        var at = text.indexOf('@')
        while (at >= 0) {
            var start = at
            while (start > 0 && isEmailLocalChar(text[start - 1])) start--
            var end = at + 1
            while (end < text.length && isEmailDomainChar(text[end])) end++
            // A sentence's full stop is not part of the address.
            while (end > at + 1 && (text[end - 1] == '.' || text[end - 1] == '-')) end--

            val dot = text.indexOf('.', at + 1)
            if (start < at && dot in (at + 2) until end - 1) {
                for (k in start until end) skipped[k] = true
            }
            at = text.indexOf('@', maxOf(end, at + 1))
        }
    }

    private fun isEmailLocalChar(c: Char) = c.isAsciiLetterOrDigit() || c == '.' || c == '_' || c == '%' || c == '+' || c == '-'

    private fun isEmailDomainChar(c: Char) = c.isAsciiLetterOrDigit() || c == '.' || c == '-'

    private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    private fun skipUrls(
        text: String,
        skipped: BooleanArray,
    ) {
        var searchFrom = 0
        UrlDetector(text).detect().forEach { url ->
            val start = text.indexOf(url.originalUrl, searchFrom)
            if (start >= 0) {
                val end = start + url.originalUrl.length
                for (k in start until end) skipped[k] = true
                searchFrom = end
            }
        }
    }
}
