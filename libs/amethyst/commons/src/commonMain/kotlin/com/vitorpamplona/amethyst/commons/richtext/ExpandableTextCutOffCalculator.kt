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

class ExpandableTextCutOffCalculator {
    companion object {
        private const val SHORT_TEXT_LENGTH = 350
        private const val SHORTEN_AFTER_LINES = 10
        private const val TOO_FAR_SEARCH_THE_OTHER_WAY = 450

        fun indexToCutOff(content: String): Int {
            // Cuts the text in the first space or first new line after SHORT_TEXT_LENGTH characters
            val firstSpaceAfterCut =
                content.indexOf(' ', SHORT_TEXT_LENGTH).let { if (it < 0) content.length else it }
            val firstNewLineAfterCut =
                content.indexOf('\n', SHORT_TEXT_LENGTH).let { if (it < 0) content.length else it }

            // Cuts the text if too many new lines have passed.
            val firstLineAfterLineLimits =
                content.nthIndexOf('\n', SHORTEN_AFTER_LINES).let { if (it < 0) content.length else it }

            // gets the minimum of them all.
            val min = minOf(firstSpaceAfterCut, firstNewLineAfterCut, firstLineAfterLineLimits)

            val result =
                if (min > TOO_FAR_SEARCH_THE_OTHER_WAY) {
                    // if it is still too big, finds the first space or new line BEFORE the cut off.
                    val newString = content.take(SHORT_TEXT_LENGTH)

                    // The last break of either kind. Each must count as missing (-1) on its own:
                    // a text with spaces but no new line still cuts at its last space.
                    val lastBreakBeforeCut = maxOf(newString.lastIndexOf(' '), newString.lastIndexOf('\n'))
                    val cutBefore = if (lastBreakBeforeCut < 0) newString.length else lastBreakBeforeCut

                    // No break between the cut and TOO_FAR_SEARCH_THE_OTHER_WAY, so one word runs
                    // across SHORT_TEXT_LENGTH, from after the last break before it to `min`. When
                    // that word is a token the parser draws as a single widget (a cashu token, an
                    // invoice, a nostr: link), cutting it leaves an unparseable fragment, and
                    // cutting before it hides the widget behind "Show More": keep it whole.
                    if (isAtomicToken(content, lastBreakBeforeCut + 1)) min else cutBefore
                } else {
                    min
                }

            // Only returns if the difference between short and long posts is more than 100 chars or too many new lines.
            return if (result == firstLineAfterLineLimits || result + 100 < content.length) {
                result
            } else {
                content.length
            }
        }

        /**
         * Whether the word at [from] is drawn as one widget whose size does not depend on its
         * length, so it must never be split: cashu tokens, lightning invoices and LNURLs, CLINK
         * offers and NIP-19 entities. Mirrors the prefixes `RichTextParser.wordIdentifier` checks.
         */
        private fun isAtomicToken(
            content: String,
            from: Int,
        ): Boolean =
            content.startsWith("cashuA", from, true) ||
                content.startsWith("cashuB", from, true) ||
                content.startsWith("lnbc", from, true) ||
                content.startsWith("lnurl", from, true) ||
                content.startsWith("noffer1", from, true) ||
                RichTextParser.startsWithNIP19Scheme(content, from)
    }
}

fun String.nthIndexOf(
    ch: Char,
    N: Int,
): Int {
    var occur = N
    var pos = -1

    while (occur > 0) {
        // calling the native function multiple times is faster than looping just once
        pos = indexOf(ch, pos + 1)
        if (pos == -1) return -1
        occur--
    }

    return if (occur == 0) pos else -1
}
