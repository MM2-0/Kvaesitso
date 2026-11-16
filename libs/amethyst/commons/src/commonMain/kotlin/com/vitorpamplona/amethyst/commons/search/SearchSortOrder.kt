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

enum class SearchSortOrder(
    val label: String,
) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    RELEVANCE("Relevance"),
    POPULAR("Popular"),
    NAME_AZ("A → Z"),
    NAME_ZA("Z → A"),
    ;

    companion object {
        val EVENT_OPTIONS = listOf(RELEVANCE, NEWEST, POPULAR)
        val PEOPLE_OPTIONS = listOf(NAME_AZ, NAME_ZA)
        val EVENT_DEFAULT = NEWEST
        val PEOPLE_DEFAULT = NAME_AZ
    }
}
