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
package com.vitorpamplona.amethyst.commons.relayClient.user

import com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allUserFollows.AllUserFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.custom.CustomFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.BaseEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.user.loaders.UserOutboxFinderSubAssembler
import com.vitorpamplona.amethyst.commons.relayClient.user.watchers.UserCardsSubAssembler
import com.vitorpamplona.amethyst.commons.relayClient.user.watchers.UserReportsSubAssembler
import com.vitorpamplona.amethyst.commons.relayClient.user.watchers.UserWatcherSubAssembler
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A member of a list the user picked in the top bar, but does not follow, had no loader asking for
 * their kind:10002, so the feed only looked for their notes on fallback relays. The feed now watches
 * its authors through the regular user finder as feed authors ([UserFinderQueryState.isFeedAuthor]):
 * relay list and live metadata, but not the reports and cards only a drawn profile needs.
 */
class FeedAuthorsUserFinderTest {
    private val indexRelay = NormalizedRelayUrl("wss://index.test/")
    private val outboxRelay = NormalizedRelayUrl("wss://outbox.test/")
    private val blocked = MutableStateFlow(emptySet<NormalizedRelayUrl>())

    private val client = EmptyNostrClient()
    private val cache = EventCache()
    private val outboxFinder = UserOutboxFinderSubAssembler(client, cache, RelayOfflineTracker(client), { emptySet() })

    private val account =
        object : UserFinderAccount {
            override val userFinderPubkeyHex: HexKey = "00".repeat(32)

            override fun indexRelays() = setOf(indexRelay)

            override fun outboxHomeRelays() = emptySet<NormalizedRelayUrl>()

            override fun searchRelays() = emptySet<NormalizedRelayUrl>()

            override fun searchOnlyRelays() = emptySet<NormalizedRelayUrl>()

            override fun followPlusAllMineWithSearchRelays() = emptySet<NormalizedRelayUrl>()

            override fun commonRelays() = emptySet<NormalizedRelayUrl>()

            override fun cardHomeRelays() = emptySet<NormalizedRelayUrl>()

            override fun trustProvider(): ServiceProviderTag? = null

            override fun followerCountProvider(): ServiceProviderTag? = null

            override fun declaredFollowsByOutboxRelay() = emptyMap<NormalizedRelayUrl, Set<HexKey>>()
        }

    @AfterTest
    fun tearDown() = outboxFinder.destroy()

    private fun newUser() = cache.getOrCreateUser(NostrSignerSync(KeyPair()).pubKey)

    private fun List<RelayBasedFilter>.authors() = flatMap { it.filter.authors.orEmpty() }.toSet()

    @Test
    fun listFeedsExposeTheirAuthors() {
        val authors = setOf("aa".repeat(32), "bb".repeat(32))
        val relays = MutableStateFlow(emptySet<NormalizedRelayUrl>())

        assertEquals(authors, AuthorsByOutboxTopNavFilter(authors, blocked).outboxAuthors())
        assertEquals(authors, MutedAuthorsByOutboxTopNavFilter(authors, blocked).outboxAuthors())

        // A proxy feed reads from the proxy relays, never from the authors' own.
        assertEquals(emptySet(), AuthorsByProxyTopNavFilter(authors, relays.value).outboxAuthors())

        // The follows feeds stay out: AccountFollowsLoaderSubAssembler loads their whole author set
        // (kind:3 plus the user's own lists and follow packs) at startup.
        assertEquals(emptySet(), AllUserFollowsByOutboxTopNavFilter(authors, relays, blocked).outboxAuthors())
        assertEquals(emptySet(), AllFollowsByOutboxTopNavFilter(authors, defaultRelays = relays, blockedRelays = blocked).outboxAuthors())
    }

    @Test
    fun customAndCommunityFeedsExposeAuthorsOnlyWhenTheyReadTheirRelays() {
        val authors = setOf("aa".repeat(32), "bb".repeat(32))
        val follows = MutableStateFlow(emptySet<NormalizedRelayUrl>())
        val byAuthors = FeedSource.Filter(authors = authors.toImmutableList())

        assertEquals(authors, CustomFeedTopNavFilter(byAuthors, follows, blocked, emptySet()).outboxAuthors())
        // A feed pinned to its own relays, or read through a proxy, never asks the authors' relays.
        val pinned = byAuthors.copy(relays = persistentListOf("wss://pinned.test"))
        assertEquals(emptySet(), CustomFeedTopNavFilter(pinned, follows, blocked, emptySet()).outboxAuthors())
        assertEquals(emptySet(), CustomFeedTopNavFilter(byAuthors, follows, blocked, setOf(outboxRelay)).outboxAuthors())

        val community = "34550:${"cc".repeat(32)}:x"
        assertEquals(authors, SingleCommunityTopNavFilter(community, authors, emptySet(), blocked).outboxAuthors())
        assertEquals(emptySet(), SingleCommunityTopNavFilter(community, authors, setOf(outboxRelay), blocked).outboxAuthors())
    }

