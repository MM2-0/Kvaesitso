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
package com.vitorpamplona.amethyst.commons.wot

import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class PeopleOrderTest {
    /** Only what the ordering reads: who is followed and the provider's scores. Nobody is muted. */
    private fun reader(
        follows: Set<HexKey>,
        scores: Map<HexKey, Int>,
    ): IAccount =
        Proxy.newProxyInstance(IAccount::class.java.classLoader, arrayOf(IAccount::class.java)) { _, method, args ->
            when (method.name) {
                "followingKeySet" -> follows
                "trustRankOf" -> scores[args[0] as String]
                "isHidden" -> false
                "getHiddenWordsCase" -> emptyList<Any>()
                else -> error("not needed: ${method.name}")
            }
        } as IAccount

    // One shared prefix so the cache search matches them all by key.
    private fun key(c: Char) = "abcd" + c.toString().repeat(60)

    private val friend = key('1')
    private val stranger = key('2')
    private val trusted = key('3')
    private val mostTrusted = key('4')
    private val friendWithScore = key('5')

    private val account =
        reader(
            follows = setOf(friend, friendWithScore),
            scores = mapOf(trusted to 40, mostTrusted to 90, friendWithScore to 99),
        )

    @Test
    fun followsLeadThenTheHighestScoresThenTheRest() {
        val people = listOf(stranger, trusted, friend, mostTrusted, friendWithScore)
        assertEquals(
            listOf(friend, friendWithScore, mostTrusted, trusted, stranger),
            people.sortedKeysByFollowsThenTrust(account),
        )
    }

    @Test
    fun theListKeepsItsOwnOrderInsideEachGroup() {
        val a = key('6')
        val b = key('7')
        assertEquals(listOf(friend, a, b), listOf(a, friend, b).sortedKeysByFollowsThenTrust(account))
        assertEquals(listOf(friend, b, a), listOf(b, friend, a).sortedKeysByFollowsThenTrust(account))
    }

    @Test
    fun theUserSearchPutsFollowsThenScoresThenTheRest() {
        val cache = EventCache()
        listOf(stranger, trusted, friend, mostTrusted, friendWithScore).forEach { cache.getOrCreateUser(it) }

        val found = cache.search.findUsersStartingWith("abcd", account).map { it.pubkeyHex }

        // Follows keep the name order among themselves; a score only lifts the people you don't follow.
        assertEquals(setOf(friend, friendWithScore), found.take(2).toSet())
        assertEquals(listOf(mostTrusted, trusted, stranger), found.drop(2))
    }
}
