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
package com.vitorpamplona.amethyst.commons.search

/** One of the kinds of thing a search can come back with. */
enum class SearchResultKind {
    PEOPLE,
    NOTES,
    HASHTAGS,
    RELAYS,
    PUBLIC_CHATS,
    EPHEMERAL_CHATS,
    LIVE_ACTIVITIES,

    /** Single custom emojis pulled out of the emoji packs a search found (see [EmojiSearch]). */
    EMOJIS,
}

/**
 * Which of those the reader asked to see.
 *
 * [shows] is the whole of it, in one table, because it used to be seven separate
 * `if (scope == …) return emptyList()` guards written into seven result flows — and being spelled
 * out seven times is how `ALL` came to mean "everything" in six of them and "everything except
 * hashtags" in the seventh. A new result kind now answers the question by appearing in the
 * `when`, rather than by someone remembering to guard it.
 */
enum class SearchScope {
    /** Everything the front end can render. */
    ALL,

    /** People only — a hashtag or a relay is not a person, and neither is a note. */
    PEOPLE,

    /**
     * Notes only. Hashtags stay: `#bitcoin` in the box is a note filter, and the tag chip is how
     * the reader applies it, so hiding it here would take away the control the scope needs.
     */
    NOTES,
    ;

    fun shows(kind: SearchResultKind): Boolean =
        when (this) {
            ALL -> true
            PEOPLE -> kind == SearchResultKind.PEOPLE
            NOTES -> kind == SearchResultKind.NOTES || kind == SearchResultKind.HASHTAGS || kind == SearchResultKind.EMOJIS
        }
}
