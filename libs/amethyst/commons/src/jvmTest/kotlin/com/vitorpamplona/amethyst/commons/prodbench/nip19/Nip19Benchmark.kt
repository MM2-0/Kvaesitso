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
package com.vitorpamplona.amethyst.commons.prodbench.nip19

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32.tlv.TlvBuilder
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import java.lang.management.ManagementFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Encode/decode cost of every NIP-19 entity, as the app calls quartz.
 *
 * Encoding is what the UI pays: `Note.toNostrUri()` (an `nevent`/`naddr`) is the `callbackUri`
 * of almost every rendered note, and `User.pubkeyNpub()` / `NPub.create` back every short
 * display name. Decoding is what ingest pays: `Nip19Parser.parseAll` decodes every `nostr:`
 * reference of every incoming note.
 *
 * Quartz 2.2.0 replaced a boxed, multi-copy codec with a single-pass one; the before/after table
 * is in `commons/plans/2026-10-23-nip19-codec-speed.md`. Rerun this to compare against it.
 *
 * Deterministic and offline. Prints ns/op and bytes allocated/op; the assertions only check that
 * every entity round-trips, never wall time (CI machines vary).
 */
class Nip19Benchmark {
    companion object {
        const val N = 1024
        val rnd = Random(42)

        fun hex32() = rnd.nextBytes(32).toHexKey()

        val relays =
            listOf(
                "wss://relay.damus.io/",
                "wss://nos.lol/",
                "wss://relay.primal.net/",
                "wss://nostr.wine/",
                "wss://relay.nostr.band/",
                "wss://nostr-pub.wellorder.net/",
            ).map { RelayUrlNormalizer.normalizeOrNull(it)!! }

        val ids = List(N) { hex32() }
        val authors = List(N) { hex32() }
        val kinds = List(N) { listOf(1, 1, 1, 6, 7, 20, 1111, 9802)[it % 8] }
        val relayHints: List<NormalizedRelayUrl?> = List(N) { if (it % 5 == 0) null else relays[it % relays.size] }
        val relayLists = List(N) { i -> List(i % 4) { relays[(i + it) % relays.size] } }
        val dTags = List(N) { "my-long-form-article-${it * 7919}" }

        private val threadBean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

        data class Row(
            val label: String,
            val ns: Double,
            val bytes: Double,
        )

        /** Best-of-5 rounds of [reps] calls over the N inputs; also reports bytes allocated/op. */
        fun bench(
            label: String,
            reps: Int = 40,
            op: (Int) -> Any?,
        ): Row {
            var sink = 0
            repeat(reps / 2) { for (i in 0 until N) sink += op(i).hashCode() } // warmup
            var best = Double.MAX_VALUE
            var bestBytes = 0.0
            repeat(5) {
                val a0 = threadBean.currentThreadAllocatedBytes
                val t0 = System.nanoTime()
                repeat(reps) { for (i in 0 until N) sink += op(i).hashCode() }
                val ns = (System.nanoTime() - t0).toDouble() / (reps.toLong() * N)
                val bytes = (threadBean.currentThreadAllocatedBytes - a0).toDouble() / (reps.toLong() * N)
                if (ns < best) {
                    best = ns
                    bestBytes = bytes
                }
            }
            if (sink == 42) println() // keep the results alive
            return Row(label, best, bestBytes)
        }

        fun print(row: Row) = println(String.format("%-30s %8.0f ns %7.0f B", row.label, row.ns, row.bytes))

        fun body(nip19: String) = nip19.substring(nip19.indexOf('1') + 1)
    }

    private fun npub(i: Int) = NPub.create(authors[i])

    private fun nsec(i: Int) = ids[i].hexToByteArray().toNsec()

    private fun note(i: Int) = NNote.create(ids[i])

    private fun nevent(i: Int) = NEvent.create(ids[i], authors[i], kinds[i], relayHints[i])

    private fun nprofile(i: Int) = NProfile.create(authors[i], relayLists[i])

    private fun naddr(i: Int) = NAddress.create(30023, authors[i], dTags[i], relayHints[i])

    private fun nrelay(i: Int) = TlvBuilder().apply { addString(0, relays[i % relays.size].url) }.build().let { Bech32.encodeBytes("nrelay", it, Bech32.Encoding.Bech32) }

    @Test
    fun everyEntityRoundTrips() {
        for (i in 0 until N) {
            assertEquals(NPub(authors[i]), Nip19Parser.uriToRoute(npub(i))?.entity)
            assertEquals(NSec(ids[i]), Nip19Parser.uriToRoute(nsec(i))?.entity)
            assertEquals(NNote(ids[i]), Nip19Parser.uriToRoute(note(i))?.entity)
            assertEquals(NEvent(ids[i], listOfNotNull(relayHints[i]), authors[i], kinds[i]), Nip19Parser.uriToRoute(nevent(i))?.entity)
            assertEquals(NProfile(authors[i], relayLists[i]), Nip19Parser.uriToRoute(nprofile(i))?.entity)
            assertEquals(NAddress(30023, authors[i], dTags[i], listOfNotNull(relayHints[i])), Nip19Parser.uriToRoute(naddr(i))?.entity)
            assertEquals(NRelay(listOf(relays[i % relays.size].url)), Nip19Parser.uriToRoute(nrelay(i))?.entity)
            assertEquals("nostr:" + nevent(i), NEvent.createNostrUri(ids[i], authors[i], kinds[i], relayHints[i]))
        }
    }

