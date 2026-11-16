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

/**
 * A local-time timestamp token, `<t:UNIX_SECONDS>` or `<t:UNIX_SECONDS:STYLE>`.
 *
 * Marmot clients (MDK 0.13+, `marmot-markdown`'s `try_timestamp`) write an
 * instant this way so every reader sees it in their own time zone and locale.
 * The parse is MDK's exactly: an optional `-`, at least one digit, an optional
 * `:` plus one style letter, then `>`. Anything else is not a timestamp and
 * stays literal text.
 */
@Immutable
data class LocalTimestamp(
    val unixSeconds: Long,
    val style: Style,
) {
    enum class Style(
        val token: Char,
    ) {
        /** `t`: hours and minutes. */
        SHORT_TIME('t'),

        /** `T`: hours, minutes and seconds. */
        LONG_TIME('T'),

        /** `d`: numeric date. */
        SHORT_DATE('d'),

        /** `D`: date with a spelled-out month. */
        LONG_DATE('D'),

        /** `f`, and the default when the style is omitted: long date and short time. */
        SHORT_DATE_TIME('f'),

        /** `F`: weekday, long date and short time. */
        LONG_DATE_TIME('F'),

        /** `s`: numeric date and short time. */
        COMPACT_DATE_TIME('s'),

        /** `S`: numeric date and time with seconds. */
        COMPACT_DATE_TIME_SECONDS('S'),

        /** `R`: relative to now, "in 30 minutes" or "2 hours ago". */
        RELATIVE('R'),
        ;

        companion object {
            fun of(token: Char): Style? = entries.firstOrNull { it.token == token }
        }
    }

    /** A run of text inside one word: literal text, or a timestamp token. */
    sealed interface Piece {
        data class Text(
            val text: String,
        ) : Piece

        data class Time(
            val timestamp: LocalTimestamp,
            val source: String,
        ) : Piece
    }

    companion object {
        private const val OPEN = "<t:"

        // Year 9999 either way: past it the milliseconds overflow and a date is meaningless.
        private const val MAX_SECONDS = 253_402_300_799L

        /** Cheap pre-check before the parse, so the hot path pays one `contains`. */
        fun mightContain(text: String): Boolean = text.contains(OPEN)

        /**
         * Parses one token starting at [start] in [text]. Returns the timestamp
         * and the index just past its `>`, or null when [text] has no valid token
         * there.
         */
        fun parseAt(
            text: String,
            start: Int,
        ): Pair<LocalTimestamp, Int>? {
            if (!text.startsWith(OPEN, start)) return null
            var end = start + OPEN.length
            val numberStart = end
            if (text.getOrNull(end) == '-') end++
            val digitsStart = end
            while (end < text.length && text[end] in '0'..'9') end++
            if (end == digitsStart) return null
            val seconds = text.substring(numberStart, end).toLongOrNull()?.takeIf { it in -MAX_SECONDS..MAX_SECONDS } ?: return null

            val style =
                if (text.getOrNull(end) == ':') {
                    end++
                    val token = text.getOrNull(end) ?: return null
                    end++
                    Style.of(token) ?: return null
                } else {
                    Style.SHORT_DATE_TIME
                }
            if (text.getOrNull(end) != '>') return null
            return LocalTimestamp(seconds, style) to end + 1
        }

        /**
         * Splits [word] into literal text and timestamp tokens, or returns null
         * when it holds no valid token (the common case, and the cue to leave the
         * word as plain text).
         */
        fun split(word: String): List<Piece>? {
            if (!mightContain(word)) return null
            val pieces = ArrayList<Piece>()
            var cursor = 0
            var textStart = 0
            var found = false
            while (cursor < word.length) {
                val next = word.indexOf(OPEN, cursor)
                if (next < 0) break
                val parsed = parseAt(word, next)
                if (parsed == null) {
                    cursor = next + 1
                    continue
                }
                if (next > textStart) pieces.add(Piece.Text(word.substring(textStart, next)))
                pieces.add(Piece.Time(parsed.first, word.substring(next, parsed.second)))
                found = true
                cursor = parsed.second
                textStart = cursor
            }
            if (!found) return null
            if (textStart < word.length) pieces.add(Piece.Text(word.substring(textStart)))
            return pieces
        }
    }
}
