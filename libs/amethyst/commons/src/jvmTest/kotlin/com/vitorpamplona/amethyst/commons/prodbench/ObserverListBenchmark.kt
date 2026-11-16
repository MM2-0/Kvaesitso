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
package com.vitorpamplona.amethyst.commons.prodbench

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.observables.NoteListMatchingFilter
import com.vitorpamplona.amethyst.commons.model.observables.Observable
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.utils.EventFactory
import java.lang.management.ManagementFactory
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Measures [NoteListMatchingFilter], the list every `observeNotes` / `observeEvents` screen
 * sits behind, against the `ConcurrentSkipListSet` implementation it replaced.
 *
 * It is on a hot path: every consumed event is offered to each observer whose filter could
 * match it, from each relay's socket coroutine, so a few hundred events a second across a
 * dozen relays reach this code thousands of times a second. The rewrite to copy-on-write was
 * made to get the class into `commonMain` (`java.util.concurrent` has no KMP equivalent);
 * that is a portability argument, and this file is the performance one, which is a separate
 * question and has to be answered with numbers rather than asymptotics.
 *
 * [SkipListReference] is the previous implementation, kept verbatim as the baseline. It also
 * serves as a differential oracle: [bothImplementationsAgree] asserts the two produce identical
 * output for identical input, which is the property the rewrite actually had to preserve.
 *
 * Deterministic and offline. Prints ns/op; asserts only on correctness, never on wall time,
 * since CI machines vary.
 */
class ObserverListBenchmark {
    private val author = "d0d0a746b44c9de8422165aef520b1fe041eedf5794f7592505477eeac122c18"

    /** Touches the emitted list so neither implementation's O(n) materialization is dead code. */
    private val blackhole = AtomicLong()

    private fun sink(list: List<Note>) {
        blackhole.addAndGet(list.size.toLong() + if (list.isEmpty()) 0 else list[0].idHex.length.toLong())
    }

    private fun event(
        i: Int,
        createdAt: Long,
    ): Event =
        EventFactory.create(
            id = "%064x".format(i),
            pubKey = author,
            createdAt = createdAt,
            kind = TextNoteEvent.KIND,
            tags = emptyArray(),
            content = "n",
            sig = "00".repeat(64),
        )

    /**
     * createdAt is deliberately NOT monotonic in i: a real feed arrives out of order, so the
     * insert lands mid-list rather than always at the head, which is the cheapest case for
     * both implementations and would flatter whichever one is worse.
     */
    private fun fixtures(n: Int): List<Pair<Event, Note>> {
        val rnd = Random(42)
        return (0 until n).map { i ->
            val e = event(i, 1_700_000_000L + rnd.nextInt(n * 4))
            val note = Note(e.id)
            note.event = e
            e to note
        }
    }

    // Started, as the cache starts every observer: an unstarted one does not emit.
    private fun cow(f: Filter) = NoteListMatchingFilter(f, { emptyList() }, ::sink).also { it.init() }

    private fun skipList(f: Filter) = SkipListReference(f, ::sink)

    // ---------------------------------------------------------------------- harness

    private fun bench(
        label: String,
        reps: Int = 5,
        warmups: Int = 2,
        body: () -> Int,
    ): Double {
        repeat(warmups) { body() }
        val times = ArrayList<Double>(reps)
        repeat(reps) {
            val start = System.nanoTime()
            val ops = body()
            times.add((System.nanoTime() - start).toDouble() / ops)
        }
        times.sort()
        val median = times[times.size / 2]
        println("  %-44s %9.0f ns/op %11.0f ops/s".format(label, median, 1_000_000_000.0 / median))
        return median
    }

    private fun compare(
        name: String,
        cowNs: Double,
        skipNs: Double,
    ) {
        val ratio = cowNs / skipNs
        val verdict =
            when {
                ratio < 0.95 -> "copy-on-write faster x%.2f".format(1 / ratio)
                ratio > 1.05 -> "copy-on-write slower x%.2f".format(ratio)
                else -> "parity"
            }
        println("  -> $name: $verdict\n")
    }

    // ------------------------------------------------------------------ correctness