    @Test
    fun encoders() {
        println("NIP-19 encode (per call; inputs vary over $N ids/keys/relays)")
        print(bench("npub (NPub.create)", op = ::npub))
        print(bench("nsec", op = ::nsec))
        print(bench("note (NNote.create)", op = ::note))
        print(bench("nevent id+author+kind+relay", op = ::nevent))
        print(bench("nevent id only") { NEvent.create(ids[it], null, null, null as NormalizedRelayUrl?) })
        print(bench("nprofile 0-3 relays", op = ::nprofile))
        print(bench("naddr kind+author+d+relay", op = ::naddr))
        print(bench("nrelay", op = ::nrelay))

        println("NIP-21 uri (the createNostrUri helpers behind toNostrUri())")
        print(bench("nostr:nevent") { NEvent.createNostrUri(ids[it], authors[it], kinds[it], relayHints[it]) })
        print(bench("nostr:nprofile") { NProfile.createNostrUri(authors[it], relayLists[it]) })
        print(bench("nostr:naddr") { NAddress.createNostrUriOrNull(30023, authors[it], dTags[it], relayHints[it]) })
    }

    @Test
    fun decoders() {
        val npubs = List(N) { npub(it) }
        val nsecs = List(N) { nsec(it) }
        val notes = List(N) { note(it) }
        val nevents = List(N) { nevent(it) }
        val nprofiles = List(N) { nprofile(it) }
        val naddrs = List(N) { naddr(it) }
        val nrelays = List(N) { nrelay(it) }

        println("NIP-19 decode, raw bech32 -> bytes")
        print(bench("npub decodeBytes") { Bech32.decodeBytes(npubs[it]).second })
        print(bench("npub decodeBytesOrNull") { Bech32.decodeBytesOrNull(npubs[it], "npub") })
        print(bench("nevent decodeBytesOrNull") { Bech32.decodeBytesOrNull(nevents[it], "nevent") })

        println("NIP-19 decode to entity (Nip19Parser.parseComponents)")
        print(bench("npub") { Nip19Parser.parseComponents("npub1", body(npubs[it]), null) })
        print(bench("nsec") { Nip19Parser.parseComponents("nsec1", body(nsecs[it]), null) })
        print(bench("note") { Nip19Parser.parseComponents("note1", body(notes[it]), null) })
        print(bench("nevent") { Nip19Parser.parseComponents("nevent1", body(nevents[it]), null) })
        print(bench("nprofile") { Nip19Parser.parseComponents("nprofile1", body(nprofiles[it]), null) })
        print(bench("naddr") { Nip19Parser.parseComponents("naddr1", body(naddrs[it]), null) })
        print(bench("nrelay") { Nip19Parser.parseComponents("nrelay1", body(nrelays[it]), null) })

        println("Content scan and uri routing")
        val notesWithRefs = List(N) { "gm nostr:${npubs[it]} look at nostr:${nevents[it]} and nostr:${naddrs[it]} ok" }
        print(bench("parseAll, 3 refs in a note") { Nip19Parser.parseAll(notesWithRefs[it]).size })
        print(bench("uriToRoute nevent") { Nip19Parser.uriToRoute("nostr:" + nevents[it]) })
        print(bench("uriToRoute npub") { Nip19Parser.uriToRoute("nostr:" + npubs[it]) })
    }

    /**
     * What the UI actually calls: `Note.toNostrUri()` and `User.toNostrUri()`, with an author whose
     * kind:10002 lists 8 relays (the common shape) and a note seen on 3 relays. Split into the
     * relay-hint lookup and the encode, to show which half the time goes to.
     */
    @Test
    fun appToNostrUri() {
        val users =
            List(N) { i ->
                User(authors[i]) { addr -> AddressableNote(addr) }.also { u ->
                    val tags = Array(8) { r -> arrayOf("r", relays[(i + r) % relays.size].url + if (r >= relays.size) "x" else "") }
                    u.nip65RelayListNote.event = AdvertisedRelayListEvent(hex32(), authors[i], 1L, tags, "", "")
                }
            }
        val notes =
            List(N) { i ->
                Note(ids[i]).also { n ->
                    n.event = TextNoteEvent(ids[i], authors[i], 1L, emptyArray(), "hello", "")
                    n.author = users[i]
                    repeat(3) { r -> n.addRelaySync(relays[(i + r * 2) % relays.size]) }
                }
            }

        println("App call sites (commons model)")
        print(bench("Note.toNostrUri()") { notes[it].toNostrUri() })
        print(bench("  Note.relayHintUrl()") { notes[it].relayHintUrl() })
        print(bench("  NEvent.createNostrUri") { NEvent.createNostrUri(ids[it], authors[it], 1, relays[it % relays.size]) })
        print(bench("User.toNostrUri()") { users[it].toNostrUri() })
        print(bench("  User.relayHints()") { users[it].relayHints() })
        print(bench("User.pubkeyNpub()") { users[it].pubkeyNpub() })
        print(bench("User.pubkeyDisplayHex()") { users[it].pubkeyDisplayHex() })
    }
}
