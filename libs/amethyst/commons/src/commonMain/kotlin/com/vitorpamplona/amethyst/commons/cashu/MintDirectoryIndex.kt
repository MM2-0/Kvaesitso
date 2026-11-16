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
package com.vitorpamplona.amethyst.commons.cashu

import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap

/**
 * In-memory directory of Cashu mint URLs the app has seen on the network,
 * sibling to `LocalCache.relayHints`. Used to offer autocomplete in any
 * text field where the user types a mint URL — typing "min" suggests
 * mints other nostr users have already published in a kind:10019
 * (`NutzapInfoEvent`), kind:38000 (`MintRecommendationEvent`), or kind:38172
 * (`CashuMintEvent`), so the user doesn't have to memorise URLs.
 *
 * Population is passive — `LocalCache.updateMintIndex(event)` adds entries
 * as events flow through `justConsumeAndUpdateIndexes`. Entries are never
 * removed: the index is a best-effort suggestion source, not authoritative,
 * and a still-listed mint URL never hurts (the user can always tap Verify
 * before adding it). Each call to [add] increments a counter that
 * [suggest] uses to rank popular mints first.
 *
 * Normalisation: incoming URLs are trimmed, lower-cased, and the trailing
 * `/` is stripped so `https://Mint.Example.com/` and `https://mint.example.com`
 * collapse to the same entry. URLs that don't carry an `http://` or
 * `https://` scheme are dropped — autocomplete on garbage tags would
 * surface noise.
 *
 * Thread-safe via [ConcurrentMap], whose `merge` is atomic on every target, so a
 * count cannot be lost to a racing [add]; safe to call from any dispatcher.
 */
class MintDirectoryIndex {
    private val counts = ConcurrentMap<String, Int>()

    fun add(rawUrl: String) {
        val key = normalize(rawUrl) ?: return
        counts.merge(key, 1) { old, new -> old + new }
    }

    fun addAll(rawUrls: Iterable<String>) = rawUrls.forEach(::add)

    /**
     * Up to [limit] mint URLs whose normalised form contains [query]
     * (case-insensitive substring), ranked by occurrence count descending
     * then by URL ascending. Empty [query] returns the most popular mints
     * overall, capped at [limit].
     */
    fun suggest(
        query: String,
        limit: Int = 8,
    ): List<String> {
        val needle = query.trim().lowercase()
        val ranking =
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key }
        // snapshot() instead of a live view: the ranking sorts the whole entry set,
        // and a copy cannot shift under the comparator while it does.
        return counts
            .snapshot()
            .entries
            .asSequence()
            .filter { needle.isEmpty() || it.key.contains(needle) }
            .sortedWith(ranking)
            .take(limit)
            .map { it.key }
            .toList()
    }

    /** Total number of unique mint URLs in the index. */
    fun size(): Int = counts.size()

    /** Clears the index — for tests; production should let it accumulate. */
    fun clear() = counts.clear()

    companion object {
        /**
         * Returns the canonical lowercased URL with trailing `/` stripped,
         * or `null` if the input doesn't look like an HTTP(S) URL.
         */
        fun normalize(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val lower = trimmed.lowercase()
            if (!lower.startsWith("https://") && !lower.startsWith("http://")) return null
            return lower.trimEnd('/')
        }
    }
}