    @Test
    fun feedAuthorsGetRelayListsAndMetadataButNotReportsOrCards() {
        val finder = UserFinderFilterAssembler(client, cache, RelayOfflineTracker(client))
        try {
            val drawn = UserFinderQueryState(newUser(), account)
            val member = UserFinderQueryState(newUser(), account, isFeedAuthor = true)
            finder.subscribe(listOf(drawn, member))

            fun keysOf(type: Class<*>) = (finder.group.single { type.isInstance(it) } as BaseEoseManager<*>).allKeys()

            assertEquals(setOf(drawn, member), keysOf(UserOutboxFinderSubAssembler::class.java))
            assertEquals(setOf(drawn, member), keysOf(UserWatcherSubAssembler::class.java))
            assertEquals(setOf(drawn), keysOf(UserReportsSubAssembler::class.java))
            assertEquals(setOf(drawn), keysOf(UserCardsSubAssembler::class.java))
        } finally {
            finder.destroy()
        }
    }

    @Test
    fun asksForTheRelayListOnlyOfAuthorsMissingOne() {
        val listOnly = NostrSignerSync(KeyPair())
        val withRelayList = NostrSignerSync(KeyPair())

        val relayList = AdvertisedRelayListEvent.create(listOf(AdvertisedRelayInfo(outboxRelay, AdvertisedRelayType.BOTH)), withRelayList)
        assertTrue(cache.justConsume(relayList, null, true))

        val keys =
            listOf(listOnly, withRelayList).map {
                UserFinderQueryState(cache.getOrCreateUser(it.pubKey), account, isFeedAuthor = true)
            }

        val filters = outboxFinder.updateFilter(keys).orEmpty()

        assertEquals(setOf(listOnly.pubKey), filters.authors())
        filters.forEach {
            assertEquals(listOf(MetadataEvent.KIND, AdvertisedRelayListEvent.KIND), it.filter.kinds)
        }
        assertTrue(filters.any { it.relay == indexRelay }, "the index relays are asked: ${filters.map { it.relay }}")
    }

    @Test
    fun aWholeListIsAskedForInChunks() {
        val keys = List(250) { UserFinderQueryState(newUser(), account, isFeedAuthor = true) }

        val onIndex = outboxFinder.updateFilter(keys).orEmpty().filter { it.relay == indexRelay }

        assertEquals(3, onIndex.size)
        onIndex.forEach {
            assertTrue(
                it.filter.authors
                    .orEmpty()
                    .size <= 100,
            )
        }
        assertEquals(keys.map { it.user.pubkeyHex }.toSet(), onIndex.authors())
    }

    @Test
    fun aListLargerThanTheBaseCacheIsNotAskedForAgain() {
        // More members than the tried-set's base size (200). Before it was fitted to the live keys,
        // marking them all tried evicted the first 100, which the next pass asked for again.
        val keys = List(300) { UserFinderQueryState(newUser(), account, isFeedAuthor = true) }
        assertEquals(
            300,
            outboxFinder
                .updateFilter(keys)
                .orEmpty()
                .authors()
                .size,
        )

        keys.forEach { outboxFinder.hasTried.newEose(it.user, indexRelay, TimeUtils.now()) }

        assertEquals(emptySet(), outboxFinder.updateFilter(keys).orEmpty().authors())
    }

    @Test
    fun onlyDrawnUsersKeepAStandingReqWhenNoRelayHasTheirList() {
        val member = newUser()
        val drawn = newUser()
        val keys =
            listOf(
                UserFinderQueryState(member, account, isFeedAuthor = true),
                UserFinderQueryState(drawn, account),
            )

        // Every relay this account would ask already answered without a kind:10002.
        outboxFinder.hasTried.newEose(member, indexRelay, TimeUtils.now())
        outboxFinder.hasTried.newEose(drawn, indexRelay, TimeUtils.now())

        assertEquals(setOf(drawn.pubkeyHex), outboxFinder.updateFilter(keys).orEmpty().authors())
    }

    @Test
    fun aMemberThatIsAlsoDrawnIsTreatedAsDrawn() {
        val user = newUser()
        val keys =
            listOf(
                UserFinderQueryState(user, account, isFeedAuthor = true),
                UserFinderQueryState(user, account),
            )

        outboxFinder.hasTried.newEose(user, indexRelay, TimeUtils.now())

        assertEquals(setOf(user.pubkeyHex), outboxFinder.updateFilter(keys).orEmpty().authors())
    }
}
