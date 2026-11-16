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

import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser.Companion.tagIndex
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findIndexTagsWithEventsOrAddresses
import com.vitorpamplona.quartz.nip10Notes.content.findIndexTagsWithPeople
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.hashtagSearch
import com.vitorpamplona.quartz.nip10Notes.content.tagSearch
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import kotlin.test.Test

/**
 * Measures the regex scans Amethyst runs over note **content**.
 *
 * Why: an on-device heap dump (SM-T220, Dr. Edo's account) found 2,541 of 4,573
 * live `java.util.regex.Matcher` instances running [Nip19Parser.nip19regex],
 * reached from `BaseNoteEvent.citedNIP19()` during ingest. Kotlin's `findAll`
 * allocates a **new Matcher per match**, each retaining the whole input
 * (`jvmMain/kotlin/text/regex/Regex.kt`: `matcher.pattern().matcher(input)`),
 * so a long article with N mentions builds N matchers over the full text.
 *
 * Corpus sizes come from that same dump's measured distribution of the strings
 * these matchers held: median 529 B, p90/max 767 KB (long-form articles — the
 * v1.13.0 release notes at 68 KB, an essay at 50 KB).
 *
 * Deterministic and offline. Prints ns/op and MB/s; no assertions on wall time
 * (CI machines vary) beyond a sanity check that the scans return results.
 */
class RegexContentBenchmark {
    companion object {
        private const val NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
        private const val NEVENT = "nevent1qqstna2yrezu5wghjvswqqculvvwxsrcvu7uc0f78gan4xqhvz49d9spr3mhxue69uhkummnw3ez6un9d3shjtn4de6x2argwghx6egpr4mhxue69uhkummnw3ez6ur4vgh8wetvd3hhyer9wghxuet5nxnepm"

        /** A note with exactly [mentions] nostr: URIs, padded with prose to ~[targetBytes]. */
        fun note(
            targetBytes: Int,
            mentions: Int,
            hashtags: Boolean = true,
        ): String {
            val filler =
                if (hashtags) {
                    "A few months ago a nostrich was switching from iOS to Android and asked for " +
                        "suggestions for #Nostr apps to try out. Here is what came back, with notes. "
                } else {
                    "A few months ago a nostrich was switching from iOS to Android and asked for " +
                        "suggestions for great apps to try out. Here is what came back, with notes. "
                }
            val sb = StringBuilder(targetBytes + 4096)
            var placed = 0
            val stride = if (mentions > 0) targetBytes / (mentions + 1) else Int.MAX_VALUE
            while (sb.length < targetBytes) {
                sb.append(filler)
                if (placed < mentions && sb.length >= (placed + 1).toLong() * stride) {
                    sb.append("nostr:").append(if (placed % 2 == 0) NPUB else NEVENT).append(' ')
                    placed++
                }
            }
            while (placed < mentions) {
                sb.append("nostr:").append(if (placed % 2 == 0) NPUB else NEVENT).append(' ')
                placed++
            }
            return sb.toString()
        }

        /**
         * REFERENCE: the pre-optimization `findHashtags`, verbatim. Production is
         * compared against this so the guard can never become a tautology.
         */
        fun referenceFindHashtags(content: String): List<String> {
            if (content.isBlank()) return emptyList()
            val output = mutableSetOf<String>()
            hashtagSearch.findAll(content).forEach {
                try {
                    val tag = it.groups[1]?.value
                    if (tag != null && tag.isNotBlank()) output.add(tag)
                } catch (e: Exception) {
                }
            }
            return output.toList()
        }

        /** REFERENCE: pre-optimization nip19 scan (findAll), resolved via the public uriToRoute. */
        fun referenceNip19(content: String): List<String> =
            Nip19Parser.nip19regex
                .findAll(content)
                .mapNotNull { m ->
                    val type = m.groups[3]?.value ?: m.groups[5]?.value
                    val key = m.groups[4]?.value ?: m.groups[6]?.value
                    if (type != null && key != null) {
                        Nip19Parser.uriToRoute(type + key)?.entity?.let { type + key }
                    } else {
                        null
                    }
                }.toList()

        /** A tag array with [n] p/e/a entries, for the #[index] scans. */
        fun tagArray(n: Int): Array<Array<String>> =
            Array(n) { i ->
                when (i % 3) {
                    0 -> arrayOf("p", "%064x".format(i))
                    1 -> arrayOf("e", "%064x".format(i + 1000))
                    else -> arrayOf("a", "30023:%064x:slug$i".format(i))
                }
            }

        /** A note with [refs] legacy #[n] references, padded to ~[targetBytes]. */
        fun indexNote(
            targetBytes: Int,
            refs: Int,
            tagCount: Int,
        ): String {
            val filler = "Legacy index refs used to link people and events inline in the content. "
            val sb = StringBuilder(targetBytes + 4096)
            var placed = 0
            val stride = if (refs > 0) targetBytes / (refs + 1) else Int.MAX_VALUE
            while (sb.length < targetBytes) {
                sb.append(filler)
                if (placed < refs && sb.length >= (placed + 1).toLong() * stride) {
                    sb.append("#[").append(placed % tagCount).append("] ")
                    placed++
                }
            }
            while (placed < refs) {
                sb.append("#[").append(placed % tagCount).append("] ")
                placed++
            }
            return sb.toString()
        }

        /** REFERENCE: pre-optimization findIndexTagsWithPeople, verbatim. */
        fun referenceIndexPeople(
            content: String,
            tags: Array<Array<String>>,
        ): List<String> {
            val output = mutableSetOf<String>()
            tagSearch.findAll(content).forEach { index ->
                try {
                    val tag = index.groups[1]?.value?.let { tags[it.toInt()] }
                    if (tag != null && tag.size > 1 && tag[0] == "p") output.add(tag[1])
                } catch (e: Exception) {
                }
            }
            return output.toList()
        }

        /** REFERENCE: pre-optimization findIndexTagsWithEventsOrAddresses, verbatim. */
        fun referenceIndexEvents(
            content: String,
            tags: Array<Array<String>>,
        ): Set<String> {
            val output = mutableSetOf<String>()
            tagSearch.findAll(content).forEach { index ->
                try {
                    val tag = index.groups[1]?.value?.let { tags[it.toInt()] }
                    if (tag != null && tag.size > 1 && tag[0] == "e") output.add(tag[1])
                    if (tag != null && tag.size > 1 && tag[0] == "a") output.add(tag[1])
                } catch (e: Exception) {
                }
            }
            return output
        }

        fun bench(
            label: String,
            input: String,
            reps: Int,
            op: (String) -> Int,
        ) {
            repeat(maxOf(reps / 4, 2)) { op(input) } // warmup
            val t0 = System.nanoTime()
            var sink = 0
            repeat(reps) { sink += op(input) }
            val ns = (System.nanoTime() - t0) / reps
            val mbps = input.length.toDouble() / ns * 1000.0 // bytes/ns -> MB/s
            println(
                String.format(
                    "%-34s %9d B %9d ns/op %8.1f MB/s   (hits=%d)",
                    label,
                    input.length,
                    ns,
                    mbps,
                    sink / reps,
                ),
            )
        }
    }

