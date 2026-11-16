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
package com.vitorpamplona.amethyst

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.rankPriorityFirst
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Locks in the @-mention priority semantics: priority pubkeys only move
 * users that already matched the search to the top of the list — they
 * never inject new entries, never remove any, and never disturb the
 * search's relevance order within the priority / non-priority groups.
 */
class UserSuggestionPriorityRankingTest {
    // User eagerly pins a few addressable note shells on construction;
    // empty shells are enough since the ranking never reads them.
    private val noContext = UserContext { addr -> AddressableNote(addr) }

    private fun user(hex: String) = User(hex, noContext)

    private val alice = user("aa".repeat(32))
    private val bob = user("bb".repeat(32))
    private val carol = user("cc".repeat(32))
    private val dave = user("dd".repeat(32))

    @Test
    fun emptyPriorityKeepsTheListUntouched() {
        val found = listOf(alice, bob, carol)

        assertSame(found, rankPriorityFirst(found, emptySet()))
    }

    @Test
    fun priorityUsersMoveToTheTop() {
        val found = listOf(alice, bob, carol, dave)

        val ranked = rankPriorityFirst(found, setOf(carol.pubkeyHex))

        assertEquals(listOf(carol, alice, bob, dave), ranked)
    }

    @Test
    fun relativeOrderIsPreservedWithinBothGroups() {
        // findUsersStartingWith returns relevance order; the stable sort
        // must keep alice-before-carol (priority) and bob-before-dave (rest).
        val found = listOf(alice, bob, carol, dave)

        val ranked = rankPriorityFirst(found, setOf(alice.pubkeyHex, carol.pubkeyHex))

        assertEquals(listOf(alice, carol, bob, dave), ranked)
    }

    @Test
    fun priorityKeysThatDidNotMatchTheSearchAreNotInjected() {
        val found = listOf(alice, bob)

        val ranked = rankPriorityFirst(found, setOf(carol.pubkeyHex, dave.pubkeyHex))

        assertEquals(found, ranked)
    }
}
