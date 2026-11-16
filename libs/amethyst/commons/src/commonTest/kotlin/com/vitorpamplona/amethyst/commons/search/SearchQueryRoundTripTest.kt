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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A saved search, a shared url and the form panel all put serialized text back into the field,
 * and the parser has to read it as the same query it came from. Anything that does not survive
 * this round trip silently changes what a bookmark means the next time it is opened.
 */
class SearchQueryRoundTripTest {
    private companion object {
        const val NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
        const val NOTE = "note1stqea6wmwezg9x6yyr6qkukw95ewtdukyaztycws65l8wppjmtpscawevv"
        const val NADDR = "naddr1qqqqygzxpsj7dqha57pjk5k37gkn6g4nzakewtmqmnwryyhd3jfwlpgxtspsgqqqw4rs3xyxus"
    }

    private fun assertRoundTrips(input: String) {
        val once = QueryParser.parse(input)
        val twice = QueryParser.parse(QuerySerializer.serialize(once))
        assertEquals(once, twice, "\"$input\" serialized to \"${QuerySerializer.serialize(once)}\"")
    }

    @Test
    fun everyTokenKindSurvivesBeingWrittenBackOut() {
        listOf(
            "bitcoin lightning",
            "from:$NPUB",
            "to:$NPUB",
            "to:$NOTE",
            "to:$NADDR",
            "#bitcoin #nostr",
            "since:2026-01-01 until:2026-12-31",
            "label:review/app",
            "group:dev",
            "site:example.com/a",
            "isbn:978-0-13-468599-1",
            "kind:article lang:en domain:nostr.com",
            "bitcoin -scam",
            "a OR b OR c",
            "from:$NPUB #bitcoin since:2026-01-01 group:dev zaps",
        ).forEach(::assertRoundTrips)
    }

    @Test
    fun aValueThatCouldNotReadBackAsItselfIsNotWrittenAsAToken() {
        // A group id with a space would silently point at somebody else's room.
        assertEquals(false, QuerySerializer.tokenizes("two words"))
        assertEquals(false, QuerySerializer.tokenizes("trailing."))
        assertEquals(true, QuerySerializer.tokenizes("general"))
    }
}