    @Test
    fun contentScans() {
        // (bytes, mentions, reps) — mirrors the measured distribution
        val corpus =
            listOf(
                Triple(120, 1, 20_000), // short note
                Triple(529, 2, 20_000), // MEDIAN of what matchers held
                Triple(4_000, 5, 5_000), // long note
                Triple(68_000, 40, 200), // v1.13.0 release notes
                Triple(767_000, 120, 20), // observed MAX
            )

        // Guard the corpus: a benchmark that silently matches nothing measures nothing.
        corpus.forEach { (n, m, _) ->
            val found = Nip19Parser.parseAll(note(n, m)).size
            check(found >= m) { "corpus broken: ${n}B/$m mentions parsed only $found entities" }
        }

        println("\n=== nip19 findNostrUris — NO matches (the common case) ===")
        corpus.forEach { (n, _, r) ->
            bench("nip19 0 mentions", note(n, 0), r) { findNostrUris(it).size }
        }

        println("\n=== nip19 findNostrUris — WITH matches (Matcher per match) ===")
        corpus.forEach { (n, m, r) ->
            bench("nip19 m=$m", note(n, m), r) { findNostrUris(it).size }
        }

        println("\n=== findHashtags (ContentHashTags.hashtagSearch) ===")
        corpus.forEach { (n, m, r) ->
            bench("hashtags m=$m", note(n, m), r) { findHashtags(it).size }
        }

        println("\n=== IndexedTags findIndexTagsWithPeople ===")
        val tags = tagArray(30)
        corpus.forEach { (n, _, r) ->
            bench("idxTags none", indexNote(n, 0, 30), r) { findIndexTagsWithPeople(it, tags).size }
        }
        corpus.forEach { (n, m, r) ->
            bench("idxTags refs=$m", indexNote(n, m, 30), r) { findIndexTagsWithPeople(it, tags).size }
        }

        println("\n=== parseAllEvents (composer: findNostrEventUris) ===")
        corpus.forEach { (n, _, r) ->
            bench("parseAllEvents 0 mentions", note(n, 0), r) { Nip19Parser.parseAllEvents(it).size }
        }
        corpus.forEach { (n, m, r) ->
            bench("parseAllEvents m=$m", note(n, m), r) { Nip19Parser.parseAllEvents(it).size }
        }

        println("\n=== uriToRoute (short strings: one URI / id per call) ===")
        listOf(
            "nostr:$NPUB" to 200_000,
            NPUB to 200_000,
            "nostr:$NEVENT" to 200_000,
            "30023:abc:slug" to 200_000,
            "not an entity at all" to 200_000,
        ).forEach { (uri, reps) ->
            bench("uriToRoute ${uri.take(18)}", uri, reps) { if (Nip19Parser.uriToRoute(it) != null) 1 else 0 }
        }

        println("\n=== tryParseAndClean (short strings, like uriToRoute) ===")
        listOf(
            "nostr:$NPUB" to 200_000,
            NPUB to 200_000,
            "not an entity at all" to 200_000,
        ).forEach { (uri, reps) ->
            bench("tryParseAndClean ${uri.take(14)}", uri, reps) { if (Nip19Parser.tryParseAndClean(it) != null) 1 else 0 }
        }

        println("\n=== RENDER PATH: RichTextParser.parseText (per note, per render) ===")
        val rtTags = tagArray(30).toImmutableListOfLists()
        val rtCorpus =
            listOf(
                Triple(120, 1, 3_000),
                Triple(529, 2, 3_000),
                Triple(4_000, 5, 500),
                Triple(68_000, 40, 20),
            )
        rtCorpus.forEach { (n, m, r) ->
            bench("parseText hashtags m=$m", note(n, m), r) {
                RichTextParser().parseText(it, rtTags, null).paragraphs.size
            }
        }
        rtCorpus.forEach { (n, _, r) ->
            bench("parseText no '#'", note(n, 0, hashtags = false), r) {
                RichTextParser().parseText(it, rtTags, null).paragraphs.size
            }
        }
        rtCorpus.forEach { (n, m, r) ->
            bench("parseText #[n] refs", indexNote(n, m, 30), r) {
                RichTextParser().parseText(it, rtTags, null).paragraphs.size
            }
        }
    }

