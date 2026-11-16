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
package com.vitorpamplona.amethyst.commons.relayClient.home

import com.vitorpamplona.amethyst.commons.model.HomeFeedType
import com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows.HomePostsConversationKinds
import com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows.HomePostsNewThreadKinds1
import com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows.HomePostsNewThreadKinds2
import com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows.HomePostsNewThreadKinds3
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Home only shows what is in the cache, so a kind the feed filters accept but the follows legs never
 * ask for only appears after some other screen happened to load it.
 */
class HomeFeedTypeKindCoverageTest {
    private val authorLegs =
        mapOf(
            "new threads 1" to HomePostsNewThreadKinds1,
            "new threads 2" to HomePostsNewThreadKinds2,
            "new threads 3" to HomePostsNewThreadKinds3,
            "conversations" to HomePostsConversationKinds,
        )

    @Test
    fun everyToggleableKindIsRequestedFromFollows() {
        val requested = authorLegs.values.flatten().toSet()
        HomeFeedType.entries.forEach { type ->
            type.kinds.forEach { kind ->
                assertTrue(kind in requested, "${type.name} owns kind $kind but no follows leg requests it")
            }
        }
    }

    @Test
    fun noKindIsRequestedByTwoLegs() {
        val all = authorLegs.values.flatten()
        assertEquals(all.distinct().size, all.size, "kinds requested twice: ${all.groupBy { it }.filterValues { it.size > 1 }.keys}")
    }

    @Test
    fun gitRepositoryAnnouncementsStayOffHome() {
        // Long-lived replaceable listings with no published_at: a back catalogue fills a leg and
        // every edit resurfaces one. Issues and PRs are the news; the repos have their own screen.
        assertTrue(authorLegs.values.none { GitRepositoryEvent.KIND in it })
        assertTrue(HomeFeedType.entries.none { GitRepositoryEvent.KIND in it.kinds })
    }

    @Test
    fun groupCodesAreUnique() {
        assertEquals(
            HomeFeedType.entries.size,
            HomeFeedType.entries
                .map { it.code }
                .toSet()
                .size,
        )
    }
}
