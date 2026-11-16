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
package com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup

import com.vitorpamplona.quartz.buzz.forum.ForumCommentEvent
import com.vitorpamplona.quartz.buzz.forum.ForumPostEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.FiltersChanged
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupRolesEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupDeleteEventEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.GroupTag
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the exact REQ each NIP-29 data source puts on the wire (kinds, `#d`/`#h` scope, per-relay
 * batching, `since`/`until`/`limit`, authors) — the "does the correct query load per screen" half of
 * amethyst/plans/2026-07-18-nip29-group-chat-test-plan.md (Tier B), without an Account/relay client.
 */
class RelayGroupFilterBuildersTest {
    private val relayAUrl = "wss://relay-a.example/"
    private val relayBUrl = "wss://relay-b.example/"
    private val relayA = RelayUrlNormalizer.normalizeOrNull(relayAUrl)!!
    private val relayB = RelayUrlNormalizer.normalizeOrNull(relayBUrl)!!

    // Joined set: two groups on relay A, one on relay B.
    private val joined =
        setOf(
            GroupTag("g1", relayAUrl),
            GroupTag("g2", relayAUrl),
            GroupTag("g3", relayBUrl),
        )
    private val g1OnA = GroupId("g1", relayA)

    private val timelineKinds = listOf(ChatEvent.KIND, PollEvent.KIND)

    // NIP-29 kind-11/1111 threads + Buzz forum roots (45001) & comments (45003) — the Threads REQ
    // fetches both dialects (a vanilla relay matches nothing on the Buzz kinds and vice versa).
    private val threadKinds = listOf(ThreadEvent.KIND, CommentEvent.KIND, ForumPostEvent.KIND, ForumCommentEvent.KIND)

    // --- State (always-on): one #d filter per host relay, batching that relay's group ids ---

    @Test
    fun `state batches d-filters per host relay with metadata and pins kept apart`() {
        val filters = buildRelayGroupStateFilters(joined) { null }
        // two relays x (metadata + pins)
        assertEquals(4, filters.size)

        val a = filters.filter { it.relay == relayA }
        assertEquals(listOf(RELAY_GROUP_METADATA_KINDS, RELAY_GROUP_PIN_KINDS), a.map { it.filter.kinds })
        a.forEach {
            assertEquals(setOf("g1", "g2"), it.filter.tags!!["d"]!!.toSet())
            assertNull(it.filter.tags!!["h"], "state is #d-scoped, never #h")
        }

        val b = filters.filter { it.relay == relayB }
        b.forEach { assertEquals(listOf("g3"), it.filter.tags!!["d"]) }
    }

    /**
     * Regression: relay29-family relays (0xchat's `groups.0xchat.com`) reject a filter mixing the
     * 39000-39003 metadata kinds with any other kind (39005 pins included) with
     * `CLOSED … "blocked: it's not allowed to mix metadata kinds with others"` and drop the whole REQ —
     * so every joined group on such a relay silently loses its name, roster and membership.
     */
    @Test
    fun `state never mixes pins into the metadata filter`() {
        buildRelayGroupStateFilters(joined) { null }.forEach { f ->
            val kinds = f.filter.kinds!!
            assertFalse(
                kinds.contains(GroupPinnedEvent.KIND) && kinds.any { it in RELAY_GROUP_METADATA_KINDS },
                "39005 must not share a filter with the 39000-39003 metadata block: $kinds",
            )
        }
    }

    @Test
    fun `state applies the per-relay since`() {
        val filters = buildRelayGroupStateFilters(joined) { relay -> if (relay == relayA) 111L else null }
        filters.filter { it.relay == relayA }.forEach { assertEquals(111L, it.filter.since) }
        filters.filter { it.relay == relayB }.forEach { assertNull(it.filter.since) }
    }

    // --- Joined chat tail (always-on): ONE single-valued #h per group, time floor, NO limit ---

