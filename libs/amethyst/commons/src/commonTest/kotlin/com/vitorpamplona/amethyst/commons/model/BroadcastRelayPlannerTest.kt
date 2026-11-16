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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import com.vitorpamplona.quartz.nip34Git.state.tags.RefTag
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The routing policy extracted from `EventBroadcaster`, driven over a plain [EventCache]
 * the way amy drives it — the app runs the same code over its `LocalCache`.
 */
class BroadcastRelayPlannerTest {
    private val me = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))
    private val bob = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000008".hexToByteArray()))

    private fun relay(name: String): NormalizedRelayUrl = RelayUrlNormalizer.normalizeOrNull("wss://$name.example.com")!!

    private val myOutbox = setOf(relay("my-outbox"))
    private val myInbox = setOf(relay("my-inbox"))
    private val myBroadcast = setOf(relay("my-broadcast"))
    private val everywhere = setOf(relay("index"), relay("popular"))
    private val bobInbox = relay("bob-inbox")
    private val bobOutbox = relay("bob-outbox")
    private val bobDm = relay("bob-dm")
    private val seenOn = relay("seen-on")

    private fun planner(cache: EventCache) =
        BroadcastRelayPlanner(
            cache,
            object : BroadcastRelaySource {
                override fun userProfile() = cache.getOrCreateUser(me.pubKey)

                override fun notificationRelays() = myInbox

                override fun broadcastRelays() = myBroadcast

                override fun outboxRelays() = myOutbox + myBroadcast

                override fun personalOutboxRelays() = myOutbox

                override fun everywhereRelays() = everywhere
            },
        )

    /** A cache that knows Bob's NIP-65 and DM relay lists and holds one note of his, seen on [seenOn]. */
    private suspend fun cacheWithBob(): Pair<EventCache, Note> {
        val cache = EventCache()
        val nip65 =
            AdvertisedRelayListEvent.create(
                listOf(
                    AdvertisedRelayInfo(bobInbox, AdvertisedRelayType.READ),
                    AdvertisedRelayInfo(bobOutbox, AdvertisedRelayType.WRITE),
                ),
                bob,
            )
        cache.justConsume(nip65, null, true)
        cache.justConsume(DmRelayListEvent.create(listOf(bobDm), bob), null, true)
        val note = bob.sign(TextNoteEvent.build("hi"))
        cache.justConsume(note, null, true)
        val bobNote = cache.getNoteIfExists(note.id)!!
        bobNote.addRelay(seenOn)
        return cache to bobNote
    }

    @Test
    fun aReactionReachesTheAuthorsInboxAndOurOutbox() =
        runTest {
            val (cache, bobNote) = cacheWithBob()
            val planner = planner(cache)
            val like = me.sign(ReactionEvent.build("+", EventHintBundle<Event>(bobNote.event!!)))
            cache.justConsumeMyOwnEvent(like)

            val relays = planner.computeRelayListToBroadcast(like)
            assertTrue(relays.containsAll(myOutbox), "our outbox: $relays")
            assertTrue(bobInbox in relays, "the author's inbox: $relays")
            assertTrue(myBroadcast.all { it in relays }, "a public reaction also goes to the broadcast list: $relays")
        }

    @Test
    fun aRepostUsesTheReactionRouting() =
        runTest {
            val (cache, bobNote) = cacheWithBob()
            val repost = me.sign(RepostEvent.build(EventHintBundle<Event>(bobNote.event!!)))

            val relays = planner(cache).computeMyReactionToNote(bobNote, repost)
            assertTrue(seenOn in relays, "where the note was seen: $relays")
            assertTrue(bobInbox in relays, "the author's inbox: $relays")
            assertTrue(relays.containsAll(myOutbox + myBroadcast), "our full outbox: $relays")
        }

    @Test
    fun aDeletionGoesToOurOutboxAndWhereTheTargetsWereSeen() =
        runTest {
            val cache = EventCache()
            val mine = me.sign(TextNoteEvent.build("oops"))
            cache.justConsume(mine, null, true)
            val note = cache.getNoteIfExists(mine.id)!!
            note.addRelay(seenOn)

            assertEquals(myOutbox + myBroadcast + seenOn, planner(cache).computeDeletionRelays(listOf(note)))
        }

    @Test
    fun aGiftWrapGoesOnlyToTheRecipientsDmInbox() =
        runTest {
            val (cache, _) = cacheWithBob()
            val wrap = GiftWrapEvent.create(me.sign(TextNoteEvent.build("secret")), bob.pubKey)
            assertEquals(setOf(bobDm), planner(cache).computeRelayListToBroadcast(wrap))
        }

    @Test
    fun aRelayListGoesEverywhere() =
        runTest {
            val cache = EventCache()
            val list = AdvertisedRelayListEvent.create(listOf(AdvertisedRelayInfo(relay("x"), AdvertisedRelayType.BOTH)), me)
            val relays = planner(cache).computeRelayListToBroadcast(list)
            assertTrue(relays.containsAll(everywhere + myBroadcast), "$relays")
        }

    @Test
    fun personalEventsSkipTheBroadcastList() =
        runTest {
            val cache = EventCache()
            val planner = planner(cache)
            cache.getOrCreateUser(me.pubKey)
            val settings = me.sign<Event>(1_700_000_000, 30078, arrayOf(arrayOf("d", "app-settings")), "{}")
            cache.justConsumeMyOwnEvent(settings)

            assertEquals(myOutbox, planner.computeRelayListToBroadcast(settings))
        }

    @Test
    fun aNoteThatTagsUsReachesOurOwnInbox() =
        runTest {
            val (cache, bobNote) = cacheWithBob()
            val planner = planner(cache)
            val reply = bob.sign(TextNoteEvent.build("hey") { add(arrayOf("p", me.pubKey)) })
            cache.justConsume(reply, null, true)
            assertTrue(planner.computeRelayListToBroadcast(reply).containsAll(myInbox))
        }

    @Test
    fun aRepositoryAnnouncementAndItsIssuesReachTheRepositoryRelays() =
        runTest {
            val (cache, _) = cacheWithBob()
            val planner = planner(cache)
            val repo =
                me.sign(
                    GitRepositoryEvent.build(
                        name = "repo",
                        description = null,
                        webUrls = emptyList(),
                        cloneUrls = emptyList(),
                        relays = listOf("wss://grasp.example.com", "not a relay"),
                        maintainers = emptyList(),
                        hashtags = emptyList(),
                        earliestUniqueCommit = null,
                        dTag = "repo",
                    ),
                )
            cache.justConsumeMyOwnEvent(repo)

            val repoRelays = planner.computeRelayListToBroadcast(repo)
            assertTrue(relay("grasp") in repoRelays, "a GRASP server only hosts what it holds: $repoRelays")
            assertTrue(repoRelays.containsAll(myOutbox), "our outbox: $repoRelays")

            val issue = bob.sign(GitIssueEvent.build("bug", "it broke", EventHintBundle(repo), emptyList(), emptyList()))
            val issueRelays = planner.computeRelayListToBroadcast(issue)
            assertTrue(relay("grasp") in issueRelays, "the relays the repository monitors for issues: $issueRelays")
        }

    /** Bob's private repository (GRASP-08), monitored only on its own relay. */
    private suspend fun privateRepository(cache: EventCache): GitRepositoryEvent {
        val repo =
            bob.sign(
                GitRepositoryEvent.build(
                    name = "secret",
                    description = null,
                    webUrls = emptyList(),
                    cloneUrls = emptyList(),
                    relays = listOf("wss://grasp.example.com"),
                    maintainers = emptyList(),
                    hashtags = emptyList(),
                    earliestUniqueCommit = null,
                    dTag = "secret",
                ) { add(arrayOf("private", "true")) },
            )
        cache.justConsume(repo, null, true)
        return repo
    }

    @Test
    fun aPrivateRepositorysEventsStayOnItsRelaysEvenFarFromIt() =
        runTest {
            val (cache, _) = cacheWithBob()
            val planner = planner(cache)
            val repo = privateRepository(cache)
            val only = setOf(relay("grasp"))

            val issue = bob.sign(GitIssueEvent.build("bug", "it broke", EventHintBundle(repo), emptyList(), emptyList()))
            cache.justConsume(issue, null, true)
            val like = me.sign(ReactionEvent.build("+", EventHintBundle<Event>(issue)))
            cache.justConsumeMyOwnEvent(like)
            // Two levels down: a reaction to the reaction names no repository itself.
            val likeOfLike = me.sign(ReactionEvent.build("+", EventHintBundle<Event>(like)))
            assertEquals(only, planner.computeRelayListToBroadcast(likeOfLike))

            // A repost goes through the reaction routing, and stays too.
            val issueNote = cache.getNoteIfExists(issue.id)!!
            assertEquals(only, planner.computeMyReactionToNote(issueNote, me.sign(RepostEvent.build(EventHintBundle<Event>(issue)))))

            // The signed state names no repository: it is the one with its own d-tag.
            val state = bob.sign(GitRepositoryStateEvent.build("secret", listOf(RefTag.branch("main", "a".repeat(40))), "main"))
            assertEquals(only, planner.computeRelayListToBroadcast(state))
        }

    @Test
    fun anEventUnderARepositoryNotYetLoadedStaysWhereThatRepositoryWasSeen() =
        runTest {
            val (cache, _) = cacheWithBob()
            val planner = planner(cache)
            // The announcement is signed but never reaches this cache: it could be private.
            val repo = privateRepository(EventCache())
            val issue = bob.sign(GitIssueEvent.build("bug", "it broke", EventHintBundle(repo, relay("grasp")), emptyList(), emptyList()))
            cache.justConsume(issue, null, true)
            cache.getNoteIfExists(issue.id)!!.addRelay(seenOn)

            val like = me.sign(ReactionEvent.build("+", EventHintBundle<Event>(issue)))
            cache.justConsumeMyOwnEvent(like)
            val relays = planner.computeRelayListToBroadcast(like)
            assertTrue(myOutbox.none { it in relays }, "not to the outbox while the repository may be private: $relays")
            assertTrue(seenOn in relays || relay("grasp") in relays, "where the repository's events were seen: $relays")

            // Once a public announcement arrives, the usual routing applies.
            val publicRepo =
                bob.sign(
                    GitRepositoryEvent.build(
                        name = "secret",
                        description = null,
                        webUrls = emptyList(),
                        cloneUrls = emptyList(),
                        relays = listOf("wss://grasp.example.com"),
                        maintainers = emptyList(),
                        hashtags = emptyList(),
                        earliestUniqueCommit = null,
                        dTag = "secret",
                        createdAt = repo.createdAt + 1,
                    ),
                )
            cache.justConsume(publicRepo, null, true)
            assertTrue(planner.computeRelayListToBroadcast(like).containsAll(myOutbox), "public: the outbox again")
        }

    @Test
    fun aNoteThatOnlyNamesARepositoryCannotSteerItsReplies() =
        runTest {
            val (cache, _) = cacheWithBob()
            val planner = planner(cache)
            // A plain note naming a repository nobody has loaded, with a relay hint of its choosing.
            val bait = bob.sign(TextNoteEvent.build("look") { add(arrayOf("a", "30617:${"f".repeat(64)}:x", "wss://evil.example.com")) })
            cache.justConsume(bait, null, true)
            cache.getNoteIfExists(bait.id)!!.addRelay(seenOn)
            val like = me.sign(ReactionEvent.build("+", EventHintBundle<Event>(bait)))
            cache.justConsumeMyOwnEvent(like)
            val relays = planner.computeRelayListToBroadcast(like)
            assertTrue(relays.containsAll(myOutbox), "the usual routing: $relays")
            assertTrue(relay("evil") !in relays || relays.size > 1, "never only the note's hint: $relays")
        }

    @Test
    fun deletingAPrivateRepositorysEventStaysOnItsRelays() =
        runTest {
            val (cache, _) = cacheWithBob()
            val planner = planner(cache)
            val repo = privateRepository(cache)
            val issue = me.sign(GitIssueEvent.build("bug", "it broke", EventHintBundle(repo), emptyList(), emptyList()))
            cache.justConsumeMyOwnEvent(issue)
            val relays = planner.computeDeletionRelays(listOf(cache.getNoteIfExists(issue.id)!!))
            assertTrue(myOutbox.none { it in relays } && myBroadcast.none { it in relays }, "not the outbox: $relays")
            assertTrue(relay("grasp") in relays, "the repository's relay: $relays")
        }
}
