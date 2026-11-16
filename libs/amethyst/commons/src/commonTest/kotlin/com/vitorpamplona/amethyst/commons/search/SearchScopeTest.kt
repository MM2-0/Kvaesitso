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
import kotlin.test.assertTrue

/**
 * The scope table, pinned exactly as the seven hand-written guards behaved before it existed.
 *
 * Written from the guards rather than from the enum, so this is the record of what the toggle did
 * on the day it was collapsed — the one thing a refactor of seven scattered `if`s into one `when`
 * can quietly get wrong.
 */
class SearchScopeTest {
    @Test
    fun theTableIsWhatTheSevenGuardsSaid() {
        // scope to the kinds it showed, read off the result flows in SearchBarViewModel.
        val expected =
            mapOf(
                // `ALL` guarded nothing anywhere.
                SearchScope.ALL to SearchResultKind.entries.toSet(),
                // People hid notes, hashtags, relays and all three channel kinds.
                SearchScope.PEOPLE to setOf(SearchResultKind.PEOPLE),
                // Notes hid people, relays and the channels — but not hashtags, which the note
                // flow's guard let through and the channel flows' guards did not. Emojis came later: they are pulled out of the notes found.
                SearchScope.NOTES to setOf(SearchResultKind.NOTES, SearchResultKind.HASHTAGS, SearchResultKind.EMOJIS),
            )
        expected.forEach { (scope, shown) ->
            assertEquals(shown, SearchResultKind.entries.filter { scope.shows(it) }.toSet(), "$scope")
        }
    }

    @Test
    fun everyResultKindIsAnsweredBySomeScope() {
        // A kind no scope shows is a result the reader can never reach, which is a wiring mistake
        // rather than a decision — and the failure mode of adding a kind to the enum alone.
        SearchResultKind.entries.forEach { kind ->
            assertTrue(SearchScope.entries.any { it.shows(kind) }, "$kind is unreachable in every scope")
        }
    }
}