    @Test
    fun `joined chat tail carries exactly one h value per group with no count limit`() {
        val filter = buildRelayGroupJoinedChatTailFilter(GroupId("g1", relayA), 999L)

        assertEquals(relayA, filter.relay)
        // The joined tail always carries the Buzz timeline kinds too (see
        // RELAY_GROUP_ALL_TIMELINE_KINDS / BuzzTimelineKindsTest).
        assertEquals(timelineKinds + BUZZ_RELAY_GROUP_TIMELINE_EXTRA_KINDS, filter.filter.kinds)
        assertEquals(listOf("g1"), filter.filter.tags!!["h"])
        assertEquals(999L, filter.filter.since)
        assertNull(filter.filter.limit, "a time-floored tail must NOT cap by count")
        assertNull(filter.filter.until)
    }

    /**
     * Regression guard for the Messages-list freeze: `block/buzz` indexes a live subscription under a
     * single channel uuid resolved from `#h`, and downgrades any subscription whose filters mention two
     * or more distinct ids to a *global* one — which by design receives no channel-scoped events. The
     * stored query still answers correctly, so the symptom was "backfills on launch, then never updates".
     * Each joined group must therefore get its own single-valued filter (and its own subscription).
     */
    @Test
    fun `joined chat tail never batches multiple groups into one h filter`() {
        joined.forEach { tag ->
            val relay = RelayUrlNormalizer.normalizeOrNull(tag.relayUrl)!!
            val values = buildRelayGroupJoinedChatTailFilter(GroupId(tag.groupId, relay), 1L).filter.tags!!["h"]!!
            assertEquals(1, values.size, "a multi-value #h makes buzz register the sub as global and stop live delivery")
            assertEquals(tag.groupId, values.single())
        }
    }

    /**
     * The Messages-row preview is bounded by COUNT, never by time. The chat tail floors at `now - 7 days`
     * to keep recent chat warm; on its own that leaves a channel quiet for longer stuck on its
     * "No messages yet" placeholder forever, sorted to the bottom by `createdAt = 0`. Roster-driven
     * protocols fill their rows this way — NIP-28 with `limit = 1`, Concord with `limit = 10` — because
     * their row set comes from a list event and only needs one newest message per row.
     */
    @Test
    fun `preview filter asks for one newest message with no time floor on a cold start`() {
        val filter = buildRelayGroupPreviewFilter(g1OnA, null)

        assertEquals(relayA, filter.relay)
        assertEquals(listOf("g1"), filter.filter.tags!!["h"])
        assertEquals(1, filter.filter.limit)
        assertNull(filter.filter.since, "a cold start must reach back past any window to fill the row")
        assertNull(filter.filter.until)
    }

    @Test
    fun `preview filter rides the per-relay EOSE since once caught up`() {
        assertEquals(555L, buildRelayGroupPreviewFilter(g1OnA, 555L).filter.since)
    }

    /** Single-valued `#h`, so it can share the channel-scoped subscription without downgrading it. */
    @Test
    fun `preview filter names exactly one channel`() {
        assertEquals(1, buildRelayGroupPreviewFilter(g1OnA, null).filter.tags!!["h"]!!.size)
    }

    /**
     * Reactions/deletions ride the channel's own `#h` subscription, the way the Buzz reference client
     * does it (its `channelEventKinds` bundles deletion + reaction into the one `#h` channel REQ).
     * Amethyst's other source of reactions is the shared `#e` EventFinder query, which carries no `#h`
     * and is therefore registered as a *global* subscription — a class that never receives
     * channel-scoped events live, and a reaction inherits its target message's channel.
     */
    @Test
    fun `aux filter carries reactions and deletions scoped to one group`() {
        val filter = buildRelayGroupAuxFilter(GroupId("g1", relayA), 42L)

        assertEquals(relayA, filter.relay)
        assertEquals(listOf(DeletionRequestEvent.KIND, ReactionEvent.KIND, GroupDeleteEventEvent.KIND), filter.filter.kinds)
        assertEquals(listOf("g1"), filter.filter.tags!!["h"])
        assertEquals(42L, filter.filter.since)
        assertEquals(RELAY_GROUP_AUX_LIMIT, filter.filter.limit)
    }