    @Test
    fun bothImplementationsAgree() {
        val fx = fixtures(500)
        for (limit in listOf(null, 50, 400)) {
            val f = Filter(kinds = listOf(TextNoteEvent.KIND), limit = limit)

            var fromCow: List<Note> = emptyList()
            val a = NoteListMatchingFilter(f, { emptyList() }, { fromCow = it }).also { it.init() }
            var fromSkipList: List<Note> = emptyList()
            val b = SkipListReference(f) { fromSkipList = it }

            fx.forEach { (e, note) -> a.new(e, note) }
            fx.forEach { (e, note) -> b.new(e, note) }

            assertEquals(
                fromSkipList.map { it.idHex },
                fromCow.map { it.idHex },
                "copy-on-write must emit exactly what the skip list emitted (limit=$limit)",
            )
            assertEquals(fromCow.size, fromCow.map { it.idHex }.toSet().size, "no duplicate keys")

            // Re-delivering an already listed note must be a no-op in both.
            fx.take(50).forEach { (e, note) -> a.new(e, note) }
            fx.take(50).forEach { (e, note) -> b.new(e, note) }
            assertEquals(fromSkipList.map { it.idHex }, fromCow.map { it.idHex }, "re-delivery must not change either list")
        }
    }

    // ------------------------------------------------------------------- serial load

    @Test
    fun insertIntoAPopulatedList() {
        // No limit: that is what nearly every production observer passes, so the list grows
        // to whatever the cache holds for that kind.
        println("\n== insert into a list already holding n (seed excluded from timing, no limit) ==")
        for (n in listOf(100, 1000)) {
            val seed = fixtures(n)
            val arrivals = fixtures(n * 2).drop(n).map { (e, _) -> e }
            val f = Filter(kinds = listOf(TextNoteEvent.KIND))

            fun run(make: () -> Observable): () -> Int =
                {
                    val subject = make()
                    seed.forEach { (e, note) -> subject.new(e, note) }
                    val notes =
                        arrivals.map { e ->
                            val note = Note(e.id)
                            note.event = e
                            e to note
                        }
                    val t0 = System.nanoTime()
                    notes.forEach { (e, note) -> subject.new(e, note) }
                    elapsed.addAndGet(System.nanoTime() - t0)
                    arrivals.size
                }

            val c = benchInner("copy-on-write insert @$n", run { cow(f) })
            val s = benchInner("skip list     insert @$n", run { skipList(f) })
            compare("insert @n=$n", c, s)
        }
    }

    private val elapsed = AtomicLong()

    /** Reports only the span the body accumulated into [elapsed], excluding its setup. */
    private fun benchInner(
        label: String,
        body: () -> Int,
        reps: Int = 5,
        warmups: Int = 2,
    ): Double {
        repeat(warmups) {
            elapsed.set(0)
            body()
        }
        val times = ArrayList<Double>(reps)
        repeat(reps) {
            elapsed.set(0)
            val ops = body()
            times.add(elapsed.get().toDouble() / ops)
        }
        times.sort()
        val median = times[times.size / 2]
        println("  %-44s %9.0f ns/op %11.0f ops/s".format(label, median, 1_000_000_000.0 / median))
        return median
    }

    @Test
    fun reDeliveryOfAnAlreadyListedNote() {
        // The dominant steady-state call once a screen is warm: a newer version of an
        // addressable, or the same note arriving from a second relay.
        println("\n== re-delivery of a note already in the list ==")
        for (n in listOf(100, 1000)) {
            val fx = fixtures(n)
            val f = Filter(kinds = listOf(TextNoteEvent.KIND), limit = n)
            val reps = 100_000

            val a = cow(f).also { s -> fx.forEach { (e, note) -> s.new(e, note) } }
            val b = skipList(f).also { s -> fx.forEach { (e, note) -> s.new(e, note) } }

            val c =
                bench("copy-on-write re-deliver @$n") {
                    for (i in 0 until reps) {
                        val (e, note) = fx[i % n]
                        a.new(e, note)
                    }
                    reps
                }
            val s =
                bench("skip list     re-deliver @$n") {
                    for (i in 0 until reps) {
                        val (e, note) = fx[i % n]
                        b.new(e, note)
                    }
                    reps
                }
            compare("re-deliver n=$n", c, s)
        }
    }

    // ------------------------------------------------------------ allocation / GC

    /**
     * Bytes allocated per operation, which is the GC-pressure question: a phone pays for young-gen
     * churn in jank, not just in CPU. Measured with HotSpot's per-thread allocation counter, so it
     * is exact rather than inferred from heap deltas, and single-threaded so the counter is this
     * work and nothing else.
     */
    private fun allocatedBytes(): Long {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        return bean.getThreadAllocatedBytes(Thread.currentThread().threadId())
    }

