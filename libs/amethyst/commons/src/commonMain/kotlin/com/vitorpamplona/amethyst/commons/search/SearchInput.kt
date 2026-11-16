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

/**
 * One state of the search box: the text, and what it means.
 *
 * A pair rather than two flows because both halves are needed and they have to agree. Most
 * collectors want [query]; a few genuinely want the characters — a relay finder matching
 * `wss://`, an id lookup deciding whether the box holds a bech32 pointer rather than a phrase —
 * and reading those from a separately debounced flow lets a collector pair one keystroke's text
 * with another's parse.
 *
 * [nameTerms] is here rather than at the call site because it was the most-repeated parse of all:
 * every people-and-channel finder re-parsed the whole box to ask for it.
 */
class SearchInput(
    val text: String,
) {
    val query: SearchQuery = QueryParser.parse(text)

    /**
     * The words a name search should be given.
     *
     * Not simply the leftover text: a query that is nothing but `#bitcoin` leaves no leftovers,
     * and handing the finders an empty string means they answer with nobody rather than with the
     * channel called "Bitcoin" the reader was plainly looking for.
     */
    val nameTerms: String get() = query.nameSearchTerms()

    val isBlank: Boolean get() = text.isBlank()
}