    /**
     * The aux kinds must stay OUT of the timeline set: that set feeds the backward history pager, whose
     * `until` cursor walks `created_at`. Reactions eating a page's `limit` would advance the cursor past
     * chat messages that were never delivered.
     */
    @Test
    fun `aux kinds are not in the timeline set the history pager pages over`() {
        RELAY_GROUP_AUX_KINDS.forEach {
            assertFalse(it in RELAY_GROUP_ALL_TIMELINE_KINDS, "aux kind $it must not consume history-page limit")
        }
    }

    // --- Open chat tail: a single group's recent #h on the host relay ---

    @Test
    fun `open chat tail is a single host h-filter with since only`() {
        val f = buildRelayGroupOpenChatTailFilter(g1OnA, 5L)
        assertEquals(relayA, f.relay)
        // The open-channel tail carries the full Buzz timeline set PLUS the ephemeral
        // kind-20002 typing indicator (RELAY_GROUP_OPEN_TAIL_KINDS) — typing is scoped to
        // the one channel on screen, not the joined fleet. It is also the Buzz-dialect
        // detection bootstrap. Harmless on vanilla relays.
        assertEquals(RELAY_GROUP_OPEN_TAIL_KINDS, f.filter.kinds)
        assertEquals(listOf("g1"), f.filter.tags!!["h"])
        assertEquals(5L, f.filter.since)
        assertNull(f.filter.until)
        assertNull(f.filter.limit)
    }

    // --- Open chat history (backward pager): only armed relays, each at its own until, all authors ---

    @Test
    fun `history emits only armed relays at their until for all authors`() {
        val untilByRelay = mapOf(relayA to 200L) // relayB not armed → no cursor
        val filters = buildRelayGroupHistoryFilters(g1OnA, listOf(relayA, relayB), { untilByRelay[it] }, 50)

        val f = filters.single()
        assertEquals(relayA, f.relay)
        assertEquals(200L, f.filter.until)
        assertEquals(50, f.filter.limit)
        assertNull(f.filter.authors, "history must be all-authors so it re-materializes my own messages too")
        assertNull(f.filter.since, "backward paging is until-anchored, not since")
        assertEquals(listOf("g1"), f.filter.tags!!["h"])
    }

    @Test
    fun `history with nothing armed builds no filters`() {
        assertTrue(buildRelayGroupHistoryFilters(g1OnA, emptyList(), { 1L }, 50).isEmpty())
    }

    // --- Threads tab: kind 11/1111 #h on the host relay ---

    @Test
    fun `threads filter is host-scoped thread kinds`() {
        val f = buildRelayGroupThreadsFilter(g1OnA, 7L)
        assertEquals(relayA, f.relay)
        assertEquals(threadKinds, f.filter.kinds)
        assertEquals(listOf("g1"), f.filter.tags!!["h"])
        assertEquals(7L, f.filter.since)
    }

    // --- Threads history (backward pager): only armed relays, each at its own until, over thread kinds ---

    @Test
    fun `threads history emits only armed relays at their until over thread kinds`() {
        val untilByRelay = mapOf(relayA to 300L) // relayB not armed → no cursor
        val filters = buildRelayGroupThreadsHistoryFilters(g1OnA, listOf(relayA, relayB), { untilByRelay[it] }, 40)

        val f = filters.single()
        assertEquals(relayA, f.relay)
        assertEquals(threadKinds, f.filter.kinds)
        assertEquals(300L, f.filter.until)
        assertEquals(40, f.filter.limit)
        assertEquals(listOf("g1"), f.filter.tags!!["h"])
        assertNull(f.filter.since, "backward paging is until-anchored, not since")
        assertNull(f.filter.authors)
    }

    @Test
    fun `threads history with nothing armed builds no filters`() {
        assertTrue(buildRelayGroupThreadsHistoryFilters(g1OnA, emptyList(), { 1L }, 40).isEmpty())
    }

    // --- Directory (browse a relay): kinds 39000-39003, no d/h scope, limit 500 ---

