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
package com.vitorpamplona.amethyst.commons.relayClient.oneshot

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ThreadAssembler
import com.vitorpamplona.amethyst.commons.model.ThreadLevelCalculator
import com.vitorpamplona.amethyst.commons.relayClient.thread.filterEventsInThreadForRoot
import com.vitorpamplona.amethyst.commons.relayClient.thread.filterMissingEventsForThread
import com.vitorpamplona.amethyst.commons.viewmodels.thread.ThreadFeedFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Loads the conversation an event belongs to over a [OneShotRelayAccess], with the thread
 * screen's own code:
 *
 *  - what to fetch is the screen's two relay filters ([filterEventsInThreadForRoot]:
 *    everything citing the root on its author's inbox and where it was seen;
 *    [filterMissingEventsForThread]: parents and roots not loaded yet);
 *  - which notes form the thread is [ThreadAssembler];
 *  - the order and indentation are [ThreadFeedFilter.thread] and
 *    [ThreadLevelCalculator.replyLevel], exactly what the app shows.
 *
 * Each round first settles the thread from the store, then sends the relays only the
 * filters it has not sent yet, and stops when a round brings nothing new. A thread seen
 * before therefore costs one relay round (for replies newer than the store), not one per
 * filter. Deletions in the store are applied before anything is added, so a deleted note
 * never comes back from a relay that missed the kind:5.
 */
class ThreadLoader(
    private val access: OneShotRelayAccess,
    private val timeoutMs: Long = 8_000,
    private val maxRounds: Int = MAX_ROUNDS,
) {
    val notes = OneShotNoteCache(access, timeoutMs)

    /** A loaded thread, laid out for [viewer]. */
    class LoadedThread(
        val focus: Note,
        /** The root note; its event may be missing when no relay returned it. */
        val root: Note?,
        /** Every note of the thread in display order, including slots whose event never arrived. */
        val ordered: List<Note>,
        val queriedRelays: Set<NormalizedRelayUrl>,
    ) {
        private val levels = mutableMapOf<Note, Int>()

        /** How far [note] is indented under the root. */
        fun depth(note: Note): Int = ThreadLevelCalculator.replyLevel(note, levels)
    }

    /**
     * Loads the thread around [focus] and lays it out for [viewer] (whose follows rank
     * replies). Returns null when the cache will not hold [focus]: a deletion in the store
     * covers it, or it is rejected outright.
     */
    suspend fun load(
        focus: LocatedEvent,
        viewer: HexKey,
    ): LoadedThread? {
        notes.addAll(listOf(focus.seenOn.firstOrNull() to focus.event))
        val focusNote = notes.add(focus.event, focus.seenOn)?.takeIf { it.event != null } ?: return null
        val defaultRelays = access.bootstrapRelays()
        val queried = mutableSetOf<NormalizedRelayUrl>()
        val sent = mutableSetOf<String>()
        val askedAuthors = mutableSetOf<HexKey>()
        val storeQueried = mutableSetOf<String>()

        for (round in 0 until maxRounds) {
            val filters = settleFromStore(focusNote, defaultRelays, askedAuthors, storeQueried)
            val pending = filters.filter { sent.add(it.relay.url + it.filter.toJson()) }
            if (pending.isEmpty()) break
            queried += pending.map { it.relay }

            val before = loaded(focusNote)
            notes.addAll(access.fetch(pending.groupBy({ it.relay }, { it.filter }), timeoutMs))
            if (loaded(focusNote) == before) break
        }

        val me = notes.user(viewer)
        val following = access.contactListOf(viewer)?.verifiedFollowKeySet() ?: emptySet()
        val ordered = ThreadFeedFilter.thread(focusNote.idHex, notes.cache, me, following)
        val root = ThreadAssembler(notes.cache).findRoot(focusNote.idHex)
        return LoadedThread(focusNote, root, ordered, queried)
    }

    private fun loaded(focus: Note): Int = ThreadAssembler(notes.cache).findThreadFor(focus.idHex)?.allNotes?.count { it.event != null } ?: 0

    /**
     * Applies the thread screen's filters to the store until they stop finding anything,
     * loading the relay lists of authors met along the way (that is where the filters look
     * next), and returns the filters for the relays. Each distinct filter reads the store once
     * per load ([storeQueried]): what relays return later goes into the cache directly, and the
     * root's `#e` filter alone can match every reaction a popular note ever got.
     */
    private suspend fun settleFromStore(
        focus: Note,
        defaultRelays: Set<NormalizedRelayUrl>,
        askedAuthors: MutableSet<HexKey>,
        storeQueried: MutableSet<String>,
    ): List<RelayBasedFilter> {
        var filters: List<RelayBasedFilter> = emptyList()
        for (pass in 0 until MAX_STORE_PASSES) {
            val assembler = ThreadAssembler(notes.cache)
            val info = assembler.findThreadFor(focus.idHex) ?: return filters
            val root = assembler.findRoot(focus.idHex) ?: focus

            val authors = info.allNotes.mapNotNullTo(mutableSetOf()) { it.author?.pubkeyHex } - askedAuthors
            if (authors.isNotEmpty()) {
                askedAuthors += authors
                notes.addUsers(authors, fetchMissing = true, alsoAsk = root.relays)
            }

            filters = filterEventsInThreadForRoot(root, null, defaultRelays) + filterMissingEventsForThread(notes.cache, info, defaultRelays)
            val before = loaded(focus)
            val local = filters.filter { storeQueried.add(it.filter.toJson()) }.flatMap { access.query(it.filter) }
            notes.addAll(local.map { null to it })
            if (loaded(focus) == before && authors.isEmpty()) return filters
        }
        return filters
    }

    companion object {
        const val MAX_ROUNDS = 5
        private const val MAX_STORE_PASSES = 10
    }
}
