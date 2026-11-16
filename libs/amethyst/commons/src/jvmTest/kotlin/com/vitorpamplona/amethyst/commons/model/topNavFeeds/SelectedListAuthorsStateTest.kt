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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The account's top-nav selections, one per screen, collapse into one set of list members to watch:
 * picking a list adds its members, picking another swaps them, and going back to a follows feed
 * (whose authors load at startup) drops them.
 */
class SelectedListAuthorsStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val noRelays = MutableStateFlow(emptySet<NormalizedRelayUrl>())

    private val alice = "aa".repeat(32)
    private val bob = "bb".repeat(32)
    private val carol = "cc".repeat(32)

    @AfterTest
    fun tearDown() = scope.cancel()

    private fun list(vararg authors: HexKey): IFeedTopNavFilter = AuthorsByOutboxTopNavFilter(authors.toSet(), noRelays)

    private fun allFollows(): IFeedTopNavFilter = AllFollowsByOutboxTopNavFilter(setOf(alice, bob, carol), defaultRelays = noRelays, blockedRelays = noRelays)

    private fun SelectedListAuthorsState.awaitAuthors(expected: Set<HexKey>) =
        runBlocking {
            withTimeout(5_000) { flow.first { it == expected } }
        }

    @Test
    fun selectionsAcrossScreensMergeAndSwap() {
        val state = SelectedListAuthorsState(scope)
        val home = MutableStateFlow(list(alice))
        val pictures = MutableStateFlow(list(bob))
        state.register(home)
        state.register(pictures)

        state.awaitAuthors(setOf(alice, bob))

        // Picking another list on one screen swaps only that screen's members.
        home.value = list(carol)
        state.awaitAuthors(setOf(carol, bob))

        // Back to the follows feed: its authors load at startup, so nothing is watched for it.
        pictures.value = allFollows()
        state.awaitAuthors(setOf(carol))
    }

    @Test
    fun aScreenRegisteredLaterJoinsTheSet() {
        val state = SelectedListAuthorsState(scope)
        state.register(MutableStateFlow(list(alice)))
        state.awaitAuthors(setOf(alice))

        state.register(MutableStateFlow(list(bob, carol)))
        state.awaitAuthors(setOf(alice, bob, carol))
    }

    @Test
    fun noListSelectedWatchesNobody() {
        val state = SelectedListAuthorsState(scope)
        assertEquals(emptySet(), state.flow.value)

        state.register(MutableStateFlow(allFollows()))
        state.awaitAuthors(emptySet())
    }
}