    private fun measureAllocation(
        label: String,
        warmups: Int = 2,
        body: () -> Int,
    ): Double {
        repeat(warmups) { body() }
        val before = allocatedBytes()
        val ops = body()
        val bytes = (allocatedBytes() - before).toDouble() / ops
        println("  %-44s %12.0f bytes/op".format(label, bytes))
        return bytes
    }

    private fun compareBytes(
        name: String,
        cowBytes: Double,
        skipBytes: Double,
    ) {
        val ratio = cowBytes / skipBytes
        val verdict =
            when {
                ratio < 0.95 -> "copy-on-write allocates %.2fx LESS".format(1 / ratio)
                ratio > 1.05 -> "copy-on-write allocates %.2fx MORE".format(ratio)
                else -> "parity"
            }
        println("  -> $name: $verdict\n")
    }

    @Test
    fun allocationPerOperation() {
        println("\n== bytes allocated per operation (GC pressure) ==")

        // (a) The re-delivery path: the dominant steady-state call once a screen is warm.
        for (n in listOf(100, 1000)) {
            val fx = fixtures(n)
            val f = Filter(kinds = listOf(TextNoteEvent.KIND), limit = n)
            val a = cow(f).also { s -> fx.forEach { (e, note) -> s.new(e, note) } }
            val b = skipList(f).also { s -> fx.forEach { (e, note) -> s.new(e, note) } }
            val reps = 50_000

            val c =
                measureAllocation("copy-on-write re-deliver @$n") {
                    for (i in 0 until reps) {
                        val (e, note) = fx[i % n]
                        a.new(e, note)
                    }
                    reps
                }
            val sk =
                measureAllocation("skip list     re-deliver @$n") {
                    for (i in 0 until reps) {
                        val (e, note) = fx[i % n]
                        b.new(e, note)
                    }
                    reps
                }
            compareBytes("re-deliver n=$n", c, sk)
        }

        // (b) The insert path, where copy-on-write copies the list and the skip list does not.
        for (n in listOf(100, 1000)) {
            val seed = fixtures(n)
            val arrivals = fixtures(n * 2).drop(n).map { (e, _) -> e }
            val f = Filter(kinds = listOf(TextNoteEvent.KIND))

            fun run(make: () -> Observable): () -> Int =
                {
                    val subject = make()
                    seed.forEach { (e, note) -> subject.new(e, note) }
                    val notes =
                        arrivals.map { e ->
                            val note = Note(e.id)
                            note.event = e
                            e to note
                        }
                    val before = allocatedBytes()
                    notes.forEach { (e, note) -> subject.new(e, note) }
                    alloc.addAndGet(allocatedBytes() - before)
                    arrivals.size
                }

            fun measureInner(
                label: String,
                body: () -> Int,
            ): Double {
                repeat(2) {
                    alloc.set(0)
                    body()
                }
                alloc.set(0)
                val ops = body()
                val bytes = alloc.get().toDouble() / ops
                println("  %-44s %12.0f bytes/op".format(label, bytes))
                return bytes
            }

            val c = measureInner("copy-on-write insert @$n", run { cow(f) })
            val sk = measureInner("skip list     insert @$n", run { skipList(f) })
            compareBytes("insert @n=$n", c, sk)
        }
        println("  blackhole=${blackhole.get()}")
    }

    private val alloc = AtomicLong()

    /**
     * The one place copy-on-write could lose on allocation: a lost CAS throws away the copy it
     * just built and retries, so contention multiplies the bytes per insert. The skip list has no
     * such amplification. Summed across the worker threads, since the counter is per-thread.
     */
    @Test
    fun allocationUnderContention() {
        println("\n== bytes allocated per insert under contention (CAS retry amplification) ==")
        val ops = 4_000
        val fx = fixtures(ops)
        val f = Filter(kinds = listOf(TextNoteEvent.KIND), limit = 1000)

        fun run(
            threads: Int,
            subject: Observable,
        ): Double {
            val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
            val total = AtomicLong()
            val start = CountDownLatch(1)
            val done = CountDownLatch(threads)
            repeat(threads) { t ->
                thread {
                    start.await()
                    val before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId())
                    var i = t
                    while (i < ops) {
                        val (e, note) = fx[i % fx.size]
                        subject.new(e, note)
                        i += threads
                    }
                    total.addAndGet(bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before)
                    done.countDown()
                }
            }
            start.countDown()
            done.await()
            return total.get().toDouble() / ops
        }

