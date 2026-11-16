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
@file:OptIn(ExperimentalAtomicApi::class)

package com.vitorpamplona.amethyst.commons.model.observables

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Creates a list of notes (regular and addressable), sorted by created_at like a
 * relay, that only grows when a new note appears.
 *
 * New versions of addressables do not update the list.
 *
 * There is exactly one [Note] instance per id/address (the cache owns their creation), so
 * uniqueness is a non-issue in principle — except a note's sort key is mutable: a newer
 * replaceable event swaps the event on the SAME [AddressableNote] instance, changing its
 * created_at in place. Anything ordered on that live value cannot survive it. So the sort key is
 * snapshotted into an immutable [Entry] when the note first enters and never read live again.
 *
 * **Lock-free, by copy-on-write.** Observer callbacks fire concurrently from several consume
 * threads (relay ingest + UI-side justConsume) and this observer is used everywhere, so it takes
 * no lock: the whole list lives in one immutable [State] behind an [AtomicReference], and every
 * mutation builds the next state and publishes it with a compare-and-set, retrying if another
 * thread won the race. Same shape quartz uses for its native `ConcurrentMap`.
 *
 * Two things fall out of that, both previously paid for by hand:
 *  - a reader never sees a torn list, so the emitted snapshot needs no de-duplication pass —
 *    one entry per idHex holds by construction, since [State.ids] is what gates insertion;
 *  - the emitted list IS the published state, so emitting costs one map rather than a walk of a
 *    concurrent structure plus a `HashSet` to strip the duplicates that walk could surface.
 *
 * The copy is O(n) per write, which is what the previous per-emit snapshot already cost.
 *
 * **Measured**, against the `ConcurrentSkipListSet` implementation this replaced — see
 * `ObserverListBenchmark`, which keeps that implementation as the baseline and as a
 * differential oracle:
 *  - re-delivery of an already listed note (the steady state once a screen is warm): 3.3–4.1x
 *    faster, because it is a load plus a set lookup where the skip list took a `compute`;
 *  - insert into a populated unlimited list (what nearly every observer registers): 1.3–1.5x
 *    faster at n = 100–1000, widening with n;
 *  - concurrent inserts into the SAME observer: at parity on one thread, 2.1–2.7x slower on
 *    4–8. That is the honest cost of this design: threads serialize on one reference and a
 *    lost CAS throws away its copy, where the skip list striped across keys and scaled. It is
 *    measured with threads doing nothing but inserting; real ingest spends most of its
 *    per-event budget verifying signatures and parsing before it reaches an observer, so the
 *    contention window is a fraction of that. Worth revisiting if a profile ever says
 *    otherwise.
 *
 * **Garbage**, measured the same way, since on a phone young-gen churn is paid in jank rather
 * than in CPU: re-delivery allocates **nothing at all** (a load and a set lookup — where the
 * skip list's `compute` allocated a capturing lambda and a boxed `Ref.BooleanRef`, 56 bytes on
 * every one of the commonest call in the app), and a single-threaded insert allocates 1.2x less
 * than the skip list did. Under contention it inverts for the same reason the timing does — a
 * lost CAS discards the copy it just built — reaching 2.0x more on 4 threads and 2.4x on 8.
 *
 * What neither implementation is, is cheap per insert: both materialize the whole list on every
 * write, so an observer holding a thousand notes allocates ~78 KB each time one arrives. That is
 * unchanged by this rewrite and is where the next win is, if one is wanted — not in the locking.
 */
