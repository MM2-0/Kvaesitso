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

import com.vitorpamplona.quartz.nip50Search.SearchableKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The kind window, against the two things that can make it wrong.
 *
 * It can ask for a kind whose text nothing indexes — a filter slot spent on a result that cannot
 * come back — or it can leave out a kind that is both searchable and renderable, which the reader
 * meets as "search does not find my pictures". Both had happened by the time this was written, in
 * three copies of the list that disagreed with each other.
 */
class RenderableKindsTest {
    @Test
    fun everyKindAskedForIsOneQuartzIndexes() {
        val notIndexed = RenderableKinds.ALL.filterNot { it in SearchableKinds.ALL }
        assertEquals(
            RenderableKinds.MATCHES_ON_CONTENT_ONLY,
            notIndexed.toSet(),
            "a kind is being asked for that Quartz does not index. Either it gained an " +
                "indexableContent() and belongs in neither exception list, or it is a new " +
                "exception that needs saying out loud in MATCHES_ON_CONTENT_ONLY.",
        )
    }

    @Test
    fun theKindsDeliberatelyLeftOutAreStillTheOnesLeftOut() {
        // Pinned so that a kind Quartz *starts* indexing arrives here as a decision — show it in
        // search or say why not — rather than as silence. Adding one to RenderableKinds.ALL is
        // the fix when Amethyst has a card for it; extending this list is the fix when it does not.
        val omitted = SearchableKinds.ALL.filterNot { it in RenderableKinds.ALL.toSet() }
        assertEquals(
            listOf(
                // profiles and statuses — the People scope finds these, the Notes scope should not
                0,
                30315,
                30382,
                // chat and DMs: found inside a channel or a conversation, not in a global feed
                9,
                14,
                42,
                1311,
                3302,
                9002,
                30177,
                39000,
                40002,
                // raids and clips: side traffic belonging to one stream, not a global feed
                1312,
                1313,
                // threads and forums Amethyst has no card for
                11,
                45001,
                45003,
                48106,
                // citations
                31,
                32,
                33,
                // edits, file headers and gallery entries: attachments to a note, not results
                1010,
                1063,
                1065,
                1163,
                32176,
                39092,
                // subtitle tracks: their cue text is indexed so a store can find what was said in
                // a video, but the track has no card of its own — a hit here would be a wall of
                // WebVTT rather than the video it belongs to.
                39307,
                // git: the repo itself is searchable, its patch/issue/status traffic is not
                818,
                1617,
                1618,
                1621,
                1622,
                1624,
                1630,
                1631,
                1632,
                1633,
                // labels, ratings and trust assertions: metadata about other events
                1985,
                30392,
                30393,
                30394,
                30395,
                31871,
                31872,
                31873,
                11871,
                34259,
                31987,
                // zaps and money
                8333,
                9321,
                9734,
                9735,
                9736,
                9737,
                9041,
                33863,
                38383,
                // Mostro instance terms: they have a card, but what they index is an instance name
                // and its currency codes, so a search for "USD" or "EUR" would be a page of
                // instance terms. Reached from a link or a repost, not from global search.
                38385,
                38000,
                // DVM job requests
                5050,
                5100,
                5250,
                5302,
                5303,
                // napplet and nsite snapshots: the app or site is searchable, a snapshot of it is not
                5128,
                5129,
                // torrents, roads, birds, canvases and other feeds Amethyst does not render
                1315,
                2003,
                2004,
                2473,
                12473,
                30142,
                30620,
                38192,
                40100,
                // curation sets and relay/interest lists
                30002,
                30003,
                30004,
                30006,
                30015,
                30040,
                30041,
                30045,
                30063,
                39701,
                // marketplace stalls and products: reached through their merchant, not searched
                30017,
                30018,
                30019,
                30020,
                // Open Markets shipping options: drawn on the product that names them, not searched
                30406,
                // podcasting 2.0 mirrors of kinds already in the window
                30054,
                30055,
                // agents and personas
                10100,
                30175,
                30176,
                // calendar RSVPs, exercise and workout templates and app definitions
                31925,
                33401,
                33402,
                31990,
                // geocache curation lists: the caches themselves are in the window, but a list of
                // them has no card yet, so a result would be a blank row
                37517,
                // decentralized list headers and items: no card yet, so a result would be a blank row
                9998,
                9999,
                39998,
                39999,
                // deletions, vanish requests and group/channel moderation: their stated reason is
                // indexed so a store can find it, but each is an action on other events, not a result
                5,
                43,
                44,
                62,
                9007,
                9021,
                9022,
                39003,
                // a DM's encrypted-file header: found inside the conversation, like kind 14
                15,
                // chess games and live moves: no card in search
                64,
                30066,
                30068,
                // marketplace bid confirmations and DVM OP_RETURN jobs: side traffic of a sale or a job
                1022,
                5901,
                // long-form drafts: indexed like the article they become, but an unpublished draft
                // is not a result to show in global search
                30024,
                // sticker packs: their shortcodes are indexed like emoji packs', no card yet
                30031,
                // profile theme definitions: their name and description are indexed, no card yet
                36767,
                // accepted-badge sets and feed definitions: lists with no card yet
                30008,
                31890,
                // relay roles, ContextVM server announcements and cyberspace objects: no card yet
                11316,
                11333,
                33331,
                33534,
                // Buzz workspace traffic (identity archival, projects, team catalogs, stream edits,
                // reminders, summaries, jobs, approvals, artifacts): searched inside a workspace,
                // not in a global feed
                8002,
                8003,
                9035,
                9036,
                30178,
                30621,
                40003,
                40006,
                40007,
                40008,
                40099,
                40901,
                42000,
                43001,
                43002,
                43003,
                43004,
                43005,
                43006,
                45010,
                46030,
                46031,
                // OpenAgents capability heads, package listings and releases: indexed so search
                // relays can find agent capabilities and plugins, but Amethyst has no card for them
                3184,
                30180,
                30184,
            ).sorted(),
            omitted.sorted(),
            "the set of searchable kinds not shown in search changed",
        )
    }

    @Test
    fun theGroupsAreTheWholeListAndNothingElse() {
        assertEquals(RenderableKinds.ALL, RenderableKinds.GROUPS.flatten())
        assertEquals(RenderableKinds.ALL.distinct(), RenderableKinds.ALL)
    }

    @Test
    fun noGroupIsLargerThanARelayWillAcceptOrSmallEnoughToWaste() {
        // Balanced, not greedy: a greedy split leaves a tail group of one, which costs a whole
        // extra REQ to carry a single kind.
        val sizes = RenderableKinds.GROUPS.map { it.size }
        assertTrue(sizes.all { it <= RenderableKinds.MAX_KINDS_PER_FILTER }, "$sizes")
        assertTrue(sizes.max() - sizes.min() <= 1, "groups are lopsided: $sizes")
    }

    @Test
    fun theWindowNeverAsksForAKindResultsWouldDrop() {
        // The two lists answer different questions — "what does a query without a kind ask for"
        // and "what can a result never be" — but an overlap would mean a REQ arm spent on events
        // the scan throws away on arrival.
        assertEquals(
            emptySet(),
            RenderableKinds.ALL.toSet() intersect RenderableKinds.NEVER_IN_RESULTS,
        )
    }
}
