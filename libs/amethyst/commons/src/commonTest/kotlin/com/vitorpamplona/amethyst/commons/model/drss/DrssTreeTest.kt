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
package com.vitorpamplona.amethyst.commons.model.drss

import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.quartz.drss.feedList.DrssFeedListEvent
import com.vitorpamplona.quartz.drss.feedList.tags.FeedTag
import com.vitorpamplona.quartz.drss.listFolder.DrssListFolderEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DrssTreeTest {
    private val me = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    private fun a(
        kind: Int,
        d: String,
    ) = ATag(kind, me.pubKey, d)

    @Test
    fun aRiverFollowsEveryoneInTheFoldersUnderIt() =
        runTest {
            val cache = EventCache()
            // drss: reading-feeds (30000) and a "podcasts" folder holding a feed list, plus a
            // folder that names its own parent, which is walked once.
            cache.justConsume(me.sign(FollowSetEvent.build("reading", listOf(UserTag(alice)), signer = me, dTag = "reading-feeds")), null, true)
            cache.justConsume(me.sign(DrssFeedListEvent.build("podcasts-feeds", listOf(FeedTag(bob), FeedTag(carol)))), null, true)
            cache.justConsume(me.sign(DrssListFolderEvent.build("podcasts", "podcasts", listOf(a(30100, "podcasts-feeds"), a(30101, "drss")))), null, true)
            cache.justConsume(me.sign(DrssListFolderEvent.build("drss", "DRSS lists", listOf(a(30000, "reading-feeds"), a(30101, "podcasts")))), null, true)

            val root = Address(30101, me.pubKey, "drss")
            assertEquals(
                listOf("drss" to 0, "reading-feeds" to 1, "podcasts" to 1, "podcasts-feeds" to 2),
                DrssTree.walk(root, cache).map { it.address.dTag to it.depth },
            )
            assertEquals(Address(30101, me.pubKey, "podcasts"), DrssTree.walk(root, cache).last().parent)
            assertEquals(setOf(alice, bob, carol), DrssTree.authorsUnder(root, cache))
        }

    @Test
    fun aChildNotLoadedYetIsListedButAddsNoOne() =
        runTest {
            val cache = EventCache()
            cache.justConsume(me.sign(DrssListFolderEvent.build("drss", "DRSS lists", listOf(a(30000, "missing")))), null, true)
            val root = Address(30101, me.pubKey, "drss")
            assertEquals(listOf("drss", "missing"), DrssTree.walk(root, cache).map { it.address.dTag })
            assertEquals(emptySet(), DrssTree.authorsUnder(root, cache))
        }
}