class NoteListMatchingFilter(
    private val filter: Filter,
    private val atOnce: (filter: Filter) -> List<Note>,
    private val update: (List<Note>) -> Unit,
) : Observable {
    /** A note plus the sort key captured at insertion time, so ordering never depends on mutable state. */
    private class Entry(
        val note: Note,
        val createdAt: Long,
        val id: HexKey,
    )

    /** One immutable version of the list. [ids] is the membership gate, keyed by the stable idHex. */
    private class State(
        val entries: List<Entry>,
        val ids: Set<HexKey>,
    ) {
        fun notes(): List<Note> = entries.map { it.note }

        companion object {
            val EMPTY = State(emptyList(), emptySet())
        }
    }

    // created_at descending, id ascending as a stable tiebreak. Both fields are
    // immutable snapshots, so an Entry never moves once inserted.
    private val order =
        Comparator<Entry> { a, b ->
            val byCreatedAt = b.createdAt.compareTo(a.createdAt)
            if (byCreatedAt != 0) byCreatedAt else a.id.compareTo(b.id)
        }

    private val state = AtomicReference(State.EMPTY)

    // Ids [remove] was given while [start] ran: its scan may have collected them before they went.
    private val removedWhileStarting = AtomicReference<Set<HexKey>>(emptySet())

    // False until [start] has published its first list.
    @Volatile
    private var started = false

    private fun entryFor(note: Note): Entry {
        // A null event (unresolved note) sorts last, matching CreatedAtIdHexComparator.
        val event = note.event
        return Entry(note, note.createdAt() ?: Long.MIN_VALUE, event?.id ?: note.idHex)
    }

    private fun State.plus(
        entry: Entry,
        limit: Int?,
    ): State {
        val found = entries.binarySearch(entry, order)
        val at = if (found < 0) -found - 1 else found

        val grown = ArrayList<Entry>(entries.size + 1)
        grown.addAll(entries.subList(0, at))
        grown.add(entry)
        grown.addAll(entries.subList(at, entries.size))

        // One copy of the membership set per write. `ids + added` and `ids + added - dropped`
        // read well but allocate one full set per operator, so the limit path paid for two;
        // measured, that was the whole of this implementation's deficit against the skip list
        // on a limited filter.
        val nextIds = HashSet<HexKey>(((entries.size + 2) / 0.75f).toInt() + 1)
        nextIds.addAll(ids)
        nextIds.add(entry.note.idHex)

        if (limit == null || grown.size <= limit) return State(grown, nextIds)

        // Over the limit: drop the oldest, which sorts last. That can be the entry just
        // inserted, and then it is simply not listed — as the previous pollLast() did.
        val dropped = grown.removeAt(grown.size - 1)
        nextIds.remove(dropped.note.idHex)
        return State(grown, nextIds)
    }

    private fun State.minus(idHex: HexKey): State = State(entries.filterNot { it.note.idHex == idHex }, ids - idHex)

    override fun new(
        event: Event,
        note: Note,
    ) {
        if (event is AddressableEvent && note !is AddressableNote) return

        if (!filter.match(event)) return

        val limit = filter.limit
        while (true) {
            val current = state.load()
            // Already listed: a newer version of an addressable keeps its entry, and its
            // position with it, so the list only ever grows.
            if (note.idHex in current.ids) return

            val next = current.plus(entryFor(note), limit)
            if (state.compareAndSet(current, next)) {
                send(next)
                return
            }
        }
    }

    override fun remove(note: Note) {
        if (!started) {
            while (true) {
                val removed = removedWhileStarting.load()
                if (removedWhileStarting.compareAndSet(removed, removed + note.idHex)) break
            }
        }
        while (true) {
            val current = state.load()
            if (note.idHex !in current.ids) return

            val next = current.minus(note.idHex)
            if (state.compareAndSet(current, next)) {
                send(next)
                return
            }
        }
    }

    /** [start] for an observer no cache delivers to: a test, or a list built once. */
    fun init() = start {}

    /**
     * Fills the list from the cache and publishes it. [register] hands this observer to the
     * cache first, so an event inserted while the scan runs reaches [new] and is merged in, and a
     * note deleted meanwhile is left out; scanning first lost both, as the cache had nobody to
     * tell. Until the merged list is published [new] and [remove] only change the state, so a
     * collector's first list is the whole one.
     */
    fun start(register: () -> Unit) {
        register()
        val scanned = stateOf(atOnce(filter))
        while (true) {
            val current = state.load()
            val next = scanned.without(removedWhileStarting.load()).mergedWith(current)
            if (state.compareAndSet(current, next)) break
        }
        // A deletion that landed after the merge read the set, but found nothing to remove yet.
        dropRemovedWhileStarting()
        started = true
        removedWhileStarting.store(emptySet())
        send(state.load())
    }

    /**
     * Sends [published], once [start] has published the first list. Threads publish in one order
     * and can send in another: one that published an older state may send after a newer one was
     * sent, and the collector, conflated, keeps the last. So it sends again until the state stops
     * moving, and the newest list is always the last one sent. Alone, that is one extra load.
     */
    private fun send(published: State) {
        if (!started) return
        var sent = published
        update(sent.notes())
        while (true) {
            val now = state.load()
            if (now === sent) return
            sent = now
            update(now.notes())
        }
    }

    /**
     * The cache query's notes as one state: sorted once and de-duplicated by id. Inserting them
     * one by one copied the whole list and its id set for each, O(n²) in time and garbage.
     */
    private fun stateOf(notes: List<Note>): State {
        val ids = HashSet<HexKey>(((notes.size + 1) / 0.75f).toInt() + 1)
        val entries = ArrayList<Entry>(notes.size)
        for (note in notes) {
            if (ids.add(note.idHex)) entries.add(entryFor(note))
        }
        entries.sortWith(order)
        return State(entries, ids)
    }

    private fun State.without(removed: Set<HexKey>): State = if (removed.none { it in ids }) this else State(entries.filterNot { it.note.idHex in removed }, ids - removed)

    /** This scanned state plus what [new] listed while the scan ran, the limit kept. */
    private fun State.mergedWith(live: State): State {
        var next = this
        for (entry in live.entries) {
            if (entry.note.idHex !in next.ids) next = next.plus(entry, filter.limit)
        }
        return next
    }

    private fun dropRemovedWhileStarting() {
        while (true) {
            val current = state.load()
            val next = current.without(removedWhileStarting.load())
            if (next === current || state.compareAndSet(current, next)) return
        }
    }
}
