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

import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.prodbench.RegexContentBenchmark.Companion.bench
import com.vitorpamplona.amethyst.commons.prodbench.RegexContentBenchmark.Companion.note
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import kotlin.test.Test

/**
 * Measures [RichTextParser.parseText] end to end, and the per-word segmenting loop that every
 * word of every rendered note walks through.
 *
 * Exists to price the opening-bracket peel added for `(@npub1...)`: it adds a check to the hot
 * loop that runs once per word, so the interesting number is the plain-prose corpus (no
 * brackets, no entities) where the check can only ever cost and never pay.
 *
 * Deterministic and offline. Prints ns/op; no assertions on wall time.
 */
class RichTextParserBenchmark {
    private val parser = RichTextParser()

    private fun parse(content: String) = parser.parseText(content, EmptyTagList, null).paragraphs.sumOf { it.words.size }

    /** Prose with [brackets] parenthesised asides — the shape the peel has to test and reject. */
    private fun bracketedProse(
        targetBytes: Int,
        brackets: Int,
    ): String {
        val filler = "A few months ago a nostrich was switching from iOS to Android and asked for suggestions. "
        val sb = StringBuilder(targetBytes + 4096)
        var placed = 0
        val stride = if (brackets > 0) targetBytes / (brackets + 1) else Int.MAX_VALUE
        while (sb.length < targetBytes) {
            sb.append(filler)
            if (placed < brackets && sb.length >= (placed + 1).toLong() * stride) {
                sb.append("(a parenthesised aside, as one writes) ")
                placed++
            }
        }
        return sb.toString()
    }

    /** The kind-1111 comment this peel was added for, padded to [targetBytes] of prose. */
    private fun bracketedMentions(
        targetBytes: Int,
        mentions: Int,
    ): String {
        val npub = "npub1hgvtv4zn2l8l3ef34n87r4sf5s00xq3lhgr3mvwt7kn8gjxpjprqc89jnv"
        val filler = "A few months ago a nostrich was switching from iOS to Android and asked for suggestions. "
        val sb = StringBuilder(targetBytes + 4096)
        var placed = 0
        val stride = if (mentions > 0) targetBytes / (mentions + 1) else Int.MAX_VALUE
        while (sb.length < targetBytes) {
            sb.append(filler)
            if (placed < mentions && sb.length >= (placed + 1).toLong() * stride) {
                sb.append("(@").append(npub).append(") ")
                placed++
            }
        }
        while (placed < mentions) {
            sb.append("(@").append(npub).append(") ")
            placed++
        }
        return sb.toString()
    }

    @Test
    fun parseTextScans() {
        // (bytes, mentions, reps) — same distribution as RegexContentBenchmark.
        val corpus =
            listOf(
                Triple(120, 1, 20_000), // short note
                Triple(529, 2, 20_000), // MEDIAN of what matchers held
                Triple(4_000, 5, 5_000), // long note
                Triple(68_000, 40, 200), // v1.13.0 release notes
            )

        println("\n=== parseText — plain prose, no entities (pure cost of the per-word check) ===")
        corpus.forEach { (n, _, r) ->
            bench("parseText prose", note(n, 0), r) { parse(it) }
        }

        println("\n=== parseText — prose with parenthesised asides (peel tests, then rejects) ===")
        corpus.forEach { (n, m, r) ->
            bench("parseText asides b=$m", bracketedProse(n, m), r) { parse(it) }
        }

        println("\n=== parseText — nostr: mentions (unchanged path) ===")
        corpus.forEach { (n, m, r) ->
            bench("parseText nostr: m=$m", note(n, m), r) { parse(it) }
        }

        println("\n=== parseText — (@npub1...) mentions (the newly-detected shape) ===")
        corpus.forEach { (n, m, r) ->
            bench("parseText (@npub) m=$m", bracketedMentions(n, m), r) { parse(it) }
        }
    }
}