    /**
     * Fuzz `findHashtags` against a verbatim copy of the implementation it replaced.
     *
     * The explicit behavioural cases live in quartz's `ContentScanTest` (commonTest,
     * so they run on every target). What is kept here is the randomised half: text
     * peppered with `#` in awkward positions, which is where an anchored scan can
     * disagree with the original `findAll`.
     */
    @Test
    fun hashtagsMatchReferenceUnderFuzz() {
        val rnd = kotlin.random.Random(20260813)
        val alphabet = " \n\t#abcXYZ.,!@\u00a0-_0123"
        repeat(3_000) {
            val c = (1..rnd.nextInt(0, 80)).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
            val ref = referenceFindHashtags(c).sorted()
            val prod = findHashtags(c).sorted()
            check(ref == prod) { "fuzz mismatch on ${c.replace("\n", "\\n")}: reference=$ref production=$prod" }
        }
    }

    /**
     * Isolated A/B for the `#[` gate, interleaved with medians.
     *
     * parseText is allocation-heavy and its whole-parse timings swing ±50% run to
     * run (the unchanged no-'#' control arm moved that much), which is far larger
     * than this effect — so measure the gated call on its own instead.
     */
    @Test
    fun hashGateIsolatedAb() {
        // realistic mix: mostly plain hashtags, a few legacy #[n] refs
        val words =
            buildList {
                repeat(90) { add("#hashtag$it") }
                repeat(10) { add("#[$it]") }
            }

        fun ungated(): Int {
            var n = 0
            words.forEach { if (tagIndex.find(it) != null) n++ }
            return n
        }

        fun gated(): Int {
            var n = 0
            words.forEach { if (it.contains("#[") && tagIndex.find(it) != null) n++ }
            return n
        }
        check(ungated() == gated()) { "gate changed the result" }
        repeat(2_000) {
            ungated()
            gated()
        } // warmup both
        val a = mutableListOf<Long>()
        val b = mutableListOf<Long>()
        repeat(21) {
            var t = System.nanoTime()
            repeat(2_000) { ungated() }
            a.add(System.nanoTime() - t)
            t = System.nanoTime()
            repeat(2_000) { gated() }
            b.add(System.nanoTime() - t)
        }
        a.sort()
        b.sort()
        val am = a[a.size / 2] / 2_000
        val bm = b[b.size / 2] / 2_000
        println(
            "\nHASH GATE (100 words: 90 plain hashtags + 10 #[n]) median of 21:" +
                "\n   ungated tagIndex.find : $am ns/word-set" +
                "\n   gated with contains   : $bm ns/word-set" +
                "\n   speedup               : ${String.format("%.2f", am.toDouble() / bm)}x",
        )
    }