        for (threads in listOf(1, 4, 8)) {
            run(threads, cow(f))
            run(threads, skipList(f))
            val c = run(threads, cow(f))
            val sk = run(threads, skipList(f))
            println("  %-44s %12.0f bytes/op".format("copy-on-write insert x$threads", c))
            println("  %-44s %12.0f bytes/op".format("skip list     insert x$threads", sk))
            compareBytes("insert x$threads", c, sk)
        }
        println("  blackhole=${blackhole.get()}")
    }

    // --------------------------------------------------------------- concurrent load

    private fun concurrently(
        threads: Int,
        ops: Int,
        subject: Observable,
        fx: List<Pair<Event, Note>>,
    ): Int {
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) { t ->
            thread {
                start.await()
                var i = t
                while (i < ops) {
                    val (e, note) = fx[i % fx.size]
                    subject.new(e, note)
                    i += threads
                }
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        return ops
    }

    @Test
    fun concurrentInsertsFromSeveralRelayThreads() {
        // Worst case on purpose: these threads do nothing but insert. Real ingest spends most
        // of its per-event budget on signature verification and parsing before reaching an
        // observer, so actual contention on one filter is a fraction of this.
        println("\n== concurrent inserts, threads doing nothing but inserting (worst case) ==")
        val ops = 4_000
        val fx = fixtures(ops)
        val f = Filter(kinds = listOf(TextNoteEvent.KIND), limit = 1000)
        for (threads in listOf(1, 4, 8)) {
            val c = bench("copy-on-write concurrent x$threads", reps = 3, warmups = 1) { concurrently(threads, ops, cow(f), fx) }
            val s = bench("skip list     concurrent x$threads", reps = 3, warmups = 1) { concurrently(threads, ops, skipList(f), fx) }
            compare("concurrent x$threads", c, s)
        }
        println("  blackhole=${blackhole.get()}")
    }

    /**
     * [NoteListMatchingFilter] as it stood before the copy-on-write rewrite, verbatim apart from
     * dropping the unused `atOnce`/`init` pair. Kept as the baseline these numbers are measured
     * against, and as the oracle in [bothImplementationsAgree].
     *
     * It is lock-free too -- `byId` is a ConcurrentHashMap, striped per key, and every write to
     * `sorted` for an idHex happens inside that key's `compute` section -- which is why the
     * comparison is about speed and portability, not about locking.
     */
    private class SkipListReference(
        private val filter: Filter,
        private val update: (List<Note>) -> Unit,
    ) : Observable {
        private class Entry(
            val note: Note,
            val createdAt: Long,
            val id: HexKey,
        )

        private val order =
            Comparator<Entry> { a, b ->
                val byCreatedAt = b.createdAt.compareTo(a.createdAt)
                if (byCreatedAt != 0) byCreatedAt else a.id.compareTo(b.id)
            }

        private val sorted = ConcurrentSkipListSet(order)
        private val byId = ConcurrentHashMap<HexKey, Entry>()

        private fun entryFor(note: Note) = Entry(note, note.createdAt() ?: Long.MIN_VALUE, note.event?.id ?: note.idHex)

        override fun new(
            event: Event,
            note: Note,
        ) {
            if (event is AddressableEvent && note !is AddressableNote) return
            if (!filter.match(event)) return

            var added = false
            byId.compute(note.idHex) { _, existing ->
                existing ?: entryFor(note).also {
                    sorted.add(it)
                    added = true
                }
            }
            if (!added) return

            val limit = filter.limit
            if (limit != null && sorted.size > limit) {
                sorted.pollLast()?.let { byId.remove(it.note.idHex, it) }
            }

            update(snapshot())
        }

        override fun remove(note: Note) {
            var removed = false
            byId.compute(note.idHex) { _, existing ->
                if (existing != null) {
                    sorted.remove(existing)
                    removed = true
                }
                null
            }
            if (removed) update(snapshot())
        }

        /** The skip list's iterator is only weakly consistent, so this had to strip duplicates. */
        private fun snapshot(): List<Note> {
            val seen = HashSet<HexKey>()
            return sorted.mapNotNull { e -> e.note.takeIf { seen.add(it.idHex) } }
        }
    }
}
