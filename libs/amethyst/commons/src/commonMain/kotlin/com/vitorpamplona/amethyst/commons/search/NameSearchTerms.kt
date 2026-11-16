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
 * The one word a *name* search should be given, from a query written in the token language.
 *
 * The people and channel finders match a single string against a name — `startsWith`, `contains` —
 * so they cannot be handed the whole box. Handing them the raw text was wrong once the tokens
 * started meaning something: `#bitcoin` went in verbatim and matched no channel called "Bitcoin".
 * Handing them only the leftover terms is wrong in the other direction, because a query that is
 * *nothing but* a token leaves nothing behind, and `#bitcoin` would find nobody at all.
 *
 * So: the leftover words when there are any, and otherwise the word the reader actually typed
 * inside the token. Someone typing `#bitcoin` into a search box means "bitcoin" by it, and someone
 * typing `from:vitor` means "vitor" — neither means "search for nothing".
 *
 * One term, never a join: `"bitcoin lightning"` is not a name any channel starts with, so where a
 * query names two tokens the first is the one that stands for it.
 */
fun SearchQuery.nameSearchTerms(): String {
    if (text.isNotBlank()) return text
    hashtags.firstOrNull()?.let { return it }
    authorNames.firstOrNull()?.let { return it }
    return ""
}