    /**
     * Segment-level guard for the RichTextParser '#' path.
     *
     * Expectations are HARDCODED from the behaviour before the `#[` gate was added,
     * so this pins the parser against the code it replaced rather than against itself.
     */
    @Test
    fun richTextHashSegmentsUnchanged() {
        val tags = tagArray(30).toImmutableListOfLists()
        val expected =
            listOf(
                "#Nostr" to "HashTagSegment|#Nostr",
                "#[0]" to "HashIndexUserSegment|#[0]",
                "#[1]suffix" to "HashIndexEventSegment|#[1]suffix",
                "#[999]" to "RegularTextSegment|#[999]",
                "#[abc]" to "RegularTextSegment|#[abc]",
                "#[]" to "RegularTextSegment|#[]",
                "#" to "RegularTextSegment|#",
                "##tag" to "HashTagSegment|##tag",
                "#tag," to "HashTagSegment|#tag,",
                "#tag." to "HashTagSegment|#tag.",
                "#a#b" to "HashTagSegment|#a#b",
                "#[2]#tail" to "HashIndexEventSegment|#[2]#tail",
                "#\u00e9t\u00e9" to "HashTagSegment|#\u00e9t\u00e9",
                "#123" to "HashTagSegment|#123",
                "#[0]!" to "HashIndexUserSegment|#[0]!",
                "plain" to "RegularTextSegment|plain",
                "#tag)" to "HashTagSegment|#tag)",
                "#-dash" to "HashTagSegment|#-dash",
                "#_under" to "HashTagSegment|#_under",
                // '#[' appearing mid-word must still reach the index parser
                "#a#[0]" to "HashIndexUserSegment|#a#[0]",
            )
        expected.forEach { (word, want) ->
            val got =
                RichTextParser()
                    .parseText(word, tags, null)
                    .paragraphs
                    .flatMap { p -> p.words.map { it::class.simpleName + "|" + it.segmentText } }
                    .joinToString(";")
            check(got == want) { "segment mismatch for '$word': want=$want got=$got" }
        }
    }

    /** Both IndexedTags scans must equal the findAll-based references. */
    @Test
    fun indexedTagsMatchReference() {
        val tags = tagArray(30)
        val cases =
            listOf(
                "#[0] #[1] #[2]",
                "no refs here",
                "#[0] at start",
                "mid #[5] ref",
                "nospace#[3] should not match",
                "\n#[7] after newline",
                "#[999] out of range",
                "#[abc] not numeric",
                "#[] empty",
                "",
                "   ",
                indexNote(2_000, 0, 30),
                indexNote(4_000, 6, 30),
                indexNote(20_000, 20, 30),
            )
        cases.forEach { c ->
            check(referenceIndexPeople(c, tags).sorted() == findIndexTagsWithPeople(c, tags).sorted()) {
                "people mismatch on ${c.take(40).replace("\n", "\\n")}"
            }
            check(referenceIndexEvents(c, tags).sorted() == findIndexTagsWithEventsOrAddresses(c, tags).sorted()) {
                "events mismatch on ${c.take(40).replace("\n", "\\n")}"
            }
        }
        val rnd = kotlin.random.Random(20260813)
        val alphabet = " \n#[]0123456789abc\u00a0"
        repeat(3_000) {
            val c = (1..rnd.nextInt(0, 60)).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
            check(referenceIndexPeople(c, tags).sorted() == findIndexTagsWithPeople(c, tags).sorted()) {
                "fuzz people mismatch on ${c.replace("\n", "\\n")}"
            }
            check(referenceIndexEvents(c, tags).sorted() == findIndexTagsWithEventsOrAddresses(c, tags).sorted()) {
                "fuzz events mismatch on ${c.replace("\n", "\\n")}"
            }
        }
    }

    /** Production parseAll must equal the findAll-based reference scan. */
    @Test
    fun nip19MatchesReferenceScan() {
        val cases =
            listOf(
                "nostr:$NPUB",
                "@$NPUB",
                NPUB,
                "text nostr:$NEVENT tail",
                "two $NPUB and nostr:$NEVENT here",
                "none at all",
                "npub1tooshort",
                "",
                note(200, 1),
                note(4_000, 5),
                note(20_000, 12),
                note(4_000, 0),
                // every prefix standalone
                "npub1",
                "nsec1",
                "note1",
                "nevent1",
                "naddr1",
                "nprofile1",
                "nrelay1",
                "nembed1",
                // 'n' as the LAST char: the second-char dispatch must not read past the end
                "n",
                "N",
                "ends with n",
                "trailing N",
                "nn",
                "np",
                "ne",
                "na",
                "nr",
                "ns",
                "no",
                // case + adjacency
                "NOSTR:" + NPUB.uppercase(),
                "x" + NPUB,
                NPUB + NEVENT,
                NPUB + " " + NEVENT,
            )
        cases.forEach { c ->
            val ref = referenceNip19(c)
            val prod = Nip19Parser.parseAll(c).size
            check(ref.size == prod) { "nip19 mismatch on ${c.take(50)}: reference=${ref.size} production=$prod" }
        }
    }
}