    @Test
    fun `directory is an unscoped host filter over the four directory kinds`() {
        val f = buildRelayGroupDirectoryFilter(relayA, 42L)
        assertEquals(relayA, f.relay)
        assertEquals(RELAY_GROUP_DIRECTORY_KINDS, f.filter.kinds)
        assertEquals(RELAY_GROUP_DIRECTORY_LIMIT, f.filter.limit)
        assertEquals(42L, f.filter.since)
        assertNull(f.filter.tags, "the directory lists every group, so it carries no d/h scope")
        assertNull(f.filter.until)
        assertNull(f.filter.authors)
    }

    @Test
    fun `directory kinds are 39000-39003 and exclude the pin list`() {
        assertEquals(
            listOf(GroupMetadataEvent.KIND, GroupAdminsEvent.KIND, GroupMembersEvent.KIND, GroupRolesEvent.KIND),
            RELAY_GROUP_DIRECTORY_KINDS,
        )
        assertFalse(RELAY_GROUP_DIRECTORY_KINDS.contains(GroupPinnedEvent.KIND), "the directory doesn't fetch each group's pins")
        // The always-on state set DOES carry pins — the two kind sets must not be conflated.
        assertTrue(RELAY_GROUP_STATE_KINDS.contains(GroupPinnedEvent.KIND))
    }

    // --- Card warmup joined-skip: joined groups are covered always-on, so warmup must skip them ---

    @Test
    fun `joined check keys on both group id and host relay`() {
        assertTrue(isRelayGroupJoined(joined, GroupId("g1", relayA)))
        // Same group id but a DIFFERENT host relay is a different group — must not count as joined.
        assertFalse(isRelayGroupJoined(joined, GroupId("g1", relayB)))
        assertFalse(isRelayGroupJoined(joined, GroupId("gX", relayA)))
        assertFalse(isRelayGroupJoined(emptySet(), GroupId("g1", relayA)))
    }

    // --- Empty joined set ---

    @Test
    fun `empty joined set produces no filters`() {
        assertTrue(buildRelayGroupStateFilters(emptySet()) { null }.isEmpty())
    }

    // --- Reconnect stability: a `since`-only bump must NOT trigger a fresh REQ (no full replay) ---
    //
    // A RelayPool reconnect re-issues the current filter. If a post-EOSE `since` bump counted as a
    // "changed" filter, every reconnect would replay the whole window. These pin that the always-on
    // state + tail filters are reconnect-safe, while the backward pager's `until` step correctly IS a
    // new request (each page is genuinely new data). This is the whole reason the refactor uses a shared
    // time floor instead of a per-group `limit` + per-relay EOSE `since`.

    @Test
    fun `state gaining a since after EOSE is not a resend`() {
        val firstLoad = buildRelayGroupStateFilters(joined) { null }.map { it.filter }
        val afterEose = buildRelayGroupStateFilters(joined) { 500L }.map { it.filter }
        assertEquals(firstLoad.size, afterEose.size)
        firstLoad.zip(afterEose).forEach { (old, new) ->
            assertFalse(
                FiltersChanged.needsToResendRequest(old, new),
                "adding a since once EOSE lands is a since-only change → no replay on reconnect",
            )
        }
    }

    @Test
    fun `joined chat tail advancing its time floor is not a resend`() {
        val groups = joined.map { GroupId(it.groupId, RelayUrlNormalizer.normalizeOrNull(it.relayUrl)!!) }
        val earlier = groups.map { buildRelayGroupJoinedChatTailFilter(it, 1_000L).filter }
        val later = groups.map { buildRelayGroupJoinedChatTailFilter(it, 2_000L).filter } // recentBoundary() crept forward
        earlier.zip(later).forEach { (old, new) ->
            assertFalse(
                FiltersChanged.needsToResendRequest(old, new),
                "a forward recent-tail floor bump is since-only → reconnect re-REQs the tail, not a full page",
            )
        }
    }

    @Test
    fun `history stepping to an older until IS a resend`() {
        val page1 = buildRelayGroupHistoryFilters(g1OnA, listOf(relayA), { 300L }, 50).single().filter
        val page2 = buildRelayGroupHistoryFilters(g1OnA, listOf(relayA), { 200L }, 50).single().filter
        assertTrue(
            FiltersChanged.needsToResendRequest(page1, page2),
            "each backward page moves until, which is genuinely new data and MUST re-REQ",
        )
    }
}
